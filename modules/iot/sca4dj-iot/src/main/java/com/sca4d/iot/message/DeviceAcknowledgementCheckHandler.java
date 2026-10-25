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
import com.sca4d.iot.api.message.DeviceAcknowledgementCheck;
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

/** 消费延迟 ACK 超时检查，并按下行消息的 retry policy 登记下一次发送。 */
public class DeviceAcknowledgementCheckHandler implements MessageHandler<DeviceAcknowledgementCheck> {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceAcknowledgementCheckHandler.class);

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull DeviceDeliveryScheduler messages;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull MessageCodec<DeviceAcknowledgementCheck> codec;

    private final @NotNull TransactionTemplate transactions;

    /** 创建按 messageId 加锁，并在短事务中处理 ACK 超时的 Consumer。 */
    public DeviceAcknowledgementCheckHandler(
            @NotNull DownlinkRepository downlinks,
            @NotNull DeviceDeliveryScheduler messages,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions,
            @NotNull MessageCodec<DeviceAcknowledgementCheck> codec) {
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.messages = Objects.requireNonNull(messages, "下行消息发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "ACK 超时时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "ACK 超时分布式锁不能为空");
        this.codec = Objects.requireNonNull(codec, "ACK 超时消息 Codec 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "ACK 超时事务模板不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("IOT/DOWNLINK/ACK/CHECK/HANDLER");
    }

    @Override
    public @NotNull MessageCodec<DeviceAcknowledgementCheck> codec() {
        return codec;
    }

    /** 只有消息仍在等待同一次发送的 ACK 时才执行超时重试判断。 */
    @Override
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<DeviceAcknowledgementCheck> delivery) {
        DeviceAcknowledgementCheck timeout = Objects.requireNonNull(delivery, "ACK 超时投递不能为空")
                .message();
        distributedLock.execute(
                downlinkLockKey(timeout.messageId()),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> handleTimeout(timeout));
                    return null;
                });
        return HandlingDecision.acknowledge();
    }

    /** 在消息锁和事务内重新确认等待状态及发送次数。 */
    private void handleTimeout(@NotNull DeviceAcknowledgementCheck timeout) {
        DeviceDownlink message = downlinks.find(timeout.messageId()).orElse(null);
        if (message == null
                || message.status() != DeviceDownlinkStatus.WAITING_ACK
                || message.attemptCount() != timeout.attempt()) {
            LOG.trace(
                    "忽略已经失效的 Device ACK 超时消息 | messageId={} attempt={}",
                    timeout.messageId(),
                    timeout.attempt());
            return;
        }
        Instant now = clock.instant();
        IotFailureCode failure = IotFailureCode.ACK_TIMEOUT;
        RetryDecision decision = message.retryPolicy().evaluate(message, failure, now);
        if (decision.retryAt().isPresent()) {
            Instant retryAt = decision.retryAt().orElseThrow();
            if (!downlinks.rescheduleAfterAcknowledgement(message.messageId(), failure.code())) {
                return;
            }
            messages.publishDispatch(message.messageId(), Math.incrementExact(message.attemptCount()), retryAt);
            LOG.warn(
                    "Device 下行 ACK 超时后等待重试 | messageId={} attempt={} retryAt={}",
                    message.messageId(),
                    message.attemptCount(),
                    retryAt);
        } else {
            downlinks.complete(
                    message.messageId(),
                    DeviceDownlinkStatus.FAILED,
                    decision.stopFailure().orElse(failure).code(),
                    now);
            LOG.warn(
                    "Device 下行因 ACK 超时结束 | messageId={} attempt={} reason={}",
                    message.messageId(),
                    message.attemptCount(),
                    decision.stopFailure().orElse(failure).code());
        }
        changes.changed(message.deviceId(), DeviceChangeArea.DOWNLINK, now);
    }

    /** @return 指定下行消息在全部应用实例间共用的锁键 */
    private static @NotNull LockKey downlinkLockKey(long messageId) {
        return new LockKey(TreeCode.of("IOT:DOWNLINK"), messageId);
    }
}
