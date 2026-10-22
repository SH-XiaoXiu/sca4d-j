package com.sca4d.messaging.outbox;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 由集群定时器直接触发一次到期 Outbox 消息领取和发布。 */
public final class OutboxDispatchTask {

    private final @NotNull MessageOutboxDispatcher dispatcher;

    /**
     * 创建执行一次 Outbox 派发批次的集群任务。
     *
     * @param dispatcher 负责逐条发布、确认和退避结果写回的 Outbox 操作
     * @throws NullPointerException Outbox 操作为空时抛出
     */
    public OutboxDispatchTask(@NotNull MessageOutboxDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "MessageOutboxDispatcher 不能为空");
    }

    /** 领取并派发一批到期消息。 */
    public void execute() {
        dispatcher.dispatch();
    }
}
