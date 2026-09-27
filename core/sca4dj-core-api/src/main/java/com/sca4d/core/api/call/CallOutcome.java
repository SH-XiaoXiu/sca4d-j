package com.sca4d.core.api.call;

import static com.sca4d.core.api.call.CallOutcome.Status.DENIED;
import static com.sca4d.core.api.call.CallOutcome.Status.FAILED;
import static com.sca4d.core.api.call.CallOutcome.Status.REPLAYED;
import static com.sca4d.core.api.call.CallOutcome.Status.SUCCESS;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存一次方法执行的实际路径、返回结果或失败。 */
public class CallOutcome {

    /** 列出方法执行可以产生的最终结果。 */
    public enum Status {
        /** 实际业务方法成功返回。 */
        SUCCESS,
        /** 直接复用已经保存的幂等结果。 */
        REPLAYED,
        /** 调用因业务规则被拒绝。 */
        DENIED,
        /** 调用因技术错误或契约违反而失败。 */
        FAILED
    }

    private final Status status;

    private final @Nullable Object result;

    private final @Nullable Throwable error;

    private CallOutcome(Status status, @Nullable Object result, @Nullable Throwable error) {
        this.status = Objects.requireNonNull(status, "调用结果状态不能为空");
        this.result = result;
        this.error = error;
    }

    /** @param result 业务方法结果 @return 实际执行成功的结果 */
    public static CallOutcome success(@Nullable Object result) {
        return new CallOutcome(SUCCESS, result, null);
    }

    /** @param result 已保存的业务结果 @return 幂等重放结果 */
    public static CallOutcome replayed(@Nullable Object result) {
        return new CallOutcome(REPLAYED, result, null);
    }

    /** @param error 拒绝调用的业务异常 @return 被拒绝的结果 */
    public static CallOutcome denied(Throwable error) {
        return new CallOutcome(DENIED, null, Objects.requireNonNull(error, "拒绝异常不能为空"));
    }

    /** @param error 导致调用失败的异常 @return 失败结果 */
    public static CallOutcome failed(Throwable error) {
        return new CallOutcome(FAILED, null, Objects.requireNonNull(error, "失败异常不能为空"));
    }

    /** @return 当前结果状态 */
    @Contract(pure = true)
    public Status status() {
        return status;
    }

    /** @return 成功或重放状态的结果；拒绝和失败状态不包含结果 */
    @Contract(pure = true)
    public @Nullable Object result() {
        return result;
    }

    /** @return 拒绝或失败异常；成功状态为空 */
    @Contract(pure = true)
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "调用结果契约需要返回原始 Throwable，供审计解析器判断实际失败类型")
    public @Nullable Throwable error() {
        return error;
    }
}
