package com.sca4d.notification.sms;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.delivery.DeliveryOutcome;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.RegionCatalog;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
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
public class SmsNotificationDelivery implements NotificationDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(SmsNotificationDelivery.class);

    private static final int MAXIMUM_CHECK_ATTEMPTS = 10;

    private static final Duration CHECK_DELAY = Duration.ofSeconds(30);

    private final SmsRepository repository;

    private final RegionCatalog regions;

    private final SmsDeliveryStore deliveries;

    private final SmsProviderSelector selector;

    private final SmsRecipientResolver recipients;

    private final SmsRouteResolver routes;

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
        SmsRoute route = routes.resolve(checked.code(), checked.region(), parsed, checked.locale());
        return selector.candidates(route)
                .stream()
                .anyMatch(
                        provider -> repository.binding(provider.providerId(), checked.code(), route, checked.locale())
                                .isPresent()
                                && transports.containsKey(provider.type()));
    }

    @Override
    public @NotNull RetryRule retryRule() {
        return RetryRule.atMost(11, Backoff.fixed(Duration.ofSeconds(5)));
    }

    /**
     * 按路由顺序尝试 Provider 发送短信，并把每次尝试写入 SMS 投递记录。
     * 接收手机号必须已验证且符合 E.164，否则抛出 IllegalStateException 或 NullPointerException。
     * 已送达返回 confirmed；已接受、结果未知或处理中返回 pending 并登记一条延迟查询；所有候选项都明确拒绝或无绑定时返回 rejected。
     * 结果未知时不切换到其他 Provider，避免同一条短信被重复发送。
     *
     * @param messageId 通知投递消息 ID，用于查找和登记同一消息的各次 Provider 尝试
     * @param notification 待发送通知，包含接收人、模板 code、区域、语言和模板变量
     * @return 投递结果
     */
    @Override
    public @NotNull DeliveryOutcome deliver(@NotNull Id messageId, @NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        String phone = recipients.resolve(checked.recipient())
                .orElseThrow(() -> new IllegalStateException("SMS 接收人没有已验证手机号"));
        ParsedPhoneNumber parsed = Objects.requireNonNull(regions.parse(phone), "SMS 接收手机号不是规范 E.164");
        SmsRoute route = routes.resolve(checked.code(), checked.region(), parsed, checked.locale());

        // 按应用路由取得 Provider 顺序，并跳过缺少当前通知绑定或 Transport 的候选项。
        for (SmsProvider provider : selector.candidates(route)) {
            SmsProviderBinding binding = repository.binding(
                    provider.providerId(),
                    checked.code(),
                    route,
                    checked.locale())
                    .orElse(null);
            SmsProviderClient transport = transports.get(provider.type());
            // 缺少绑定或 Transport 的候选项不能发送，跳过并记录 Provider ID 与类型，便于排查路由配置。
            if (binding == null || transport == null) {
                LOG.warn(
                        "SMS 候选 Provider 缺少当前通知绑定或 Transport，已跳过 | messageId={} providerId={} providerType={} bindingFound={} transportFound={}",
                        messageId,
                        provider.providerId(),
                        provider.type(),
                        binding != null,
                        transport != null);
                continue;
            }

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

            // begin 返回 false 表示同一消息在该 Provider 上的尝试已由其他投递占用，本次跳过，避免重复发送。
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
        SmsRoute route = routes.resolve(notification.code(), notification.region(), parsed, notification.locale());
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
        LOG.info(
                "SMS Provider 状态查询已完成 | messageId={} providerId={} attempt={} status={} failureCode={}",
                checked.messageId(),
                provider.providerId(),
                checked.attempt(),
                result.status(),
                result.failureCode());
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

    /**
     * 在当前事务中把查询消息登记到 Outbox，并要求最早在 CHECK_DELAY 之后派发。
     * 事务提交前不会派发查询消息；事务回滚时该查询消息也不会被登记。
     *
     * @param check 待登记的查询消息，包含 Provider 请求 ID、业务 ID 和已尝试次数
     */
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
     * 按绑定的 Provider 参数名从通知变量中取值，生成发送给 Transport 的参数表。
     *
     * @param binding 当前 Provider 绑定，parameterMapping 的键为 Provider 参数名，值为通知变量名
     * @param variables 通知模板变量
     * @return 不可变的 Provider 参数表
     * @throws IllegalArgumentException 绑定引用的变量在 variables 中不存在时抛出
     */
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

    /**
     * 生成用于日志和投递记录的手机号掩码。长度不超过 7 位时整体替换为 {@code ***}，否则保留前 4 位和后 4 位。
     * 返回值不能还原完整手机号，用于替代完整号码写入日志。
     *
     * @param value 完整手机号，调用方保证不为 null
     * @return 掩码后的手机号
     */
    private static String mask(String value) {
        return value.length() <= 7 ? "***" : value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
