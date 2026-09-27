package com.sca4d.core.api.messaging;

import static com.sca4d.core.api.messaging.DispatchPolicy.Mode.AFTER_COMMIT;
import static com.sca4d.core.api.messaging.DispatchPolicy.Mode.IMMEDIATE;
import static com.sca4d.core.api.messaging.DispatchPolicy.Mode.OUTBOX;

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

    /** 创建消息分发策略。 */
    private DispatchPolicy(@NotNull Mode mode, @Nullable Instant notBefore) {
        this.mode = Objects.requireNonNull(mode, "消息分发方式不能为空");
        if (mode != OUTBOX && notBefore != null) {
            throw new IllegalArgumentException("只有 Outbox 分发可以声明最早发布时间");
        }
        this.notBefore = notBefore;
    }

    /** @return 立即向 Broker 分发的策略 */
    @CheckReturnValue
    public static @NotNull DispatchPolicy immediate() {
        return new DispatchPolicy(IMMEDIATE, null);
    }

    /** @return 当前事务提交后尝试一次的策略；进程退出或 Broker 失败时允许丢失，必须发布的消息使用 Outbox */
    @CheckReturnValue
    public static @NotNull DispatchPolicy afterCommit() {
        return new DispatchPolicy(AFTER_COMMIT, null);
    }

    /** @return 加入当前事务 Outbox 的策略 */
    @CheckReturnValue
    public static @NotNull DispatchPolicy outbox() {
        return new DispatchPolicy(OUTBOX, null);
    }

    /** @param notBefore 最早允许发布的时间 @return 带最早发布时间的 Outbox 策略 */
    @CheckReturnValue
    public static @NotNull DispatchPolicy outboxNotBefore(@NotNull Instant notBefore) {
        return new DispatchPolicy(OUTBOX, Objects.requireNonNull(notBefore, "最早发布时间不能为空"));
    }

    /** @return 当前分发模式 */
    public @NotNull Mode mode() {
        return mode;
    }

    /** @return Outbox 消息最早发布时间；其他模式返回空 */
    public @Nullable Instant notBefore() {
        return notBefore;
    }
}
