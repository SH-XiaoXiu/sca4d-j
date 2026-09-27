package com.sca4d.notification.sms;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.delivery.DeliveryOutcome;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.HandlingPolicy;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.RegionCatalog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 解析手机号、线路、Provider 与平台绑定，并提交一个必发或可选 SMS 渠道请求。
 */
public final class SmsNotificationDelivery implements NotificationDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(SmsNotificationDelivery.class);

    private static final int MAXIMUM_CHECK_ATTEMPTS = 10;

    private static final Duration CHECK_DELAY = Duration.ofSeconds(30);

    private final SmsRecipientResolver recipients;

    private final RegionCatalog regions;

    private final SmsRouteResolver routes;

    private final SmsProviderSelector selector;

    private final SmsRepository repository;

    private final SmsDeliveryStore deliveries;

    private final Map<String, SmsProviderClient> transports;

    private final Clock clock;

    private final MessageBus messages;

    private final TransactionTemplate transactions;

    /**
     * 创建 SMS 渠道投递实现。
     */
    public SmsNotificationDelivery(
            @NotNull SmsRecipientResolver recipients,
            @NotNull RegionCatalog regions,
            @NotNull SmsRouteResolver routes,
            @NotNull SmsProviderSelector selector,
            @NotNull SmsRepository repository,
            @NotNull SmsDeliveryStore deliveries,
            @NotNull List<@NotNull SmsProviderClient> transports,
            @NotNull Clock clock,
            @NotNull MessageBus messages,
            @NotNull PlatformTransactionManager transactionManager) {
        this.recipients = Objects.requireNonNull(recipients, "SMS 接收人解析器不能为空");
        this.regions = Objects.requireNonNull(regions, "RegionCatalog 不能为空");
        this.routes = Objects.requireNonNull(routes, "SMS 路由解析器不能为空");
        this.selector = Objects.requireNonNull(selector, "SMS Provider 选择器不能为空");
        this.repository = Objects.requireNonNull(repository, "SmsRepository 不能为空");
        this.deliveries = Objects.requireNonNull(deliveries, "SmsDeliveryStore 不能为空");
        this.clock = Objects.requireNonNull(clock, "SMS 时钟不能为空");
        this.messages = Objects.requireNonNull(messages, "MessageBus 不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "SMS 事务管理器不能为空"));
        Map<String, SmsProviderClient> byType = new LinkedHashMap<>();
        for (SmsProviderClient transport : Objects.requireNonNull(transports, "SMS Transport 不能为空")) {
            SmsProviderClient value = Objects.requireNonNull(transport, "SMS Transport 不能包含 null");
            if (byType.putIfAbsent(value.providerType(), value) != null) {
                throw new IllegalArgumentException("重复 SMS Provider Transport: " + value.providerType());
            }
        }
        this.transports = Map.copyOf(byType);
    }

    @Override
    public @NotNull TreeCode channel() {
        return SmsChannels.SMS;
    }

    @Override
    public boolean available(@NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        String phone = recipients.resolve(checked.recipient()).orElse(null);
        if (phone == null)
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
    public @NotNull HandlingPolicy handlingPolicy() {
        return new HandlingPolicy(11, Duration.ofSeconds(5));
    }

    @Override
    public @NotNull DeliveryOutcome deliver(@NotNull Id messageId, @NotNull Notification notification) {
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
            SmsProviderClient transport = transports.get(provider.type());
            if (binding == null || transport == null)
                continue;

            // 已接受的尝试直接完成；结果未知时禁止切换 Provider，明确拒绝时才尝试下一项。
            SmsDeliveryAttempt existing = deliveries.attempt(messageId, provider.providerId());
            if (existing != null && existing.status() == SmsDeliveryStatus.DELIVERED)
                return DeliveryOutcome.confirmed();
            if (existing != null
                    && (existing.status() == SmsDeliveryStatus.ACCEPTED
                            || existing.status() == SmsDeliveryStatus.UNKNOWN
                            || existing.status() == SmsDeliveryStatus.PENDING)) {
                scheduleCheck(
                        new SmsDeliveryCheck(
                                checked,
                                messageId,
                                provider.providerId(),
                                existing.requestId(),
                                existing.bizId(),
                                1));
                return DeliveryOutcome.pending(clock.instant().plus(CHECK_DELAY), existing.requestId());
            }
            if (existing != null
                    && (existing.status() == SmsDeliveryStatus.REJECTED
                            || existing.status() == SmsDeliveryStatus.FAILED))
                continue;

            if (!deliveries.begin(messageId, provider, route, DigestUtil.sha256Hex(phone), mask(phone)))
                continue;
            SmsProviderResult result = transport.send(
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
            if (result.status() == SmsDeliveryStatus.DELIVERED)
                return DeliveryOutcome.confirmed();
            if (result.status() == SmsDeliveryStatus.ACCEPTED
                    || result.status() == SmsDeliveryStatus.UNKNOWN
                    || result.status() == SmsDeliveryStatus.PENDING) {
                scheduleCheck(
                        new SmsDeliveryCheck(
                                checked,
                                messageId,
                                provider.providerId(),
                                result.requestId(),
                                result.bizId(),
                                1));
                return DeliveryOutcome.pending(clock.instant().plus(CHECK_DELAY), result.requestId());
            }
        }
        return DeliveryOutcome.rejected("SMS_PROVIDER_REJECTED");
    }

    /**
     * 查询一次 Provider 最终状态；未知时只登记下一条查询消息，不重新发送短信。
     */
    public @NotNull DeliveryOutcome check(@NotNull SmsDeliveryCheck check) {
        SmsDeliveryCheck checked = Objects.requireNonNull(check, "SMS 查询消息不能为空");
        if (checked.attempt() > MAXIMUM_CHECK_ATTEMPTS) {
            return DeliveryOutcome.rejected("SMS_DELIVERY_CHECK_EXHAUSTED");
        }
        Notification notification = checked.notification();
        String phone = recipients.resolve(notification.recipient())
                .orElseThrow(() -> new IllegalStateException("SMS 接收人没有已验证手机号"));
        ParsedPhoneNumber parsed = Objects.requireNonNull(regions.parse(phone), "SMS 接收手机号不是规范 E.164");
        SmsRoute route = route(notification, parsed);
        SmsProvider provider = selector.candidates(route)
                .stream()
                .filter(candidate -> candidate.providerId() == checked.providerId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SMS Provider 已不在当前路由中"));
        SmsProviderBinding binding = repository.binding(
                provider.providerId(),
                notification.code(),
                route,
                notification.locale())
                .orElseThrow(() -> new IllegalStateException("SMS Provider Binding 已不存在"));
        SmsProviderClient transport = transports.get(provider.type());
        if (transport == null) {
            throw new IllegalStateException("SMS Provider Client 未安装: " + provider.type());
        }
        SmsProviderResult result = transport.query(
                provider,
                binding,
                parsed,
                checked.requestId(),
                checked.bizId());
        deliveries.finish(checked.messageId(), provider, result);
        if (result.status() == SmsDeliveryStatus.DELIVERED)
            return DeliveryOutcome.confirmed();
        if (result.status() == SmsDeliveryStatus.ACCEPTED
                || result.status() == SmsDeliveryStatus.PENDING
                || result.status() == SmsDeliveryStatus.UNKNOWN) {
            String requestId = result.requestId() == null ? checked.requestId() : result.requestId();
            String bizId = result.bizId() == null ? checked.bizId() : result.bizId();
            scheduleCheck(
                    new SmsDeliveryCheck(
                            notification,
                            checked.messageId(),
                            provider.providerId(),
                            requestId,
                            bizId,
                            checked.attempt() + 1));
            return DeliveryOutcome.pending(clock.instant().plus(CHECK_DELAY), result.requestId());
        }
        String failureCode = result.failureCode();
        return DeliveryOutcome.rejected(failureCode == null ? "SMS_DELIVERY_FAILED" : failureCode);
    }

    private void scheduleCheck(@NotNull SmsDeliveryCheck check) {
        transactions.executeWithoutResult(
                status -> {
                    DispatchReceipt receipt = messages.dispatch(
                            check,
                            DispatchPolicy.outboxNotBefore(clock.instant().plus(CHECK_DELAY)));
                    LOG.trace(
                            "SMS 投递查询已登记 | messageId={} dispatchStatus={}",
                            check.messageId(),
                            receipt.status());
                });
    }

    /**
     * 使用类型化通知事实取得具体应用声明的 SMS 路由。
     */
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

    private static String mask(String value) {
        return value.length() <= 7 ? "***" : value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
