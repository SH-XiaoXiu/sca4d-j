package com.sca4d.core.api.messaging;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回 MessageBus 已完成的发布阶段和 MessageId。 */
public final class DispatchReceipt {

    public enum Status {
        BROKER_ACCEPTED,
        AFTER_COMMIT_REGISTERED,
        OUTBOX_ENLISTED
    }

    private final @NotNull MessageId messageId;

    private final @NotNull Status status;

    /** 创建消息分发结果。 */
    public DispatchReceipt(@NotNull MessageId messageId, @NotNull Status status) {
        this.messageId = Objects.requireNonNull(messageId, "消息标识不能为空");
        this.status = Objects.requireNonNull(status, "分发结果不能为空");
    }

    /** @return 本次分发使用的消息标识 */
    public @NotNull MessageId messageId() {
        return messageId;
    }

    /** @return 已完成的分发阶段 */
    public @NotNull Status status() {
        return status;
    }
}
