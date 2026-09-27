package com.sca4d.messaging.outbox;

import com.sca4d.messaging.config.MessagingProperties;
import com.sca4d.messaging.rabbit.RabbitPublisher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 多实例领取 Outbox 记录，在事务外发布，并用独立事务写回确认或失败结果。 */
@ApiStatus.Internal
public class MessageOutboxDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MessageOutboxDispatcher.class);

    private static final int LAST_ERROR_MAX_LENGTH = 512;

    private final @NotNull MessageOutboxRepository repository;

    private final @NotNull RabbitPublisher sender;

    private final @NotNull TransactionTemplate transactions;

    private final @NotNull MessagingProperties properties;

    private final @NotNull Clock clock;

    /**
     * 创建保留消息级租约、退避和分批清理语义的 Outbox 操作。
     *
     * @param repository Outbox 持久化入口
     * @param sender 在数据库事务外等待 RabbitMQ 发布结果的发送器
     * @param transactions 领取和结果写回使用的独立事务模板
     * @param properties 派发批量、租约、退避、保留期和清理批量配置
     * @param clock 领取、租约、退避和保留期判断使用的时钟
     */
    public MessageOutboxDispatcher(
            @NotNull MessageOutboxRepository repository,
            @NotNull RabbitPublisher sender,
            @NotNull TransactionTemplate transactions,
            @NotNull MessagingProperties properties,
            @NotNull Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Outbox Repository 不能为空");
        this.sender = Objects.requireNonNull(sender, "RabbitMQ 发送器不能为空");
        this.transactions = Objects.requireNonNull(transactions, "事务模板不能为空");
        this.properties = Objects.requireNonNull(properties, "Messaging 配置不能为空");
        this.clock = Objects.requireNonNull(clock, "Outbox 时钟不能为空");
    }

    /** 领取一批到期记录并逐条尝试发布；单条失败不阻止其他消息。 */
    public void dispatch() {
        // 在短事务中领取到期消息并登记租约，RabbitMQ 等待不占用数据库事务。
        Instant now = clock.instant();
        String leaseId = UUID.randomUUID().toString();
        List<OutboxClaim> messages = Objects.requireNonNull(
                transactions.execute(
                        status -> repository.claim(
                                now,
                                properties.getDispatchBatchSize(),
                                leaseId,
                                now.plus(properties.getLeaseDuration()))));
        int sentCount = 0;
        int failedCount = 0;
        for (OutboxClaim message : messages) {
            try {
                // 在事务外等待 mandatory return 和 publisher confirm，再用独立事务写回 SENT。
                sender.send(message.messageId(), message.messageCode(), message.envelope());
                Instant sentAt = clock.instant();
                transactions.executeWithoutResult(status -> repository.saveProgress(message.sent(sentAt)));
                sentCount++;
            } catch (RuntimeException exception) {
                failedCount++;
                // 发布或 SENT 写回失败时登记下一次退避；写回也失败则保留租约等待其他实例接管。
                Instant failedAt = clock.instant();
                Instant availableAt = failedAt.plus(backoff(message.attemptCount()));
                String error = errorSummary(exception);
                try {
                    transactions.executeWithoutResult(
                            status -> repository.saveProgress(message.failed(availableAt, error)));
                } catch (RuntimeException updateException) {
                    exception.addSuppressed(updateException);
                    LOG.error(
                            "Outbox 发布失败且结果无法写回，租约到期后将重新领取 | messageId={} messageCode={}",
                            message.messageId(),
                            message.messageCode(),
                            exception);
                    continue;
                }
                LOG.warn(
                        "Outbox 消息发布失败，将继续退避重试 | messageId={} messageCode={} attemptCount={} availableAt={} error={}",
                        message.messageId(),
                        message.messageCode(),
                        message.attemptCount(),
                        availableAt,
                        error);
            }
        }
        if (!messages.isEmpty()) {
            LOG.debug(
                    "Outbox 派发批次完成 | claimedCount={} sentCount={} failedCount={}",
                    messages.size(),
                    sentCount,
                    failedCount);
        }
    }

    /** 分批删除保留期已经结束的 SENT 记录。 */
    public void cleanup() {
        Instant before = clock.instant().minus(properties.getSentRetention());
        int deleted = Objects.requireNonNull(
                transactions.execute(
                        status -> repository.deleteSentBefore(before, properties.getCleanupBatchSize())));
        if (deleted > 0) {
            LOG.info("Outbox 已发送记录清理完成 | deletedCount={} before={}", deleted, before);
        }
    }

    /** 按失败次数计算不超过配置上限的指数退避。 */
    private @NotNull Duration backoff(int attemptCount) {
        long initialMillis = properties.getInitialBackoff().toMillis();
        long maximumMillis = properties.getMaximumBackoff().toMillis();
        int exponent = Math.clamp(attemptCount - 1, 0, 30);
        long delay;
        try {
            delay = Math.multiplyExact(initialMillis, 1L << exponent);
        } catch (ArithmeticException exception) {
            delay = maximumMillis;
        }
        return Duration.ofMillis(Math.min(delay, maximumMillis));
    }

    /** 生成可写入数据库且不包含完整消息内容的失败摘要。 */
    private static @NotNull String errorSummary(@NotNull RuntimeException exception) {
        String message = exception.getMessage();
        String result = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return result.length() <= LAST_ERROR_MAX_LENGTH
                ? result
                : result.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
