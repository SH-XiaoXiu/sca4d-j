package com.sca4d.iot.message;

import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.iot.api.message.DeviceAcknowledgementTimeout;
import com.sca4d.iot.api.message.DeviceDownlinkDispatch;
import com.sca4d.iot.api.message.IotMessageTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 在当前事务中可靠发布标准下行发送和 ACK 超时检查消息。 */
public final class DeviceDownlinkMessagePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceDownlinkMessagePublisher.class);

    private final @NotNull MessagePublisher messages;

    private final @NotNull Clock clock;

    /** @param messages 通用可靠消息发布器 @param clock 技术消息发生时间使用的时钟 */
    public DeviceDownlinkMessagePublisher(@NotNull MessagePublisher messages, @NotNull Clock clock) {
        this.messages = Objects.requireNonNull(messages, "MessagePublisher 不能为空");
        this.clock = Objects.requireNonNull(clock, "下行消息时钟不能为空");
    }

    /**
     * 发布一次发送尝试；`earliestPublishAt` 不为空时，在该时间之前不会交给 Consumer。
     */
    public void publishDispatch(long messageId, int attempt, @Nullable Instant earliestPublishAt) {
        DeviceDownlinkDispatch payload = new DeviceDownlinkDispatch(messageId, attempt);
        Event<DeviceDownlinkDispatch> event = new Event<>(
                IotMessageTypes.DOWNLINK_DISPATCH,
                "downlink:" + messageId + ":dispatch:" + attempt,
                clock.instant(),
                payload);
        MessageId dispatchMessageId = earliestPublishAt == null
                ? messages.publishReliably(event)
                : messages.publishReliably(event, earliestPublishAt);
        LOG.trace(
                "Device 下行发送消息已登记 | messageId={} attempt={} dispatchMessageId={} earliestPublishAt={}",
                messageId,
                attempt,
                dispatchMessageId,
                earliestPublishAt);
    }

    /** 在 ACK 截止时间发布指定发送尝试的超时检查。 */
    public void publishAcknowledgementTimeout(long messageId, int attempt, @NotNull Instant deadline) {
        Instant checkedDeadline = Objects.requireNonNull(deadline, "ACK 截止时间不能为空");
        MessageId timeoutMessageId = messages.publishReliably(
                new Event<>(
                        IotMessageTypes.ACKNOWLEDGEMENT_TIMEOUT,
                        "downlink:" + messageId + ":ack-timeout:" + attempt,
                        checkedDeadline,
                        new DeviceAcknowledgementTimeout(messageId, attempt)),
                checkedDeadline);
        LOG.trace(
                "Device ACK 超时消息已登记 | messageId={} attempt={} timeoutMessageId={} deadline={}",
                messageId,
                attempt,
                timeoutMessageId,
                checkedDeadline);
    }
}
