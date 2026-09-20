package com.sca4d.iot.api.message;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 表示 ACK 策略对当前下行消息给出的无副作用判断。 */
public final class AcknowledgementDecision {

    /** ACK 策略允许的固定结果。 */
    public enum Outcome {
        WAIT,
        SUCCEED,
        FAIL
    }

    private final @NotNull Outcome outcome;

    private final @Nullable IotFailureCode failure;

    private AcknowledgementDecision(@NotNull Outcome outcome, @Nullable IotFailureCode failure) {
        this.outcome = outcome;
        this.failure = failure;
    }

    /** @return 继续等待其他 ACK 的结果 */
    @CheckReturnValue
    public static @NotNull AcknowledgementDecision waitForMore() {
        return new AcknowledgementDecision(Outcome.WAIT, null);
    }

    /** @return 标记消息成功的结果 */
    @CheckReturnValue
    public static @NotNull AcknowledgementDecision succeed() {
        return new AcknowledgementDecision(Outcome.SUCCEED, null);
    }

    /** @param failure 可安全保存的稳定失败 code @return 标记消息失败的结果 */
    @CheckReturnValue
    public static @NotNull AcknowledgementDecision fail(@NotNull IotFailureCode failure) {
        return new AcknowledgementDecision(
                Outcome.FAIL,
                Objects.requireNonNull(failure, "ACK 失败 code 不能为空"));
    }

    /** @return 策略判断结果 */
    public @NotNull Outcome outcome() {
        return outcome;
    }

    /** @return FAIL 的稳定失败 code；其他结果为空 */
    public @Nullable IotFailureCode failure() {
        return failure;
    }
}
