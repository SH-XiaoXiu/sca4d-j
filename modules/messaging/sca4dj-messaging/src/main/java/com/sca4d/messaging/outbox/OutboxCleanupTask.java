package com.sca4d.messaging.outbox;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 由集群定时器直接触发一次超过保留期的 SENT Outbox 记录分批删除。 */
public final class OutboxCleanupTask {

    private final @NotNull MessageOutboxDispatcher dispatcher;

    /**
     * 创建执行一次过期 SENT 记录清理的集群任务。
     *
     * @param dispatcher 提供 SENT 记录保留期和分批删除操作
     * @throws NullPointerException Outbox 操作为空时抛出
     */
    public OutboxCleanupTask(@NotNull MessageOutboxDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "MessageOutboxDispatcher 不能为空");
    }

    /** 分批清理超过保留期的 SENT 记录。 */
    public void execute() {
        dispatcher.cleanup();
    }
}
