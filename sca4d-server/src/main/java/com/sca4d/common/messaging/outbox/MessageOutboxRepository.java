package com.sca4d.common.messaging.outbox;

import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.entity.MessageOutboxEntity;
import com.sca4d.mapper.MessageOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 消息 Outbox 仓储。
 */
@Repository
@ConditionalOnProperty(prefix = "sca4dj.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageOutboxRepository {

    private final MessageOutboxMapper mapper;
    private final Sca4djMessagingProperties properties;

    /**
     * 消息入队，等待 Outbox 调度器投递。
     *
     * @param entity Outbox 消息实体
     */
    public void enqueue(@NotNull MessageOutboxEntity entity) {
        if (mapper.insert(entity) != 1) {
            throw new IllegalStateException("Outbox 消息写入行数异常");
        }
    }

    /**
     * 抢占可投递消息。
     *
     * @param now 当前时间
     * @return 可投递消息批次
     */
    public @NotNull List<MessageOutboxEntity> claimForDispatch(@NotNull OffsetDateTime now) {
        OffsetDateTime staleSendingBefore = now.minus(properties.getOutbox().getSendingTimeout());
        return mapper.claim(now, staleSendingBefore, properties.getOutbox().getBatchSize());
    }

    /**
     * 标记消息已发布到 MQ broker。
     *
     * @param message 当前 worker 抢占到的 Outbox 消息
     * @param sentAt 发送成功时间
     * @return 当前 worker 仍持有租约并成功迁移状态时返回 true
     */
    public boolean markPublished(@NotNull MessageOutboxEntity message, @NotNull OffsetDateTime sentAt) {
        return mapper.updateStatus(
                message.getId(),
                message.getVersion(),
                MessageOutboxStatus.SENDING,
                MessageOutboxStatus.SENT,
                0,
                null,
                null,
                sentAt,
                sentAt) == 1;
    }

    /**
     * 标记消息发布失败，未达到最大次数时等待重试，超过最大次数时进入发布侧死信。
     *
     * @param message Outbox 消息
     * @param exception 发布异常
     * @param updatedAt 更新时间
     * @return 当前 worker 仍持有租约时返回已落库的下一个状态，否则返回空结果
     */
    public @NotNull Optional<MessageOutboxStatus> markRetryOrDead(
            @NotNull MessageOutboxEntity message, @NotNull RuntimeException exception, @NotNull OffsetDateTime updatedAt) {
        int nextAttempt = message.getAttempts() + 1;
        MessageOutboxStatus nextStatus = nextAttempt >= message.getMaxAttempts()
                ? MessageOutboxStatus.DEAD
                : MessageOutboxStatus.FAILED;
        OffsetDateTime nextAttemptAt = nextStatus == MessageOutboxStatus.DEAD
                ? updatedAt
                : updatedAt.plus(properties.defaultRetryPolicy().backoff(nextAttempt));
        int updated = mapper.updateStatus(
                message.getId(),
                message.getVersion(),
                MessageOutboxStatus.SENDING,
                nextStatus,
                1,
                nextAttemptAt,
                error(exception),
                null,
                updatedAt);
        return updated == 1 ? Optional.of(nextStatus) : Optional.empty();
    }

    /**
     * 将发布异常转换为适合 Outbox 诊断列保存的安全类型名。
     *
     * @param exception 本次发布失败异常
     * @return 不包含消息载荷、连接信息或第三方响应的异常类型
     */
    private @NotNull String error(@NotNull RuntimeException exception) {
        return exception.getClass().getName();
    }
}
