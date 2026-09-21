package com.sca4d.core.api.messaging;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 声明 Message 立即、事务提交后或通过 Outbox 发布到 RabbitMQ。 */
public final class DispatchPolicy {

    public enum Mode {
        IMMEDIATE,
        AFTER_COMMIT,
        OUTBOX
    }

    private final @NotNull Mode mode;

    private final @Nullable Instant notBefore;

    private DispatchPolicy(@NotNull Mode mode, @Nullable Instant notBefore) {
        this.mode = Objects.requireNonNull(mode, "消息分发方式不能为空");
        if (mode != Mode.OUTBOX && notBefore != null) {
            throw new IllegalArgumentException("只有 Outbox 分发可以声明最早发布时间");
        }
        this.notBefore = notBefore;
    }

    @CheckReturnValue
    public static @NotNull DispatchPolicy immediate() {
        return new DispatchPolicy(Mode.IMMEDIATE, null);
    }

    @CheckReturnValue
    public static @NotNull DispatchPolicy afterCommit() {
        return new DispatchPolicy(Mode.AFTER_COMMIT, null);
    }

    @CheckReturnValue
    public static @NotNull DispatchPolicy outbox() {
        return new DispatchPolicy(Mode.OUTBOX, null);
    }

    @CheckReturnValue
    public static @NotNull DispatchPolicy outboxNotBefore(@NotNull Instant notBefore) {
        return new DispatchPolicy(Mode.OUTBOX, Objects.requireNonNull(notBefore, "最早发布时间不能为空"));
    }

    public @NotNull Mode mode() {
        return mode;
    }

    public @Nullable Instant notBefore() {
        return notBefore;
    }
}
