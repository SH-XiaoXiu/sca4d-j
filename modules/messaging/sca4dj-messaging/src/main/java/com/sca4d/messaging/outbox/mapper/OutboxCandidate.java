package com.sca4d.messaging.outbox.mapper;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/** 保存 Outbox 已写入领取租约后执行发布所需的列。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class OutboxCandidate {

    private final @Nullable Long handlerAttempt;

    private final @Nullable RetryRule handlerRetryRule;

    private final @NotNull TreeCode messageCode;

    private final @Nullable TreeCode targetHandlerCode;

    private final byte @NotNull [] envelope;

    private final long attemptCount;

    private final long messageId;

    private final long outboxId;

    private final long version;

    /** 创建已经登记租约、尝试次数和版本的 Outbox 领取数据。 */
    public OutboxCandidate(
            long outboxId,
            long messageId,
            @NotNull TreeCode messageCode,
            byte @NotNull [] envelope,
            long attemptCount,
            long version,
            @Nullable TreeCode targetHandlerCode,
            @Nullable Long handlerAttempt,
            @Nullable RetryRule handlerRetryRule) {
        this.outboxId = outboxId;
        this.targetHandlerCode = targetHandlerCode;
        this.handlerAttempt = handlerAttempt;
        this.handlerRetryRule = handlerRetryRule;
        this.messageId = messageId;
        this.messageCode = Objects.requireNonNull(messageCode, "消息编码不能为空");
        this.envelope = Arrays.copyOf(Objects.requireNonNull(envelope, "消息信封不能为空"), envelope.length);
        this.attemptCount = attemptCount;
        this.version = version;
    }

    /** @return 本次代理发布记录标识 */
    public long outboxId() {
        return outboxId;
    }

    /** @return 定向投递目标；普通分发时为空 */
    public @Nullable TreeCode targetHandlerCode() {
        return targetHandlerCode;
    }

    /** @return 待开始的消费尝试序号；普通分发时为空 */
    public @Nullable Long handlerAttempt() {
        return handlerAttempt;
    }

    /** @return 冻结的消费规则；普通分发时为空 */
    public @Nullable RetryRule handlerRetryRule() {
        return handlerRetryRule;
    }

    /** @return 原业务消息标识 */
    public long messageId() {
        return messageId;
    }

    /** @return RabbitMQ routing key 使用的消息编码 */
    public @NotNull TreeCode messageCode() {
        return messageCode;
    }

    /** @return 已序列化消息信封的副本 */
    public byte @NotNull [] envelope() {
        return Arrays.copyOf(envelope, envelope.length);
    }

    /** @return 包含本次领取的累计尝试次数 */
    public long attemptCount() {
        return attemptCount;
    }

    /** @return 本次领取写入后的乐观锁版本 */
    public long version() {
        return version;
    }
}
