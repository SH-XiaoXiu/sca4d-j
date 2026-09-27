package com.sca4d.iot.message;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.message.DeviceDispatchMessage;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.change.DeviceChangeNotifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 消费可靠下行发送消息，并把协议发布结果写回业务状态。 */
public class DeviceDispatchHandler implements MessageHandler<DeviceDispatchMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceDispatchHandler.class);

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull MessageCodec<DeviceDispatchMessage> codec;

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull DeviceTransportRouter router;

    private final @NotNull DeviceDeliveryScheduler messages;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /** 创建按 messageId 加锁、使用短事务转换下行状态的消息 Consumer。 */
    public DeviceDispatchHandler(
            @NotNull DownlinkRepository downlinks,
            @NotNull DeviceTransportRouter router,
            @NotNull DeviceDeliveryScheduler messages,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions,
            @NotNull MessageCodec<DeviceDispatchMessage> codec) {
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.router = Objects.requireNonNull(router, "Device 下行路由不能为空");
        this.messages = Objects.requireNonNull(messages, "下行消息发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "下行 Consumer 时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "下行分布式锁不能为空");
        this.codec = Objects.requireNonNull(codec, "下行消息 Codec 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "下行事务模板不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("IOT/DOWNLINK/DISPATCH/HANDLER");
    }

    @Override
    public @NotNull MessageCodec<DeviceDispatchMessage> codec() {
        return codec;
    }

    /** 重复或已经失效的发送消息直接确认；协议 publish 在数据库事务外执行。 */
    @Override
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<DeviceDispatchMessage> delivery) {
        DeviceDispatchMessage dispatch = Objects.requireNonNull(delivery, "下行消息投递不能为空")
                .message();
        distributedLock.execute(
                downlinkLock(dispatch.messageId()),
                LOCK_WAIT,
                () -> {
                    dispatchLocked(dispatch);
                    return null;
                });
        return HandlingDecision.acknowledge();
    }

    /** 在消息锁内依次执行发送前事务、协议发布和结果事务。 */
    private void dispatchLocked(@NotNull DeviceDispatchMessage dispatch) {
        DeviceDownlink sending = transactions.execute(status -> beginDispatch(dispatch));
        if (sending == null) {
            return;
        }

        // 协议发布不占用数据库事务；失败结果在新的短事务中可靠登记重试。
        try {
            router.publish(sending);
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> retry(sending, clock.instant()));
            return;
        }
        transactions.executeWithoutResult(status -> markPublished(sending));
    }

    /** 在短事务中确认发送消息仍有效，并把当前尝试推进到 SENDING。 */
    private DeviceDownlink beginDispatch(@NotNull DeviceDispatchMessage dispatch) {
        DeviceDownlink current = downlinks.find(dispatch.messageId()).orElse(null);
        if (current == null
                || current.status().terminal()
                || current.status() == DeviceDownlinkStatus.WAITING_ACK
                || current.status() == DeviceDownlinkStatus.PENDING
                        && dispatch.attempt() != current.attemptCount() + 1
                || current.status() == DeviceDownlinkStatus.SENDING
                        && dispatch.attempt() != current.attemptCount()) {
            LOG.trace(
                    "忽略已经失效的 Device 下行发送消息 | messageId={} attempt={}",
                    dispatch.messageId(),
                    dispatch.attempt());
            return null;
        }
        Instant now = clock.instant();
        if (current.expiresAt().filter(expiry -> !expiry.isAfter(now)).isPresent()) {
            downlinks.complete(current.messageId(), DeviceDownlinkStatus.EXPIRED, null, now);
            changes.changed(current.deviceId(), DeviceChangeArea.DOWNLINK, now);
            return null;
        }
        if (current.status() == DeviceDownlinkStatus.SENDING) {
            return current;
        }
        if (!downlinks.beginDispatch(current.messageId(), dispatch.attempt())) {
            return null;
        }
        return downlinks.find(current.messageId()).orElseThrow();
    }

    /** 在 publish 成功后的短事务中保存等待 ACK 或最终成功状态。 */
    private void markPublished(@NotNull DeviceDownlink sending) {
        Instant publishedAt = clock.instant();
        if (sending.acknowledgementPolicy().requiresAcknowledgement()) {
            Instant deadline = publishedAt.plus(sending.acknowledgementTimeout());
            if (!downlinks.markPublished(
                    sending.messageId(),
                    sending.attemptCount(),
                    DeviceDownlinkStatus.WAITING_ACK,
                    deadline,
                    publishedAt)) {
                return;
            }
            messages.publishAcknowledgementTimeout(
                    sending.messageId(),
                    sending.attemptCount(),
                    deadline);
        } else {
            if (!downlinks.markPublished(
                    sending.messageId(),
                    sending.attemptCount(),
                    DeviceDownlinkStatus.SUCCEEDED,
                    null,
                    publishedAt)) {
                return;
            }
        }
        changes.changed(sending.deviceId(), DeviceChangeArea.DOWNLINK, publishedAt);
        LOG.debug(
                "Device 下行已发布 | messageId={} attempt={}",
                sending.messageId(),
                sending.attemptCount());
    }

    /** 使用消息自己的 retry policy 登记下一次发送或转换成最终失败。 */
    private void retry(
            @NotNull DeviceDownlink message,
            @NotNull Instant now) {
        IotFailureCode failure = IotFailureCode.TRANSPORT_FAILED;
        RetryDecision decision = message.retryPolicy().evaluate(message, failure, now);
        if (decision.retryAt().isPresent()) {
            Instant retryAt = decision.retryAt().orElseThrow();
            if (!downlinks.reschedule(message.messageId(), message.attemptCount(), failure.code())) {
                return;
            }
            messages.publishDispatch(message.messageId(), message.attemptCount() + 1, retryAt);
            LOG.warn(
                    "Device 下行等待重试 | messageId={} failure={} attempt={} retryAt={}",
                    message.messageId(),
                    failure.code(),
                    message.attemptCount(),
                    retryAt);
        } else {
            downlinks.complete(
                    message.messageId(),
                    DeviceDownlinkStatus.FAILED,
                    decision.stopFailure().orElse(failure).code(),
                    now);
            LOG.warn(
                    "Device 下行重试已停止 | messageId={} failure={} attempt={}",
                    message.messageId(),
                    failure.code(),
                    message.attemptCount());
        }
        changes.changed(message.deviceId(), DeviceChangeArea.DOWNLINK, now);
    }

    /** @return 指定下行消息在全部应用实例间共用的锁键 */
    private static @NotNull LockKey downlinkLock(long messageId) {
        return new LockKey(TreeCode.of("IOT:DOWNLINK"), messageId);
    }
}
