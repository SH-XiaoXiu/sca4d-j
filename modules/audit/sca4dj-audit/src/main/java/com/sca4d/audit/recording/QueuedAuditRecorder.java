package com.sca4d.audit.recording;

import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.MessageBus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 在独立事务中把审计加入 Outbox，交接提交后返回，不等待 RabbitMQ 消费或审计落库。 */
public class QueuedAuditRecorder implements AuditRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(QueuedAuditRecorder.class);

    private final AuditRepository repository;

    private final MessageBus messages;

    /** 保存审计字段转换入口和公开消息分发入口，不依赖 Messaging 内部 Repository。 */
    public QueuedAuditRecorder(AuditRepository repository, MessageBus messages) {
        this.repository = Objects.requireNonNull(repository);
        this.messages = Objects.requireNonNull(messages);
    }

    @Override
    @Contract(pure = true)
    public @NotNull Audit.Mode mode() {
        return Audit.Mode.QUEUED;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(@NotNull AuditRecord record) {
        // 在返回和释放调用对象之前冻结字段，拒绝记录独立于业务事务保留。
        AuditMessage frozen = new AuditMessage(repository.freeze(record));
        DispatchReceipt receipt = messages.dispatch(frozen, DispatchPolicy.outbox());
        if (receipt.status() != DispatchReceipt.Status.OUTBOX_ENLISTED) {
            throw new IllegalStateException("审计消息没有取得 Outbox 接收结果");
        }
        LOG.debug("审计已加入独立事务 Outbox | requestId={} messageId={}", record.requestId(), receipt.messageId());
    }
}
