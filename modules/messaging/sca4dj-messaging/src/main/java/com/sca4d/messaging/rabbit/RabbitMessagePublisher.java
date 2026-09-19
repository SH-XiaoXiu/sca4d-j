package com.sca4d.messaging.rabbit;

import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.messaging.outbox.OutboxRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 实现立即、提交后和 Outbox 三种业务消息发布时机。 */
@ApiStatus.Internal
public final class RabbitMessagePublisher implements MessagePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMessagePublisher.class);

    private final @NotNull IdGenerator ids;

    private final @NotNull MessagingEnvelopeCodec codec;

    private final @NotNull RabbitMessageSender sender;

    private final @NotNull OutboxRepository outbox;

    private final @NotNull Clock clock;

    private final @NotNull String sourceApplication;

    /** 创建使用当前 App RabbitMQ 与 Outbox 的消息发布器。 */
    public RabbitMessagePublisher(
            @NotNull IdGenerator ids,
            @NotNull MessagingEnvelopeCodec codec,
            @NotNull RabbitMessageSender sender,
            @NotNull OutboxRepository outbox,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace runtimeNamespace) {
        this.ids = Objects.requireNonNull(ids, "消息标识生成器不能为空");
        this.codec = Objects.requireNonNull(codec, "消息信封编解码器不能为空");
        this.sender = Objects.requireNonNull(sender, "RabbitMQ 发送器不能为空");
        this.outbox = Objects.requireNonNull(outbox, "Outbox Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "消息时钟不能为空");
        this.sourceApplication = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空")
                .applicationId();
    }

    /** 立即编码、发送并等待 RabbitMQ 确认。 */
    @Override
    public @NotNull MessageId publishNow(@NotNull Event<?> event) {
        Event<?> checked = Objects.requireNonNull(event, "业务事件不能为空");
        MessageId messageId = new MessageId(ids.nextId());
        sender.send(
                messageId,
                checked.type().code(),
                codec.encode(messageId, checked, clock.instant(), sourceApplication));
        return messageId;
    }

    /** 在当前事务提交后发送；事务回滚时不发送。 */
    @Override
    public @NotNull MessageId publishAfterCommit(@NotNull Event<?> event) {
        requireTransaction("事务提交后发布");
        Event<?> checked = Objects.requireNonNull(event, "业务事件不能为空");
        MessageId messageId = new MessageId(ids.nextId());
        String eventCode = checked.type().code();
        byte[] envelope = codec.encode(messageId, checked, clock.instant(), sourceApplication);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.send(messageId, eventCode, envelope);
            }
        });
        return messageId;
    }

    /** 在当前事务中写入可立即派发的 Outbox 记录。 */
    @Override
    public @NotNull MessageId publishReliably(@NotNull Event<?> event) {
        return publishReliably(event, clock.instant());
    }

    /** 在当前事务中写入不早于指定时间派发的 Outbox 记录。 */
    @Override
    public @NotNull MessageId publishReliably(
            @NotNull Event<?> event,
            @NotNull Instant earliestPublishAt) {
        requireTransaction("Outbox 可靠发布");
        Event<?> checked = Objects.requireNonNull(event, "业务事件不能为空");
        Instant now = clock.instant();
        Instant availableAt = Objects.requireNonNull(earliestPublishAt, "最早发布时间不能为空");
        MessageId messageId = new MessageId(ids.nextId());
        String eventCode = checked.type().code();
        outbox.add(
                messageId,
                eventCode,
                codec.encode(messageId, checked, now, sourceApplication),
                availableAt);
        LOG.trace(
                "业务消息已写入当前事务的 Outbox | messageId={} eventCode={} availableAt={}",
                messageId,
                eventCode,
                availableAt);
        return messageId;
    }

    /** 拒绝在没有实际事务和事务同步时登记提交后动作或 Outbox。 */
    private static void requireTransaction(@NotNull String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(operation + "要求当前线程存在活跃事务");
        }
    }
}
