package com.sca4d.messaging.outbox.mapper;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/** 保存 Outbox 已写入领取租约后执行发布所需的列。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class OutboxCandidate {

    private final long messageId;

    private final @NotNull String eventCode;

    private final byte @NotNull [] envelope;

    private final int attemptCount;

    private final long version;

    /** 创建已经登记租约、尝试次数和版本的 Outbox 领取数据。 */
    public OutboxCandidate(
            long messageId,
            @NotNull String eventCode,
            byte @NotNull [] envelope,
            int attemptCount,
            long version) {
        this.messageId = messageId;
        this.eventCode = Objects.requireNonNull(eventCode, "事件编码不能为空");
        this.envelope = Arrays.copyOf(Objects.requireNonNull(envelope, "消息信封不能为空"), envelope.length);
        this.attemptCount = attemptCount;
        this.version = version;
    }

    /** @return 消息主键 */
    public long messageId() {
        return messageId;
    }

    /** @return RabbitMQ routing key 使用的事件编码 */
    public @NotNull String eventCode() {
        return eventCode;
    }

    /** @return 已序列化消息信封的副本 */
    public byte @NotNull [] envelope() {
        return Arrays.copyOf(envelope, envelope.length);
    }

    /** @return 包含本次领取的累计尝试次数 */
    public int attemptCount() {
        return attemptCount;
    }

    /** @return 本次领取写入后的乐观锁版本 */
    public long version() {
        return version;
    }
}
