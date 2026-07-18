package com.sca4d.common.messaging;

import static com.sca4d.common.messaging.outbox.MessageOutboxStatus.PENDING;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.messaging.outbox.MessageOutboxRepository;
import com.sca4d.common.messaging.rabbit.RabbitMessageSender;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.entity.MessageOutboxEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

/**
 * 默认消息发布器。
 */
public class DefaultMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultMessagePublisher.class);

    static final String HEADER_MESSAGE_ID = "x-message-id";
    static final String HEADER_EVENT = "x-event";
    static final String HEADER_BUSINESS_KEY = "x-business-key";
    static final String HEADER_TRACE_ID = "x-trace-id";
    static final String HEADER_SOURCE = "x-source";

    private final RabbitMessageSender sender;
    private final ObjectProvider<MessageOutboxRepository> outboxRepositoryProvider;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator;
    private final Sca4djMessagingProperties properties;

    /**
     * 创建默认消息发布器。
     *
     * @param sender RabbitMQ 消息发送器
     * @param outboxRepositoryProvider Outbox 仓储提供器
     * @param objectMapper JSON 序列化器
     * @param idGenerator ID 生成器
     * @param properties 消息配置
     */
    public DefaultMessagePublisher(
            @NotNull RabbitMessageSender sender,
            @NotNull ObjectProvider<MessageOutboxRepository> outboxRepositoryProvider,
            @NotNull ObjectMapper objectMapper,
            @NotNull IdGenerator idGenerator,
            @NotNull Sca4djMessagingProperties properties) {
        this.sender = sender;
        this.outboxRepositoryProvider = outboxRepositoryProvider;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.properties = properties;
    }

    @Override
    public void publish(@NotNull MessageCommand command) {
        // 发布模式共享同一套消息标识和传输元数据，先在进入具体发送路径前完成契约校验与默认值补全。
        if (!StringUtils.hasText(command.getExchange())) {
            throw new MessagePublishException("消息 exchange 不能为空");
        }
        if (!StringUtils.hasText(command.getRoutingKey())) {
            throw new MessagePublishException("消息 routingKey 不能为空");
        }
        if (!StringUtils.hasText(command.getEvent())) {
            throw new MessagePublishException("消息事件名不能为空");
        }
        if (!StringUtils.hasText(command.getMessageId())) {
            command.setMessageId(idGenerator.nextIdStr());
        }
        command.setHeaders(command.getHeaders() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(command.getHeaders()));
        if (command.getDelay() == null) {
            command.setDelay(Duration.ZERO);
        }
        if (command.getDelay().isNegative()) {
            throw new MessagePublishException("消息延迟时间不能为负数");
        }
        command.setExchange(command.getExchange().trim());
        command.setRoutingKey(command.getRoutingKey().trim());
        command.setEvent(command.getEvent().trim());
        command.getHeaders().putIfAbsent(HEADER_MESSAGE_ID, command.getMessageId());
        command.getHeaders().putIfAbsent(HEADER_EVENT, command.getEvent());
        command.getHeaders().putIfAbsent(HEADER_SOURCE, properties.getSource());
        if (StringUtils.hasText(command.getBusinessKey())) {
            command.getHeaders().putIfAbsent(HEADER_BUSINESS_KEY, command.getBusinessKey());
        }
        CurrentRequest currentRequest = CurrentRequestHolder.currentOrNull();
        String traceId = currentRequest == null ? null : currentRequest.getTraceId();
        if (StringUtils.hasText(traceId)) {
            command.getHeaders().putIfAbsent(HEADER_TRACE_ID, traceId);
        }

        // DIRECT 立即发送；AFTER_COMMIT 避免事务回滚后仍对外发布；OUTBOX 将可靠交付责任持久化。
        switch (command.getMode()) {
            case DIRECT -> send(command);
            case AFTER_COMMIT -> sendAfterCommit(command);
            case OUTBOX -> writeOutbox(command);
        }
    }

    /**
     * 在当前事务成功提交后发送消息。
     *
     * <p>调用线程没有启用事务同步时立即发送；已经启用时仅注册提交回调，不在事务回滚后发送。
     *
     * @param command 已完成发布契约校验并补全传输元数据的消息命令
     */
    private void sendAfterCommit(@NotNull MessageCommand command) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(command);
            return;
        }
        MessageEnvelope<Object> envelope = MessageEnvelope.from(
                command,
                command.getHeaders().get(HEADER_TRACE_ID) instanceof String traceId ? traceId : null,
                properties.getSource(),
                TimeSupport.now());
        try {
            envelope.setPayload(objectMapper.valueToTree(command.getPayload()));
        } catch (IllegalArgumentException exception) {
            throw new MessagePublishException("消息序列化失败", exception);
        }
        String exchange = command.getExchange();
        String routingKey = command.getRoutingKey();
        Duration delay = command.getDelay();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.send(exchange, routingKey, envelope, delay);
                log.debug(
                        "消息发送完成 | messageId={} event={} mode={} delayMs={} exchange={} routingKey={}",
                        envelope.getMessageId(),
                        envelope.getEvent(),
                        MessagePublishMode.AFTER_COMMIT,
                        delay.toMillis(),
                        exchange,
                        routingKey);
            }
        });
    }

    /**
     * 将命令转换为共用消息信封并交给 RabbitMQ 发送器。
     *
     * @param command 已完成发布契约校验并补全传输元数据的消息命令
     */
    private void send(@NotNull MessageCommand command) {
        CurrentRequest currentRequest = CurrentRequestHolder.currentOrNull();
        String traceId = currentRequest == null ? null : currentRequest.getTraceId();
        MessageEnvelope<Object> envelope = MessageEnvelope.from(command, traceId, properties.getSource(), TimeSupport.now());
        sender.send(command.getExchange(), command.getRoutingKey(), envelope, command.getDelay());
        log.debug(
                "消息发送完成 | messageId={} event={} mode={} delayMs={} exchange={} routingKey={}",
                command.getMessageId(),
                command.getEvent(),
                command.getMode(),
                command.getDelay().toMillis(),
                command.getExchange(),
                command.getRoutingKey());
    }

    /**
     * 将可靠消息写入 Outbox，交由后台投递任务按重试策略发送。
     *
     * <p>该方法只建立待投递数据，不直接访问 RabbitMQ。调用方事务回滚时，Outbox 记录随业务事务一并回滚。
     *
     * @param command 已完成发布契约校验并补全可靠投递参数的消息命令
     * @throws MessagePublishException 未启用 Outbox 仓储时抛出
     */
    private void writeOutbox(@NotNull MessageCommand command) {
        MessageOutboxRepository repository = outboxRepositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new MessagePublishException("可靠消息发布需要启用 Outbox 仓储");
        }
        OffsetDateTime now = TimeSupport.now();
        MessageRetryPolicy retryPolicy = properties.defaultRetryPolicy();
        MessageOutboxEntity entity = new MessageOutboxEntity()
                .setMessageId(command.getMessageId())
                .setBusinessKey(command.getBusinessKey())
                .setExchangeName(command.getExchange())
                .setRoutingKey(command.getRoutingKey())
                .setEventName(command.getEvent())
                .setPayloadJson(json(command.getPayload()))
                .setHeadersJson(json(command.getHeaders()))
                .setStatus(PENDING)
                .setAttempts(0)
                .setMaxAttempts(retryPolicy.getMaxAttempts())
                .setNextAttemptAt(now.plus(command.getDelay()))
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L);
        repository.enqueue(entity);
        log.debug(
                "待发消息已登记 | messageId={} event={} mode={} delayMs={} exchange={} routingKey={}",
                command.getMessageId(),
                command.getEvent(),
                MessagePublishMode.OUTBOX,
                command.getDelay().toMillis(),
                command.getExchange(),
                command.getRoutingKey());
    }

    /**
     * 使用全局 Jackson 契约序列化 Outbox 字段。
     *
     * @param value 消息载荷或消息头；缺失值按照 Jackson 的 {@code null} 字面量保存
     * @return 可由 Outbox 投递器还原的 JSON 文本
     * @throws MessagePublishException 值无法按全局 JSON 契约序列化时抛出
     */
    private @NotNull String json(@Nullable Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new MessagePublishException("消息序列化失败", exception);
        }
    }
}
