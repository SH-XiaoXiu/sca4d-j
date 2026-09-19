package com.sca4d.notification.sms;

import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.RegionCatalog;
import com.sca4d.notification.template.NotificationTemplateRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 解析手机号、线路、Provider 与平台绑定，并提交一个必发或可选 SMS 渠道请求。 */
public final class SmsNotificationDelivery implements NotificationDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(SmsNotificationDelivery.class);

    private final SmsRecipientResolver recipients;

    private final RegionCatalog regions;

    private final SmsRouteResolver routes;

    private final SmsProviderSelector selector;

    private final SmsRepository repository;

    private final SmsDeliveryStore deliveries;

    private final Map<String, SmsTransport> transports;

    private final NotificationTemplateRepository templates;

    /** 创建 SMS 渠道投递实现。 */
    public SmsNotificationDelivery(
            @NotNull SmsRecipientResolver recipients,
            @NotNull RegionCatalog regions,
            @NotNull SmsRouteResolver routes,
            @NotNull SmsProviderSelector selector,
            @NotNull SmsRepository repository,
            @NotNull SmsDeliveryStore deliveries,
            @NotNull List<@NotNull SmsTransport> transports,
            @NotNull NotificationTemplateRepository templates) {
        this.recipients = Objects.requireNonNull(recipients, "SMS 接收人解析器不能为空");
        this.regions = Objects.requireNonNull(regions, "RegionCatalog 不能为空");
        this.routes = Objects.requireNonNull(routes, "SMS 路由解析器不能为空");
        this.selector = Objects.requireNonNull(selector, "SMS Provider 选择器不能为空");
        this.repository = Objects.requireNonNull(repository, "SmsRepository 不能为空");
        this.deliveries = Objects.requireNonNull(deliveries, "SmsDeliveryStore 不能为空");
        this.templates = Objects.requireNonNull(templates, "统一通知模板 Repository 不能为空");
        Map<String, SmsTransport> byType = new LinkedHashMap<>();
        for (SmsTransport transport : Objects.requireNonNull(transports, "SMS Transport 不能为空")) {
            SmsTransport value = Objects.requireNonNull(transport, "SMS Transport 不能包含 null");
            if (byType.putIfAbsent(value.providerType(), value) != null) {
                throw new IllegalArgumentException("重复 SMS Provider Transport: " + value.providerType());
            }
        }
        this.transports = Map.copyOf(byType);
    }

    @Override
    public @NotNull NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public boolean available(@NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        String phone = recipients.resolve(checked.recipient()).orElse(null);
        if (phone == null || templates.findEnabled(checked.code()).isEmpty())
            return false;
        ParsedPhoneNumber parsed = regions.parse(phone);
        if (parsed == null)
            return false;
        SmsRoute route = route(checked, parsed);
        return selector.candidates(route)
                .stream()
                .anyMatch(
                        provider -> repository.binding(provider.providerId(), checked.code(), route, checked.locale())
                                .isPresent()
                                && transports.containsKey(provider.type()));
    }

    @Override
    public void deliver(@NotNull MessageId messageId, @NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        String phone = recipients.resolve(checked.recipient())
                .orElseThrow(() -> new IllegalStateException("SMS 接收人没有已验证手机号"));
        ParsedPhoneNumber parsed = Objects.requireNonNull(regions.parse(phone), "SMS 接收手机号不是规范 E.164");
        SmsRoute route = route(checked, parsed);

        // 按应用路由取得 Provider 顺序，并跳过缺少当前通知绑定或 Transport 的候选项。
        for (SmsProvider provider : selector.candidates(route)) {
            SmsProviderBinding binding = repository.binding(
                    provider.providerId(),
                    checked.code(),
                    route,
                    checked.locale())
                    .orElse(null);
            SmsTransport transport = transports.get(provider.type());
            if (binding == null || transport == null)
                continue;

            // 已接受的尝试直接完成；结果未知时禁止切换 Provider，明确拒绝时才尝试下一项。
            SmsDeliveryStatus existing = deliveries.status(messageId, provider.providerId());
            if (existing == SmsDeliveryStatus.ACCEPTED)
                return;
            if (existing == SmsDeliveryStatus.UNKNOWN || existing == SmsDeliveryStatus.PENDING) {
                throw new IllegalStateException("SMS Provider 上次提交结果未知，禁止自动切换平台");
            }
            if (existing == SmsDeliveryStatus.REJECTED)
                continue;

            if (!deliveries.begin(messageId, provider, route, hash(phone), mask(phone)))
                continue;
            SmsTransportResult result = transport.send(
                    provider,
                    binding,
                    parsed,
                    parameters(binding, checked.variables()));
            deliveries.finish(messageId, provider, result);
            LOG.info(
                    "SMS Provider 请求已完成 | messageId={} providerId={} route={} status={} requestId={} failureCode={}",
                    messageId,
                    provider.providerId(),
                    route,
                    result.status(),
                    result.requestId(),
                    result.failureCode());
            if (result.status() == SmsDeliveryStatus.ACCEPTED)
                return;
            if (result.status() == SmsDeliveryStatus.UNKNOWN) {
                throw new IllegalStateException("SMS Provider 返回结果未知，禁止自动切换平台");
            }
        }
        throw new IllegalStateException("没有 SMS Provider 接受通知请求");
    }

    /** 使用类型化通知事实取得具体应用声明的 SMS 路由。 */
    private SmsRoute route(Notification notification, ParsedPhoneNumber destination) {
        return routes.resolve(
                notification.code(),
                notification.region(),
                destination,
                notification.locale());
    }

    private static Map<String, String> parameters(
            SmsProviderBinding binding,
            Map<String, String> variables) {
        Map<String, String> result = new LinkedHashMap<>();
        binding.parameterMapping().forEach((providerName, variableName) -> {
            String value = variables.get(variableName);
            if (value == null)
                throw new IllegalArgumentException("SMS 绑定引用了未提供的变量: " + variableName);
            result.put(providerName, value);
        });
        return Map.copyOf(result);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private static String mask(String value) {
        return value.length() <= 7 ? "***" : value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
