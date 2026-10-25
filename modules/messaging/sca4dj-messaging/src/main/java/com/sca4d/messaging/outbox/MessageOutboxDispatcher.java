package com.sca4d.messaging.outbox;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryPolicy;
import com.sca4d.core.api.retry.RetryRule;
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

    private final @NotNull MessagingProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull TransactionTemplate transactions;

    /**
     * 创建按消息级租约和退避时间派发 Outbox 消息、并分批删除过期 SENT 记录的操作对象。
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

    /**
     * 领取一批到期记录并逐条尝试发布。成功后写回 SENT；失败后写回下一次 availableAt 与错误摘要。
     * 单条失败只影响该记录，不中断本批次其余消息。会修改 Outbox 记录的租约、尝试次数、可用时间和状态，不抛出发布异常。
     * 结果写回失败时只记录 ERROR 并保留租约，等待租约到期后重新领取。
     */
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
                TreeCode handlerCode = message.targetHandlerCode();
                if (handlerCode == null) {
                    sender.send(message.messageId(), message.messageCode(), message.envelope());
                } else {
                    sender.sendHandler(
                            message.outboxId(),
                            message.messageId(),
                            handlerCode,
                            message.envelope(),
                            Objects.requireNonNull(message.handlerAttempt()),
                            Objects.requireNonNull(message.handlerRetryRule()));
                }
                Instant sentAt = clock.instant();
                transactions.executeWithoutResult(status -> repository.saveProgress(message.sent(sentAt)));
                sentCount++;
            } catch (RuntimeException exception) {
                failedCount++;
                // 发布或 SENT 写回失败时按尝试次数计算下一次退避时间，并把失败摘要写入记录，供后续派发和排查使用。
                Instant failedAt = clock.instant();
                Instant availableAt = failedAt.plus(
                        RetryPolicy.always(
                                RetryRule.unlimited(
                                        Backoff.exponential(
                                                Duration.ofMillis(
                                                        Math.min(
                                                                properties.getInitialBackoff().toMillis(),
                                                                properties.getMaximumBackoff().toMillis())),
                                                2,
                                                properties.getMaximumBackoff())))
                                .evaluate(RetryContext.failed(message.attemptCount(), exception))
                                .retryDelay()
                                .orElseThrow());
                String error = errorSummary(exception);
                // 失败结果在独立事务中写回；写回失败时保留当前租约，租约到期后由任一实例重新领取该消息。
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

    /**
     * 删除发送时间早于 clock 当前时间减去 sentRetention 的 SENT 记录，每次最多删除 cleanupBatchSize 条。
     * 只删除已经发送成功的记录，未发送或失败中的记录不受影响；没有可删除记录时不产生日志。
     */
    public void cleanup() {
        Instant before = clock.instant().minus(properties.getSentRetention());
        int deleted = Objects.requireNonNull(
                transactions.execute(
                        status -> repository.deleteSentBefore(before, properties.getCleanupBatchSize())));
        if (deleted > 0) {
            LOG.info("Outbox 已发送记录清理完成 | deletedCount={} before={}", deleted, before);
        }
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
