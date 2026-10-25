package com.sca4d.gateway.registration;

import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.gateway.GatewayRegistrationRequest;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.gateway.config.GatewayProperties;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 管理单个 Gateway 进程内的 Server 租约、readiness 状态和轮询选择。 */
@Component
public class GatewayInstanceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayInstanceRegistry.class);

    private static final Pattern INSTANCE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final @NotNull Clock clock;

    private final @NotNull Duration leaseTtl;

    private final @NotNull Map<String, GatewayInstance> instances = new ConcurrentHashMap<>();

    private final @NotNull AtomicInteger apiSequence = new AtomicInteger();

    private final @NotNull Map<GatewayCallbackCode, AtomicInteger> callbackSequences = new ConcurrentHashMap<>();

    private final @NotNull Map<String, Set<TreeCode>> apiRequirements = new ConcurrentHashMap<>();

    /**
     * 创建实例注册表。
     *
     * @param properties 已校验的 Gateway 配置
     * @param clock 租约过期判断使用的时钟
     */
    public GatewayInstanceRegistry(@NotNull GatewayProperties properties, @NotNull Clock clock) {
        this.leaseTtl = properties.getLeaseTtl();
        this.clock = clock;
    }

    /**
     * 建立或替换指定实例 ID 的注册，并分配新租约 ID。
     *
     * @param request Server 提交的完整注册资料
     * @return 新注册实例；旧租约从替换发生时失效
     * @throws IllegalArgumentException 实例标识、地址、readiness 路径或回调编码不合法时抛出
     */
    @CheckReturnValue
    public synchronized @NotNull GatewayInstance register(@NotNull GatewayRegistrationRequest request) {
        // 校验 Server 声明的地址和路由资料，注册表只保存可以直接参与检查与选择的数据。
        String instanceId = request.getInstanceId();
        if (instanceId == null || !INSTANCE_ID.matcher(instanceId).matches()) {
            throw new IllegalArgumentException("Server 实例 ID 格式不合法");
        }
        URI baseUrl = requireBaseUrl(request.getBaseUrl());
        String readinessPath = requireReadinessPath(request.getReadinessPath());
        Set<GatewayCallbackCode> callbackCodes = request.getCallbackCodes()
                .stream()
                .map(GatewayCallbackCode::new)
                .collect(Collectors.toUnmodifiableSet());
        Set<TreeCode> capabilities = request.getCapabilities()
                .stream()
                .map(GatewayInstanceRegistry::capabilityCode)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, Set<TreeCode>> requirements = request.getApiRequirements()
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> {
                    new InstanceCapability(
                            TreeCode.ofSegment("VALIDATION"),
                            SystemPrincipals.MAIN,
                            List.of(entry.getKey()));
                    if (entry.getValue().isEmpty()) {
                        throw new IllegalArgumentException("路径能力要求不能是空集合");
                    }
                    return entry.getValue()
                            .stream()
                            .map(GatewayInstanceRegistry::capabilityCode)
                            .collect(Collectors.toUnmodifiableSet());
                }));
        // 路径声明在租约过期后仍然保留，避免最后一个声明该路径能力的实例退出后，请求落到不具备该能力的实例。
        requirements.forEach((path, codes) -> {
            Set<TreeCode> previous = apiRequirements.get(path);
            if (previous != null && !previous.equals(codes)) {
                throw new IllegalArgumentException("同一路径的能力声明不一致: " + path);
            }
        });
        apiRequirements.putAll(requirements);
        // 分配新租约并原子替换同名实例，旧租约 ID 不能再续租或删除新注册。
        String leaseId = UUID.randomUUID().toString();
        GatewayInstance instance = new GatewayInstance(
                instanceId,
                leaseId,
                baseUrl,
                readinessPath,
                callbackCodes,
                capabilities,
                requirements,
                clock.instant().plus(leaseTtl));
        GatewayInstance replaced = instances.put(instanceId, instance);
        LOGGER.info(
                "Server 实例已注册 | instanceId={} callbackCount={} replaced={}",
                instanceId,
                callbackCodes.size(),
                replaced != null);
        return instance;
    }

    /**
     * 仅在实例 ID 和租约 ID 同时匹配时续租。
     *
     * @return 当前租约存在时为 true；旧租约或未知实例返回 false
     */
    @CheckReturnValue
    public boolean renew(@NotNull String instanceId, @NotNull String leaseId) {
        AtomicBoolean renewed = new AtomicBoolean();
        Instant expiresAt = clock.instant().plus(leaseTtl);
        instances.computeIfPresent(instanceId, (ignored, current) -> {
            if (current.leaseId().equals(leaseId)) {
                current.renew(expiresAt);
                renewed.set(true);
            }
            return current;
        });
        return renewed.get();
    }

    /**
     * 仅在实例 ID 和租约 ID 同时匹配时删除注册。
     *
     * @return 当前租约被删除时为 true；旧租约不能删除替换后的实例
     */
    @CheckReturnValue
    public boolean remove(@NotNull String instanceId, @NotNull String leaseId) {
        AtomicBoolean removed = new AtomicBoolean();
        instances.computeIfPresent(instanceId, (ignored, current) -> {
            if (current.leaseId().equals(leaseId)) {
                removed.set(true);
                return null;
            }
            return current;
        });
        if (removed.get()) {
            LOGGER.info("Server 实例租约已删除 | instanceId={}", instanceId);
        }
        return removed.get();
    }

    /** @return 当前所有未过期租约的快照 */
    @CheckReturnValue
    public @NotNull List<GatewayInstance> leasedInstances() {
        removeExpired();
        return instances.values()
                .stream()
                .sorted(Comparator.comparing(GatewayInstance::instanceId))
                .toList();
    }

    /** @return 轮询选择一个可处理普通 `/api/**` 请求的健康实例 */
    @CheckReturnValue
    public @Nullable GatewayInstance selectApi(@NotNull String path) {
        String apiPrefix = GatewayConstants.API_PREFIX;
        String serverPath = path.equals(apiPrefix)
                ? "/"
                : path.startsWith(apiPrefix + "/") ? path.substring(apiPrefix.length()) : path;
        int longest = -1;
        Set<TreeCode> required = Set.of();
        for (Map.Entry<String, Set<TreeCode>> entry : apiRequirements.entrySet()) {
            String prefix = entry.getKey();
            if ((prefix.equals("/") || serverPath.equals(prefix) || serverPath.startsWith(prefix + '/'))
                    && prefix.length() > longest) {
                longest = prefix.length();
                required = entry.getValue();
            }
        }
        Set<TreeCode> requirements = required;
        List<GatewayInstance> candidates = routableInstances(null).stream()
                .filter(instance -> instance.capabilities().containsAll(requirements))
                .toList();
        if (candidates.isEmpty()) {
            LOGGER.debug("没有可处理 API 请求的 Server 实例 | routeType={}", "api");
            return null;
        }
        GatewayInstance selected = select(candidates, apiSequence);
        LOGGER.trace("已选择处理 API 请求的 Server 实例 | instanceId={}", selected.instanceId());
        return selected;
    }

    /** 校验 capability 编码，不允许通配符或斜杠。 */
    private static @NotNull TreeCode capabilityCode(@NotNull String value) {
        TreeCode code = value.indexOf(':') < 0 ? TreeCode.ofSegment(value) : TreeCode.ofColon(value);
        if (code.hasNode(TreeCode.ofSegment("*"))) {
            throw new IllegalArgumentException("能力编码不能包含通配符");
        }
        return code;
    }

    /**
     * 选择能够处理指定回调的实例。
     *
     * @param callbackCode 回调编码
     * @return 轮询选择的健康实例；没有符合条件的实例时返回 null
     */
    @CheckReturnValue
    public @Nullable GatewayInstance selectCallback(@NotNull GatewayCallbackCode callbackCode) {
        List<GatewayInstance> candidates = routableInstances(callbackCode);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有可处理回调的 Server 实例 | callbackCode={}", callbackCode);
            return null;
        }
        GatewayInstance selected = select(
                candidates,
                callbackSequences.computeIfAbsent(callbackCode, ignored -> new AtomicInteger()));
        LOGGER.trace(
                "已选择处理回调的 Server 实例 | callbackCode={} instanceId={}",
                callbackCode,
                selected.instanceId());
        return selected;
    }

    /** 更新指定租约的 readiness 结果；健康检查期间发生替换时不污染新租约。 */
    public void readiness(@NotNull String instanceId, @NotNull String leaseId, boolean ready) {
        GatewayInstance current = instances.get(instanceId);
        if (current != null && current.leaseId().equals(leaseId)) {
            boolean changed = current.ready() != ready;
            current.ready(ready);
            if (changed) {
                LOGGER.info("Server readiness 状态已变化 | instanceId={} ready={}", instanceId, ready);
            }
        }
    }

    /** 删除已经超过 TTL 的租约，避免未正常注销的实例继续参与选择。 */
    private void removeExpired() {
        Instant now = clock.instant();
        for (String instanceId : instances.keySet()) {
            instances.computeIfPresent(instanceId, (ignored, current) -> {
                if (current.leaseExpiresAt().isAfter(now)) {
                    return current;
                }
                LOGGER.warn(
                        "Server 实例租约已过期，不再参与路由，实例可重新注册 | instanceId={}",
                        instanceId);
                return null;
            });
        }
    }

    /** 返回健康租约，并可选按回调编码过滤。 */
    private @NotNull List<GatewayInstance> routableInstances(@Nullable GatewayCallbackCode callbackCode) {
        removeExpired();
        Instant now = clock.instant();
        ArrayList<GatewayInstance> candidates = new ArrayList<>();
        for (GatewayInstance instance : instances.values()) {
            if (instance.routable(now)
                    && (callbackCode == null || instance.callbackCodes().contains(callbackCode))) {
                candidates.add(instance);
            }
        }
        candidates.sort(Comparator.comparing(GatewayInstance::instanceId));
        return candidates;
    }

    /**
     * 对已按名称排序的候选实例做无溢出取模轮询选择。
     *
     * @param candidates 至少包含一个实例的候选列表
     * @param sequence 当前路由的轮询序号
     * @return 本次选中的实例
     */
    private static @NotNull GatewayInstance select(
            @NotNull List<GatewayInstance> candidates,
            @NotNull AtomicInteger sequence) {
        return candidates.get(Math.floorMod(sequence.getAndIncrement(), candidates.size()));
    }

    /** 校验 Server 地址是 path 为空或 `/` 的 HTTP(S) Origin。 */
    private static @NotNull URI requireBaseUrl(@Nullable URI value) {
        if (value == null
                || value.getHost() == null
                || !("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme()))
                || value.getRawQuery() != null
                || value.getRawFragment() != null
                || value.getRawUserInfo() != null
                || value.getRawPath() != null
                        && !value.getRawPath().isEmpty()
                        && !"/".equals(value.getRawPath())) {
            throw new IllegalArgumentException("Gateway Server 基础地址必须是 HTTP(S) Origin");
        }
        return value;
    }

    /** 校验 readiness 只能是 Server 内部绝对路径。 */
    private static @NotNull String requireReadinessPath(@Nullable String value) {
        if (value == null
                || !value.startsWith("/")
                || value.contains("?")
                || value.contains("#")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Gateway readiness 路径格式不合法");
        }
        return value;
    }
}
