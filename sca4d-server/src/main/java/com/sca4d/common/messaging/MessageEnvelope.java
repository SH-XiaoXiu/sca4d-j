package com.sca4d.common.messaging;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQ 标准消息信封。
 *
 * <p>{@code event} 与 {@code businessKey} 继承业务发布命令，{@code source} 来自发布应用配置；三者均由业务
 * 或部署环境提供固定值，公共消息模块不维护固定取值集合。
 *
 * @param <T> 业务载荷类型
 */
@Data
@Accessors(chain = true)
public class MessageEnvelope<T> {

    private String messageId;
    private String event;
    private String businessKey;
    private String traceId;
    private String source;
    private OffsetDateTime publishedAt;
    private Map<String, Object> headers = new LinkedHashMap<>();
    private T payload;

    /**
     * 创建消息信封。
     *
     * @param command 发布命令
     * @param traceId 请求追踪 ID
     * @param source 来源应用
     * @param publishedAt 发布时间
     * @return 消息信封
     */
    public static @NotNull MessageEnvelope<Object> from(
            @NotNull MessageCommand command,
            @Nullable String traceId,
            @NotNull String source,
            @NotNull OffsetDateTime publishedAt) {
        return new MessageEnvelope<>()
                .setMessageId(command.getMessageId())
                .setEvent(command.getEvent())
                .setBusinessKey(command.getBusinessKey())
                .setTraceId(traceId)
                .setSource(source)
                .setPublishedAt(publishedAt)
                .setHeaders(new LinkedHashMap<>(command.getHeaders()))
                .setPayload(command.getPayload());
    }
}
