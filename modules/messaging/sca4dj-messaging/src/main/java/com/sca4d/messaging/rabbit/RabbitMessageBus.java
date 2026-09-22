package com.sca4d.messaging.rabbit;

import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.message.BinaryContent;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.messaging.outbox.MessageOutboxRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 使用固定 RabbitMQ 基座实现立即、提交后和 Outbox 三种 Message 分发方式。 */
@ApiStatus.Internal
public final class RabbitMessageBus implements MessageBus {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMessageBus.class);

    private final @NotNull IdGenerator ids;

    private final @NotNull RabbitMessageEnvelopeCodec codec;

    private final @NotNull RabbitPublisher publisher;

    private final @NotNull MessageOutboxRepository outbox;

    private final @NotNull Clock clock;

    private final @NotNull String sourceApplication;

    public RabbitMessageBus(
            @NotNull IdGenerator ids,
            @NotNull RabbitMessageEnvelopeCodec codec,
            @NotNull RabbitPublisher publisher,
            @NotNull MessageOutboxRepository outbox,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace runtimeNamespace) {
        this.ids = Objects.requireNonNull(ids, "消息标识生成器不能为空");
        this.codec = Objects.requireNonNull(codec, "消息信封编解码器不能为空");
        this.publisher = Objects.requireNonNull(publisher, "RabbitMQ Publisher 不能为空");
        this.outbox = Objects.requireNonNull(outbox, "Message Outbox Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "消息时钟不能为空");
        this.sourceApplication = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空")
                .applicationId();
    }

    @Override
    public @NotNull DispatchReceipt dispatch(@NotNull Message message, @NotNull DispatchPolicy policy) {
        Message checkedMessage = Objects.requireNonNull(message, "消息不能为空");
        DispatchPolicy checkedPolicy = Objects.requireNonNull(policy, "消息分发策略不能为空");
        if (checkedPolicy.mode() == DispatchPolicy.Mode.OUTBOX
                && checkedMessage.contents().stream().anyMatch(content -> content.body() instanceof BinaryContent)) {
            throw new IllegalArgumentException("Outbox 消息不能直接携带二进制内容，请使用 ReferencedContent");
        }
        return switch (checkedPolicy.mode()) {
            case IMMEDIATE -> dispatchNow(checkedMessage);
            case AFTER_COMMIT -> dispatchAfterCommit(checkedMessage);
            case OUTBOX -> dispatchThroughOutbox(checkedMessage, checkedPolicy.notBefore());
        };
    }

    private @NotNull DispatchReceipt dispatchNow(@NotNull Message message) {
        Instant now = clock.instant();
        MessageId messageId = new MessageId(ids.nextId());
        publisher.send(messageId, message.code(), codec.encode(messageId, message, now, sourceApplication));
        return new DispatchReceipt(messageId, DispatchReceipt.Status.BROKER_ACCEPTED);
    }

    private @NotNull DispatchReceipt dispatchAfterCommit(@NotNull Message message) {
        requireTransaction("事务提交后分发");
        Instant now = clock.instant();
        MessageId messageId = new MessageId(ids.nextId());
        byte[] envelope = codec.encode(messageId, message, now, sourceApplication);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.send(messageId, message.code(), envelope);
            }
        });
        return new DispatchReceipt(messageId, DispatchReceipt.Status.AFTER_COMMIT_REGISTERED);
    }

    private @NotNull DispatchReceipt dispatchThroughOutbox(
            @NotNull Message message,
            Instant notBefore) {
        requireTransaction("Outbox 分发");
        Instant now = clock.instant();
        Instant availableAt = notBefore == null ? now : notBefore;
        MessageId messageId = new MessageId(ids.nextId());
        outbox.add(
                messageId,
                message.code(),
                codec.encode(messageId, message, now, sourceApplication),
                availableAt);
        LOG.trace(
                "业务消息已加入当前事务的 Outbox | messageId={} messageCode={} availableAt={}",
                messageId,
                message.code(),
                availableAt);
        return new DispatchReceipt(messageId, DispatchReceipt.Status.OUTBOX_ENLISTED);
    }

    private static void requireTransaction(@NotNull String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(operation + "要求当前线程存在活跃事务");
        }
    }
}
