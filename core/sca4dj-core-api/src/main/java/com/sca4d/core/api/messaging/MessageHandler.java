package com.sca4d.core.api.messaging;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.message.Message;
import org.jetbrains.annotations.NotNull;

/** 声明一种 Message 的稳定 RabbitMQ 消费入口和技术失败处理策略。 */
public interface MessageHandler<M extends Message> {

    /** @return App 内唯一且跨部署保持稳定的 Handler TreeCode */
    @NotNull
    TreeCode code();

    /** @return 当前 Handler 消费的 Message Codec */
    @NotNull
    MessageCodec<M> codec();

    /** @return 当前 Handler 技术失败时的再次处理策略 */
    @NotNull
    default HandlingPolicy handlingPolicy() {
        return HandlingPolicy.noRetry();
    }

    /** @return RabbitMQ 对当前消息应执行的处理决定 */
    @NotNull
    HandlingDecision handle(@NotNull IncomingMessage<M> message) throws Exception;
}
