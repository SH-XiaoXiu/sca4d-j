package com.sca4d.iot.message;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.change.DeviceChangeNotifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;

/** 把显式 Device ACK 和 reported 推导 ACK 交给同一策略及下行状态机。 */
public class DeviceAcknowledgementService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceAcknowledgementService.class);

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull DeviceDownlinkMessagePublisher downlinkMessages;

    private final @NotNull MessagePublisher messages;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /** 创建按 messageId 加锁，并在短事务中保存 ACK、执行策略和发布 ACK 事件的服务。 */
    public DeviceAcknowledgementService(
            @NotNull DownlinkRepository downlinks,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull DeviceDownlinkMessagePublisher downlinkMessages,
            @NotNull MessagePublisher messages,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions) {
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "ACK 时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.downlinkMessages = Objects.requireNonNull(downlinkMessages, "下行消息发布器不能为空");
        this.messages = Objects.requireNonNull(messages, "MessagePublisher 不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "ACK 分布式锁不能为空");
        this.transactions = Objects.requireNonNull(transactions, "ACK 事务模板不能为空");
    }

    /**
     * 保存 ACK 并按消息声明的策略转换状态；重复 ACK 不重复执行策略。
     *
     * @param acknowledgement 已关联 messageId 的统一业务 ACK
     */
    public void acknowledge(@NotNull DeviceAcknowledgement acknowledgement) {
        DeviceAcknowledgement checked = Objects.requireNonNull(acknowledgement, "Device ACK 不能为空");
        distributedLock.execute(
                downlinkLock(checked.messageId()),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> acknowledgeExpected(null, checked));
                    return null;
                });
    }

    /**
     * 保存设备显式 ACK 前校验下行消息确实属于当前 Device。
     *
     * @param deviceId 发布 ACK 的 Device 标识
     * @param acknowledgement 已关联 messageId 的统一业务 ACK
     * @return ACK 后读取到的消息状态
     */
    public @NotNull DeviceDownlinkMessage acknowledge(
            long deviceId,
            @NotNull DeviceAcknowledgement acknowledgement) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Device 标识必须为正数");
        }
        DeviceAcknowledgement checked = Objects.requireNonNull(acknowledgement, "Device ACK 不能为空");
        return distributedLock.execute(
                downlinkLock(checked.messageId()),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> acknowledgeExpected(deviceId, checked)),
                        "ACK 处理结果不能为空"));
    }

    /** 在同一事务内校验可选 Device 归属并执行 ACK 状态转换。 */
    private @NotNull DeviceDownlinkMessage acknowledgeExpected(
            Long expectedDeviceId,
            @NotNull DeviceAcknowledgement acknowledgement) {
        DeviceAcknowledgement checked = Objects.requireNonNull(acknowledgement, "Device ACK 不能为空");
        DeviceDownlinkMessage message = downlinks.find(checked.messageId())
                .orElseThrow(() -> new BusinessException(IotErrorCode.STATE_CONFLICT));
        if (expectedDeviceId != null && message.deviceId() != expectedDeviceId) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        if (message.status().terminal()) {
            return message;
        }
        String acknowledgementIdentity = downlinks.saveLatestAcknowledgement(checked);
        if (acknowledgementIdentity == null) {
            return message;
        }
        Instant now = clock.instant();
        AcknowledgementDecision decision = message.acknowledgementPolicy()
                .evaluate(message, checked, now);
        switch (decision.outcome()) {
            case WAIT -> LOG.debug(
                    "Device ACK 仍需等待 | messageId={} ackCode={}",
                    message.messageId(),
                    checked.ackCode());
            case SUCCEED -> downlinks.complete(
                    message.messageId(),
                    DeviceDownlinkStatus.SUCCEEDED,
                    null,
                    now);
            case FAIL -> applyRetry(message, Objects.requireNonNull(decision.failure()), now);
        }
        publishAcknowledgementEvent(message, checked, acknowledgementIdentity);
        changes.changed(message.deviceId(), DeviceChangeArea.DOWNLINK, now);
        return downlinks.find(message.messageId()).orElseThrow();
    }

    /** ACK 策略失败后交给独立 retry 策略。 */
    private void applyRetry(
            @NotNull DeviceDownlinkMessage message,
            @NotNull IotFailureCode failure,
            @NotNull Instant now) {
        RetryDecision retry = message.retryPolicy().evaluate(message, failure, now);
        if (retry.retryAt().isPresent()) {
            Instant retryAt = retry.retryAt().orElseThrow();
            if (!downlinks.rescheduleAfterAcknowledgement(
                    message.messageId(),
                    failure.code())) {
                return;
            }
            downlinkMessages.publishDispatch(message.messageId(), message.attemptCount() + 1, retryAt);
            LOG.warn("Device 下行等待重试 | messageId={} failure={}", message.messageId(), failure.code());
        } else {
            downlinks.complete(
                    message.messageId(),
                    DeviceDownlinkStatus.FAILED,
                    retry.stopFailure().orElse(failure).code(),
                    now);
            LOG.warn("Device 下行已失败 | messageId={} failure={}", message.messageId(), failure.code());
        }
    }

    /** 把每个首次接受的 ACK 阶段可靠发布成统一 IoT 事件。 */
    private void publishAcknowledgementEvent(
            @NotNull DeviceDownlinkMessage message,
            @NotNull DeviceAcknowledgement acknowledgement,
            @NotNull String acknowledgementIdentity) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", acknowledgement.messageId());
        data.put("ackCode", acknowledgement.ackCode());
        data.put("source", acknowledgement.source());
        data.put("data", acknowledgement.data());
        String sourceAcknowledgementId = acknowledgement.sourceAcknowledgementId();
        if (sourceAcknowledgementId != null) {
            data.put("sourceAcknowledgementId", sourceAcknowledgementId);
        }
        DeviceEventMessage payload = new DeviceEventMessage(
                message.deviceId(),
                message.bindingGeneration(),
                "$downlink.ack",
                acknowledgementIdentity,
                data,
                acknowledgement.acknowledgedAt(),
                true,
                acknowledgement.receivedAt());
        Event<DeviceEventMessage> event = new Event<>(
                IotMessageTypes.DEVICE_EVENT_OCCURRED,
                "downlink-ack:" + message.messageId() + ':' + acknowledgementIdentity,
                acknowledgement.acknowledgedAt(),
                payload);
        MessageId eventMessageId = messages.publishReliably(event);
        LOG.trace(
                "Device ACK 事件已登记 | messageId={} acknowledgementIdentity={} eventMessageId={}",
                message.messageId(),
                acknowledgementIdentity,
                eventMessageId);
    }

    /** @return 指定下行消息在全部应用实例间共用的锁键 */
    private static @NotNull LockKey downlinkLock(long messageId) {
        return new LockKey("iot", "downlink", Long.toString(messageId));
    }
}
