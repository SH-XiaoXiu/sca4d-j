package com.sca4d.core.spring.gateway;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static com.sca4d.core.api.name.NameFormat.DOT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.sca4d.core.api.gateway.GatewayCallback;
import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.gateway.GatewayRegistrationRequest;
import com.sca4d.core.api.gateway.GatewayRegistrationResponse;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.spring.config.InstanceProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** 在应用 readiness 后向 Gateway 注册实例，并在进程存活期间续租。 */
public class GatewayRegistrationLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRegistrationLifecycle.class);

    private final @NotNull RestClient client;

    private final @NotNull Duration renewInterval;

    private final @NotNull String instanceId;

    private final @NotNull String readinessPath;

    private final @NotNull String secret;

    private final @NotNull URI serverBaseUrl;

    private final @NotNull URI registrationsUri;

    private final @NotNull InstanceCapabilities capabilities;

    private final @NotNull Set<GatewayCallbackCode> callbackCodes;

    private final @NotNull ScheduledExecutorService scheduler;

    private final @NotNull AtomicReference<String> leaseId = new AtomicReference<>();

    private final @NotNull AtomicBoolean closing = new AtomicBoolean();

    private volatile @Nullable ScheduledFuture<?> scheduledTask;

    /**
     * 创建注册生命周期并校验全部地址、Secret 和间隔。
     *
     * @param properties Gateway 注册配置
     * @param callbacks 当前实例声明的全部回调
     * @throws IllegalStateException 地址、Secret、路径或时长配置不合法时抛出
     */
    public GatewayRegistrationLifecycle(
            @NotNull GatewayRegistrationProperties properties,
            @NotNull List<@NotNull GatewayCallback<?, ?>> callbacks,
            @NotNull InstanceProperties instanceProperties,
            @NotNull InstanceCapabilities capabilities) {
        Objects.requireNonNull(properties, "Gateway 注册配置不能为空");
        Objects.requireNonNull(callbacks, "Gateway 回调集合不能为空");

        // 固化启动时配置，之后的注册、续租和注销始终使用同一组已校验参数。
        URI gatewayBaseUrl = requireHttpUri(properties.getGatewayBaseUrl(), "Gateway 基础地址", false);
        this.serverBaseUrl = requireHttpUri(properties.getServerBaseUrl(), "Server Origin", true);
        this.secret = requireText(properties.getSecret(), "Gateway 控制 Secret");
        this.renewInterval = requirePositive(properties.getRenewInterval(), "Gateway 续租间隔");
        Duration requestTimeout = requirePositive(properties.getRequestTimeout(), "Gateway 请求超时");
        this.readinessPath = requireText(properties.getReadinessPath(), "readiness 路径");
        if (!readinessPath.startsWith("/") || readinessPath.contains("?") || readinessPath.contains("#")) {
            throw new IllegalStateException("Gateway readiness 路径必须是以斜杠开头且不包含查询参数的路径");
        }
        this.instanceId = instanceProperties.getId();
        this.capabilities = Objects.requireNonNull(capabilities);
        this.registrationsUri = endpoint(gatewayBaseUrl, GatewayConstants.REGISTRATION_PATH);
        this.callbackCodes = callbacks.stream()
                .map(callback -> Objects.requireNonNull(callback, "Gateway 回调不能为空"))
                .map(GatewayCallback::code)
                .collect(Collectors.toUnmodifiableSet());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(requestTimeout);
        requestFactory.setReadTimeout(requestTimeout);
        this.client = RestClient.builder().requestFactory(requestFactory).build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("sca4dj-gateway-registration").daemon(true).factory());
    }

    /** 应用完成启动并进入 readiness 后立即发起第一次注册。 */
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        schedule(Duration.ZERO);
    }

    /** 停止续租并使用当前租约 ID 尝试注销，不阻止 Spring 容器关闭。 */
    @EventListener(ContextClosedEvent.class)
    public void contextClosed() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> currentTask = scheduledTask;
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        String currentLeaseId = leaseId.getAndSet(null);
        if (currentLeaseId != null) {
            try {
                client.delete()
                        .uri(leaseUri(currentLeaseId))
                        .header(GatewayConstants.SECRET_HEADER, secret)
                        .retrieve()
                        .toBodilessEntity();
                LOGGER.info("Server 实例已向 Gateway 注销 | instanceId={}", instanceId);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Server 实例向 Gateway 注销失败，租约将自动过期 | instanceId={} failureType={}",
                        instanceId,
                        exception.getClass().getSimpleName());
            }
        }
        scheduler.shutdownNow();
    }

    /** 注册新租约或续租现有租约，失败后保留应用进程并继续重试。 */
    private void registerOrRenew() {
        if (closing.get()) {
            return;
        }
        try {
            String currentLeaseId = leaseId.get();
            if (currentLeaseId == null) {
                register();
            } else {
                renew(currentLeaseId);
            }
        } catch (RuntimeException exception) {
            leaseId.set(null);
            LOGGER.warn(
                    "Gateway 注册或续租失败，将继续重试 | instanceId={} failureType={}",
                    instanceId,
                    exception.getClass().getSimpleName());
        } finally {
            schedule(renewInterval);
        }
    }

    /** 创建新租约，并用 Gateway 返回的租约 ID 保护之后的续租和注销。 */
    private void register() {
        GatewayRegistrationRequest registration = new GatewayRegistrationRequest(
                instanceId,
                serverBaseUrl,
                readinessPath,
                GatewayConstants.FINGERPRINT,
                callbackCodes.stream().map(code -> code.format(DOT)).collect(Collectors.toUnmodifiableSet()),
                capabilities.enabledCodes()
                        .stream()
                        .map(code -> code.format(COLON))
                        .collect(Collectors.toUnmodifiableSet()),
                capabilities.apiRequirements()
                        .entrySet()
                        .stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Entry::getKey,
                                        entry -> entry.getValue()
                                                .stream()
                                                .map(code -> code.format(COLON))
                                                .collect(Collectors.toUnmodifiableSet()))));
        GatewayRegistrationResponse response = client.post()
                .uri(registrationsUri)
                .header(GatewayConstants.SECRET_HEADER, secret)
                .contentType(APPLICATION_JSON)
                .body(registration)
                .retrieve()
                .body(GatewayRegistrationResponse.class);
        String newLeaseId = response == null ? null : response.getLeaseId();
        leaseId.set(requireText(newLeaseId, "Gateway 返回的租约 ID"));
        LOGGER.info(
                "Server 实例已向 Gateway 注册 | instanceId={} callbackCount={}",
                instanceId,
                callbackCodes.size());
    }

    /** 使用当前租约 ID 延长实例注册；Gateway 拒绝旧租约时下次重新注册。 */
    private void renew(@NotNull String currentLeaseId) {
        client.put()
                .uri(leaseUri(currentLeaseId))
                .header(GatewayConstants.SECRET_HEADER, secret)
                .retrieve()
                .toBodilessEntity();
        LOGGER.trace("Server 实例已续租 | instanceId={}", instanceId);
    }

    /** 安排下一次注册或续租；关闭过程不会创建新任务。 */
    private void schedule(@NotNull Duration delay) {
        if (!closing.get()) {
            scheduledTask = scheduler.schedule(this::registerOrRenew, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** 构建包含 URL 编码安全 UUID 标识的租约路径。 */
    private @NotNull URI leaseUri(@NotNull String currentLeaseId) {
        return endpoint(registrationsUri, "/" + instanceId + "/leases/" + currentLeaseId);
    }

    /** 在基础地址后附加绝对路径片段，避免 URI.resolve 丢弃已有控制 API 路径。 */
    private static @NotNull URI endpoint(@NotNull URI base, @NotNull String path) {
        String value = base.toString();
        return URI.create((value.endsWith("/") ? value.substring(0, value.length() - 1) : value) + path);
    }

    /** 校验 HTTP(S) 地址不携带用户信息、query 或 fragment，并可选要求纯 Origin。 */
    private static @NotNull URI requireHttpUri(
            @Nullable URI value,
            @NotNull String name,
            boolean originOnly) {
        if (value == null
                || value.getHost() == null
                || !("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme()))
                || value.getRawUserInfo() != null
                || value.getRawQuery() != null
                || value.getRawFragment() != null) {
            throw new IllegalStateException(name + "必须是没有 user info、查询参数和 fragment 的 HTTP(S) 地址");
        }
        if (originOnly
                && value.getRawPath() != null
                && !value.getRawPath().isEmpty()
                && !"/".equals(value.getRawPath())) {
            throw new IllegalStateException(name + "的 path 必须为空或 `/`");
        }
        return value;
    }

    /** 校验必须配置的文本，返回去除首尾空白后的值。 */
    private static @NotNull String requireText(@Nullable String value, @NotNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "不能为空");
        }
        return value.strip();
    }

    /** 校验注册和续租使用的 Duration 为正数，并返回同一个不可变值。 */
    private static @NotNull Duration requirePositive(@Nullable Duration value, @NotNull String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + "必须为正数");
        }
        return value;
    }
}
