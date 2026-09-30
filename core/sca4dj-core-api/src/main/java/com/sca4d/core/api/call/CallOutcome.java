package com.sca4d.core.api.call;

import static com.sca4d.core.api.call.CallOutcome.Status.DENIED;
import static com.sca4d.core.api.call.CallOutcome.Status.FAILED;
import static com.sca4d.core.api.call.CallOutcome.Status.REPLAYED;
import static com.sca4d.core.api.call.CallOutcome.Status.SUCCESS;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
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

    private final @Nullable Object result;

    private final Status status;

    private final @Nullable Throwable completionFailure;

    private final @Nullable Throwable error;

    private CallOutcome(Status status, @Nullable Object result, @Nullable Throwable error) {
        this(status, result, error, null);
    }

    private CallOutcome(
            Status status,
            @Nullable Object result,
            @Nullable Throwable error,
            @Nullable Throwable completionFailure) {
        this.status = Objects.requireNonNull(status, "调用结果状态不能为空");
        this.result = result;
        this.error = error;
        this.completionFailure = completionFailure;
    }

    /** 保留已发生的执行结果并登记外层完成失败；原始失败仍是向调用方抛出的主异常。 */
    public @NotNull CallOutcome withCompletionFailure(@NotNull Throwable failure) {
        Objects.requireNonNull(failure, "完成失败不能为空");
        Throwable primary = completionFailure == null ? failure : completionFailure;
        if (primary != failure) {
            primary.addSuppressed(failure);
        }
        if (error != null && error != failure && error != primary) {
            error.addSuppressed(failure);
        }
        return new CallOutcome(status, result, error, primary);
    }

    /** @return 方法已取得结果后发生的失败，不改变 SUCCESS 或 REPLAYED 等执行事实 */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "保留原始完成异常，供调用方和审计定位实际失败")
    public @Nullable Throwable completionFailure() {
        return completionFailure;
    }

    /** 返回结果，或原样抛出执行和完成阶段的主异常；允许声明为 void 的方法返回 null。 */
    public @Nullable Object valueOrThrow() throws Throwable {
        if (error != null) {
            throw error;
        }
        if (completionFailure != null) {
            throw completionFailure;
        }
        return result;
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
