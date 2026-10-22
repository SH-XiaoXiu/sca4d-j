package com.sca4d.messaging.outbox.mapper;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.messaging.outbox.OutboxStatus;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/** 执行 messaging_outbox 最小行写入、条件领取、状态更新和清理 SQL。 */
public interface OutboxMapper {

    /**
     * 新增一条待发布消息。
     *
     * @param messageId 消息主键
     * @param messageCode 消息编码
     * @param envelope 已序列化消息信封
     * @param status 初始状态
     * @param availableAt 最早发布时间
     * @return 影响行数
     */
    int insert(
            @Param("messageId") long messageId,
            @Param("messageCode") @NotNull TreeCode messageCode,
            @Param("envelope") byte @NotNull [] envelope,
            @Param("status") @NotNull OutboxStatus status,
            @Param("availableAt") @NotNull Instant availableAt);

    /** 幂等登记同一 Handler、业务消息和消费序号的下一次投递。 */
    int insertHandlerRetry(
            @Param("outboxId") long outboxId,
            @Param("messageId") long messageId,
            @Param("messageCode") @NotNull TreeCode messageCode,
            @Param("envelope") byte @NotNull [] envelope,
            @Param("handlerCode") @NotNull TreeCode handlerCode,
            @Param("handlerAttempt") long handlerAttempt,
            @Param("retryRule") @NotNull RetryRule retryRule,
            @Param("availableAt") @NotNull Instant availableAt);

    /** 查询已登记的下一次或更晚消费信封，用于交接后的重复消息确认。 */
    OutboxCandidate selectHandlerRetry(
            @Param("handlerCode") @NotNull TreeCode handlerCode,
            @Param("messageId") long messageId,
            @Param("handlerAttempt") long handlerAttempt);

    /**
     * 原子领取可发布或租约已过期的消息。
     *
     * @return 已写入新租约、尝试次数和版本的消息
     */
    @NotNull
    List<@NotNull OutboxCandidate> updateClaimable(
            @Param("now") @NotNull Instant now,
            @Param("limit") int limit,
            @Param("leaseId") @NotNull String leaseId,
            @Param("leaseUntil") @NotNull Instant leaseUntil);

    /** 按当前租约和版本更新派发字段并释放租约，返回影响行数。 */
    int updateProgress(
            @Param("outboxId") long outboxId,
            @Param("leaseId") @NotNull String leaseId,
            @Param("expectedVersion") long expectedVersion,
            @Param("status") @NotNull Patch<OutboxStatus> status,
            @Param("availableAt") @NotNull Patch<Instant> availableAt,
            @Param("sentAt") @NotNull Patch<Instant> sentAt,
            @Param("lastError") @NotNull Patch<String> lastError);

    /** @return 分批删除指定时间前 SENT 行的数量 */
    int deleteSentBefore(
            @Param("before") @NotNull Instant before,
            @Param("limit") int limit);
}
