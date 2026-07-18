package com.sca4d.mapper;

import com.sca4d.common.messaging.outbox.MessageOutboxStatus;
import com.sca4d.entity.MessageOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 消息 Outbox MyBatis Mapper。
 */
@Mapper
public interface MessageOutboxMapper {

    /**
     * 写入 Outbox 消息。
     *
     * @param entity Outbox 消息实体
     * @return 写入行数
     */
    int insert(@Param("entity") @NotNull MessageOutboxEntity entity);

    /**
     * 抢占到期消息。
     *
     * @param now 当前时间
     * @param staleSendingBefore 早于该时间的发送中记录视为超时
     * @param batchSize 批量大小
     * @return 已抢占消息
     */
    @NotNull
    List<MessageOutboxEntity> claim(
            @Param("now") @NotNull OffsetDateTime now,
            @Param("staleSendingBefore") @NotNull OffsetDateTime staleSendingBefore,
            @Param("batchSize") int batchSize);

    /**
     * 更新 Outbox 状态。
     *
     * @param id Outbox 主键
     * @param expectedVersion 当前 worker 抢占时取得的租约版本
     * @param currentStatus 当前状态
     * @param nextStatus 下一个状态
     * @param attemptsIncrement 尝试次数增量
     * @param nextAttemptAt 下次发送时间
     * @param lastError 最后错误
     * @param sentAt 发送成功时间
     * @param updatedAt 更新时间
     * @return 更新行数
     */
    int updateStatus(
            @Param("id") @NotNull Long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("currentStatus") @NotNull MessageOutboxStatus currentStatus,
            @Param("nextStatus") @NotNull MessageOutboxStatus nextStatus,
            @Param("attemptsIncrement") int attemptsIncrement,
            @Param("nextAttemptAt") @Nullable OffsetDateTime nextAttemptAt,
            @Param("lastError") @Nullable String lastError,
            @Param("sentAt") @Nullable OffsetDateTime sentAt,
            @Param("updatedAt") @NotNull OffsetDateTime updatedAt);
}
