package com.sca4d.core.api.messaging;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import org.jetbrains.annotations.NotNull;

/** 声明一个业务 Event 的稳定 RabbitMQ Handler。 */
public interface EventHandler<T> {

    /** @return App 内唯一且跨部署保持稳定的 Handler TreeCode */
    @NotNull
    TreeCode code();

    /** @return 当前 Handler 接受的 EventType */
    @NotNull
    EventType<T> eventType();

    /** @return 当前 Handler 技术失败时的再次处理策略 */
    @NotNull
    default HandlingPolicy handlingPolicy() {
        return HandlingPolicy.noRetry();
    }

    /** 处理当前 Event；正常返回表示本轮状态变化和后续消息已经可靠保存。 */
    void handleEvent(@NotNull IncomingMessage<Event<T>> message) throws Exception;

    /** @return RabbitMQ 对当前 Event 应执行的处理决定 */
    @NotNull
    default HandlingDecision handle(@NotNull IncomingMessage<Event<T>> message) throws Exception {
        handleEvent(message);
        return HandlingDecision.acknowledge();
    }
}
