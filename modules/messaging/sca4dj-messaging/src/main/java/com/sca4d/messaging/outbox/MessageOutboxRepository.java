package com.sca4d.messaging.outbox;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.messaging.outbox.mapper.OutboxMapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 保存 Outbox 消息，并用租约和版本条件领取及完成派发。 */
@ApiStatus.Internal
public class MessageOutboxRepository {

    private final @NotNull OutboxMapper mapper;

    /** 创建使用指定 Mapper 的 Outbox Repository。 */
    public MessageOutboxRepository(@NotNull OutboxMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Outbox Mapper 不能为空");
    }

    /**
     * 在调用方事务内保存一条已经序列化的待发布消息。
     *
     * @throws com.sca4d.core.api.persistence.AffectedRowsMismatchException 新增没有影响一行时抛出
     */
    @AffectedRows(target = "新增 Outbox 消息")
    public void add(
            @NotNull MessageId messageId,
            @NotNull TreeCode messageCode,
            byte @NotNull [] envelope,
            @NotNull Instant availableAt) {
        mapper.insert(
                messageId.value(),
                messageCode.value(),
                envelope,
                OutboxStatus.PENDING,
                availableAt);
    }

    /**
     * 按状态、时间和版本条件领取可发布或租约已过期的记录，并把成功领取的记录改为 SENDING。
     *
     * <p>调用方必须在一个数据库事务中完成此方法，事务提交后再执行 RabbitMQ 发布。
     */
    @NotNull
    List<@NotNull OutboxClaim> claim(
            @NotNull Instant now,
            int limit,
            @NotNull String leaseId,
            @NotNull Instant leaseUntil) {
        return mapper.updateClaimable(now, limit, leaseId, leaseUntil)
                .stream()
                .map(
                        row -> new OutboxClaim(
                                new MessageId(row.messageId()),
                                TreeCode.of(row.messageCode()),
                                row.envelope(),
                                row.attemptCount(),
                                leaseId,
                                row.version()))
                .toList();
    }

    /** 保存本轮派发已确定的结果并释放租约；旧租约或版本不得覆盖新一轮。 */
    @AffectedRows(target = "Outbox 消息派发结果", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    void saveProgress(@NotNull OutboxClaim message) {
        if (message.status() == OutboxStatus.SENDING) {
            throw new IllegalArgumentException("Outbox 派发尚未产生结果");
        }
        mapper.updateProgress(
                message.messageId().value(),
                message.leaseId(),
                message.version(),
                Patch.set(message.status()),
                Patch.<Instant>choose().setIf(message.availableAt() != null, message::availableAt).otherwiseIgnore(),
                Patch.<Instant>choose().setIf(message.sentAt() != null, message::sentAt).otherwiseIgnore(),
                Patch.<String>choose().setIf(message.lastError() != null, message::lastError).otherwiseIgnore());
    }

    /** 分批删除保留期已经结束的 SENT 记录。 */
    int deleteSentBefore(@NotNull Instant before, int limit) {
        return mapper.deleteSentBefore(before, limit);
    }
}
