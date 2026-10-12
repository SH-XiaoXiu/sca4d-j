package com.sca4d.messaging.outbox;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.AffectedRowsMismatchException;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.messaging.outbox.mapper.OutboxMapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Arrays;
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
     * @throws AffectedRowsMismatchException 新增没有影响一行时抛出
     */
    @AffectedRows(target = "新增 Outbox 消息")
    public void add(
            @NotNull Id messageId,
            @NotNull TreeCode messageCode,
            byte @NotNull [] envelope,
            @NotNull Instant availableAt) {
        mapper.insert(
                messageId.value(),
                messageCode,
                envelope,
                OutboxStatus.PENDING,
                availableAt);
    }

    /** 登记下一次定向消费；相同尝试已存在时保留首次登记的规则与执行时间。 */
    public void scheduleHandlerRetry(
            @NotNull Id outboxId,
            @NotNull Id messageId,
            @NotNull TreeCode messageCode,
            byte @NotNull [] envelope,
            @NotNull TreeCode handlerCode,
            long handlerAttempt,
            @NotNull RetryRule rule,
            @NotNull Instant availableAt) {
        if (handlerAttempt < 2)
            throw new IllegalArgumentException("再次消费序号必须至少为 2");
        mapper.insertHandlerRetry(
                outboxId.value(),
                messageId.value(),
                messageCode,
                envelope,
                handlerCode,
                handlerAttempt,
                rule,
                availableAt);
        if (!hasHandlerRetry(handlerCode, messageId, handlerAttempt, envelope)) {
            throw new IllegalStateException("定向消费重试登记后无法读取");
        }
    }

    /**
     * @return 相同信封的下一次或更晚尝试是否已登记
     * @throws IllegalArgumentException 相同消息身份对应不同信封时抛出
     */
    public boolean hasHandlerRetry(
            @NotNull TreeCode handlerCode,
            @NotNull Id messageId,
            long handlerAttempt,
            byte @NotNull [] envelope) {
        var stored = mapper.selectHandlerRetry(handlerCode, messageId.value(), handlerAttempt);
        if (stored == null)
            return false;
        if (!Arrays.equals(stored.envelope(), envelope))
            throw new IllegalArgumentException("相同消息标识对应不同重试信封");
        return true;
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
                                Id.of(row.outboxId()),
                                Id.of(row.messageId()),
                                row.messageCode(),
                                row.envelope(),
                                row.attemptCount(),
                                leaseId,
                                row.version(),
                                row.targetHandlerCode(),
                                row.handlerAttempt(),
                                row.handlerRetryRule()))
                .toList();
    }

    /** 保存本轮派发已确定的结果并释放租约；旧租约或版本不得覆盖新一轮。 */
    @AffectedRows(target = "Outbox 消息派发结果", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    void saveProgress(@NotNull OutboxClaim message) {
        if (message.status() == OutboxStatus.SENDING) {
            throw new IllegalArgumentException("Outbox 派发尚未产生结果");
        }
        mapper.updateProgress(
                message.outboxId().value(),
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
