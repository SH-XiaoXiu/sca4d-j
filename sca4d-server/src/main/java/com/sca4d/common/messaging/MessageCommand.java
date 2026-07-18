package com.sca4d.common.messaging;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息发布命令。
 *
 * <p>{@code exchange} 与 {@code routingKey} 来自业务模块部署的消息拓扑，{@code event} 来自发布方和消费方
 * 共享的业务事件常量；这些开放编码随业务模块扩展，不是公共消息模块内置的有限枚举。
 */
@Data
@Accessors(chain = true)
public class MessageCommand {

    private String messageId;
    private String exchange;
    private String routingKey;
    private String event;
    private Object payload;
    private Map<String, Object> headers = new LinkedHashMap<>();
    private String businessKey;
    private Duration delay = Duration.ZERO;
    private MessagePublishMode mode = MessagePublishMode.DIRECT;

    /**
     * 创建消息发布命令。
     *
     * @param exchange 业务模块消息拓扑定义的 exchange 名称
     * @param routingKey 业务模块消息拓扑定义的 routing key
     * @param event 发布方与消费方共享的固定事件编码
     * @param payload 业务载荷
     * @return 消息发布命令
     */
    public static @NotNull MessageCommand of(
            @NotNull String exchange, @NotNull String routingKey, @NotNull String event, @Nullable Object payload) {
        return new MessageCommand()
                .setExchange(exchange)
                .setRoutingKey(routingKey)
                .setEvent(event)
                .setPayload(payload);
    }
}
