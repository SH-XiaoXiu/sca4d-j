package com.sca4d.iot.message;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.iot.api.message.DeviceAcknowledgementCheck;
import com.sca4d.iot.api.message.DeviceDispatchMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/** 在当前事务中可靠发布标准下行发送和 ACK 超时检查消息。 */
public final class DeviceDeliveryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceDeliveryScheduler.class);

    private final @NotNull MessageBus messages;

    /** @param messages 通用可靠消息总线 */
    public DeviceDeliveryScheduler(@NotNull MessageBus messages) {
        this.messages = Objects.requireNonNull(messages, "MessageBus 不能为空");
    }

    /**
     * 发布一次发送尝试；`earliestPublishAt` 不为空时，在该时间之前不会交给 Consumer。
     */
    public void publishDispatch(long messageId, int attempt, @Nullable Instant earliestPublishAt) {
        DeviceDispatchMessage message = new DeviceDispatchMessage(messageId, attempt);
        DispatchPolicy policy = earliestPublishAt == null
                ? DispatchPolicy.outbox()
                : DispatchPolicy.outboxNotBefore(earliestPublishAt);
        Id dispatchMessageId = messages.dispatch(message, policy).messageId();
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
        Id timeoutMessageId = messages.dispatch(
                new DeviceAcknowledgementCheck(messageId, attempt),
                DispatchPolicy.outboxNotBefore(checkedDeadline)).messageId();
        LOG.trace(
                "Device ACK 超时消息已登记 | messageId={} attempt={} timeoutMessageId={} deadline={}",
                messageId,
                attempt,
                timeoutMessageId,
                checkedDeadline);
    }
}
