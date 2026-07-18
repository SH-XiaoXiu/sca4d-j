package com.sca4d.common.messaging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 共用消息发布器。
 */
public interface MessagePublisher {

    /**
     * 按命令声明的模式发布消息。
     *
     * @param command 消息发布命令
     */
    void publish(@NotNull MessageCommand command);

    /**
     * 立即发布消息。
     *
     * @param exchange exchange 名称
     * @param routingKey routing key
     * @param event 事件名
     * @param payload 业务载荷
     */
    default void publishDirect(
            @NotNull String exchange, @NotNull String routingKey, @NotNull String event, @Nullable Object payload) {
        publish(MessageCommand.of(exchange, routingKey, event, payload).setMode(MessagePublishMode.DIRECT));
    }

    /**
     * 当前事务提交后发布消息。
     *
     * @param exchange exchange 名称
     * @param routingKey routing key
     * @param event 事件名
     * @param payload 业务载荷
     */
    default void publishAfterCommit(
            @NotNull String exchange, @NotNull String routingKey, @NotNull String event, @Nullable Object payload) {
        publish(MessageCommand.of(exchange, routingKey, event, payload).setMode(MessagePublishMode.AFTER_COMMIT));
    }

    /**
     * 可靠发布业务消息。
     *
     * @param command 消息发布命令
     */
    default void publishReliable(@NotNull MessageCommand command) {
        publish(command.setMode(MessagePublishMode.OUTBOX));
    }
}
