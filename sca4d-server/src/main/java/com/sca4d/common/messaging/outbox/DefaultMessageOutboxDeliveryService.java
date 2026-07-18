package com.sca4d.common.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.messaging.MessageEnvelope;
import com.sca4d.common.messaging.MessagePublishException;
import com.sca4d.common.messaging.rabbit.RabbitMessageSender;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.entity.MessageOutboxEntity;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 Outbox 消息领取、RabbitMQ 投递和租约状态迁移实现。
 */
@RequiredArgsConstructor
public class DefaultMessageOutboxDeliveryService implements MessageOutboxDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMessageOutboxDeliveryService.class);
    private static final String HEADER_TRACE_ID = "x-trace-id";
    private static final TypeReference<Map<String, Object>> HEADERS_TYPE = new TypeReference<>() {
    };

    private final MessageOutboxRepository repository;
    private final RabbitMessageSender sender;
    private final ObjectMapper objectMapper;
    private final Sca4djMessagingProperties properties;

    @Override
    public void dispatchDueMessages() {
        if (!properties.isEnabled() || !properties.getOutbox().isEnabled()) {
            log.debug("待发消息投递未启用");
            return;
        }
        List<MessageOutboxEntity> messages = repository.claimForDispatch(TimeSupport.now());
        if (messages.isEmpty()) {
            log.debug("待发消息投递没有到期消息 | batchSize={}", properties.getOutbox().getBatchSize());
            return;
        }
        long startedAt = System.nanoTime();
        int publishedCount = 0;
        int retryCount = 0;
        int deadCount = 0;
        int skippedCount = 0;
        log.info("待发消息投递批次开始 | claimedCount={} batchSize={}",
                messages.size(), properties.getOutbox().getBatchSize());
        for (MessageOutboxEntity message : messages) {
            try {
                sender.send(message.getExchangeName(), message.getRoutingKey(), envelope(message), Duration.ZERO);
                if (!repository.markPublished(message, TimeSupport.now())) {
                    skippedCount++;
                    log.warn("待发消息已发送，但记录已由其他节点接管 | outboxId={} messageId={} version={}",
                            message.getId(),
                            message.getMessageId(),
                            message.getVersion());
                } else {
                    publishedCount++;
                }
            } catch (RuntimeException exception) {
                MessageOutboxStatus status = markFailure(message, exception);
                if (status == MessageOutboxStatus.DEAD) {
                    deadCount++;
                } else if (status == MessageOutboxStatus.PENDING) {
                    retryCount++;
                } else {
                    skippedCount++;
                }
            }
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "待发消息投递批次完成 | publishedCount={} retryCount={} deadCount={} skippedCount={} durationMs={}",
                publishedCount,
                retryCount,
                deadCount,
                skippedCount,
                durationMs);
    }

    /**
     * 从 Outbox JSON 列构建 RabbitMQ 消息信封。
     *
     * @param message 已抢占的 Outbox 消息
     * @return 包含业务内容、消息头和追踪信息的消息信封
     */
    private @NotNull MessageEnvelope<JsonNode> envelope(@NotNull MessageOutboxEntity message) {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayloadJson());
            Map<String, Object> headers = StringUtils.hasText(message.getHeadersJson())
                    ? objectMapper.readValue(message.getHeadersJson(), HEADERS_TYPE)
                    : new LinkedHashMap<>();
            Object traceId = headers.get(HEADER_TRACE_ID);
            return new MessageEnvelope<JsonNode>()
                    .setMessageId(message.getMessageId())
                    .setEvent(message.getEventName())
                    .setBusinessKey(message.getBusinessKey())
                    .setTraceId(traceId == null ? null : String.valueOf(traceId))
                    .setSource(properties.getSource())
                    .setPublishedAt(TimeSupport.now())
                    .setHeaders(headers)
                    .setPayload(payload);
        } catch (JsonProcessingException exception) {
            throw new MessagePublishException("Outbox 消息反序列化失败", exception);
        }
    }

    /**
     * 在仍持有租约时记录重试或死信状态。
     *
     * @param message 携带当前 claim version 的 Outbox 消息
     * @param exception 本次投递失败原因
     */
    private @Nullable MessageOutboxStatus markFailure(
            @NotNull MessageOutboxEntity message, @NotNull RuntimeException exception) {
        OffsetDateTime now = TimeSupport.now();
        MessageOutboxStatus nextStatus = repository.markRetryOrDead(message, exception, now).orElse(null);
        if (nextStatus == null) {
            log.warn("待发消息发送失败，记录已由其他节点接管 | outboxId={} messageId={} version={}",
                    message.getId(),
                    message.getMessageId(),
                    message.getVersion(),
                    exception);
            return null;
        }
        if (nextStatus == MessageOutboxStatus.DEAD) {
            log.error("待发消息重试耗尽，不再自动重试 | outboxId={} messageId={} attempts={}",
                    message.getId(), message.getMessageId(), message.getAttempts() + 1, exception);
        } else {
            log.warn(
                    "待发消息发送失败，等待下轮重试 | outboxId={} messageId={} attempts={}",
                    message.getId(),
                    message.getMessageId(),
                    message.getAttempts() + 1,
                    exception);
        }
        return nextStatus;
    }
}
