package com.sca4d.core.api.condition;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** 保存一组必须全部满足并产生同一个结果的条件。 */
public class DnfClause<T, R> {

    /** 一个可以同步或异步判断的纯条件。 */
    @FunctionalInterface
    public interface Condition<T> {

        /** 同步判断条件。 */
        boolean test(@NotNull T value);

        /** 使用调用方执行器异步判断条件；同步条件默认立即完成。 */
        default @NotNull CompletionStage<Boolean> testAsync(
                @NotNull T value,
                @NotNull Executor executor) {
            return CompletableFuture.completedFuture(test(value));
        }

        /** 把普通 Predicate 转为 DNF 条件。 */
        static <T> @NotNull Condition<T> of(@NotNull Predicate<? super T> predicate) {
            Predicate<? super T> checked = Objects.requireNonNull(predicate, "DNF 条件不能为空");
            return checked::test;
        }

        /** 创建由调用方执行器调度的异步条件。 */
        static <T> @NotNull Condition<T> async(
                @NotNull BiFunction<? super T, Executor, CompletionStage<Boolean>> predicate) {
            BiFunction<? super T, Executor, CompletionStage<Boolean>> checked =
                    Objects.requireNonNull(predicate, "异步 DNF 条件不能为空");
            return new Condition<>() {
                @Override
                public boolean test(@NotNull T value) {
                    return checked.apply(value, Runnable::run).toCompletableFuture().join();
                }

                @Override
                public @NotNull CompletionStage<Boolean> testAsync(
                        @NotNull T value,
                        @NotNull Executor executor) {
                    return Objects.requireNonNull(
                            checked.apply(value, executor),
                            "异步 DNF 条件不能返回 null");
                }
            };
        }
    }

    private final String code;

    private final List<Condition<? super T>> conditions;

    private final R result;

    /** 创建一个带稳定编码、条件和命中结果的 Clause。 */
    public DnfClause(
            @NotNull String code,
            @NotNull List<? extends Condition<? super T>> conditions,
            @NotNull R result) {
        this.code = requireCode(code);
        this.conditions = List.copyOf(Objects.requireNonNull(conditions, "DNF 条件不能为空"));
        if (this.conditions.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("DNF 条件不能包含 null");
        }
        this.result = Objects.requireNonNull(result, "DNF Clause 结果不能为空");
    }

    /** 创建一个由全部条件组成的 Clause。 */
    @SafeVarargs
    @CheckReturnValue
    public static <T, R> @NotNull DnfClause<T, R> allOf(
            @NotNull String code,
            @NotNull R result,
            @NotNull Condition<? super T>... conditions) {
        List<Condition<? super T>> values = new ArrayList<>(conditions.length);
        values.addAll(Arrays.asList(conditions));
        return new DnfClause<>(code, values, result);
    }

    /** 返回稳定 Clause 编码。 */
    public @NotNull String code() {
        return code;
    }

    /** 返回按声明顺序排列的条件。 */
    public @Unmodifiable @NotNull List<Condition<? super T>> conditions() {
        return conditions;
    }

    /** 返回 Clause 命中后产生的结果。 */
    public @NotNull R result() {
        return result;
    }

    /** 同步判断 Clause 内全部条件。空 Clause 表示恒真。 */
    public boolean matches(@NotNull T value) {
        Objects.requireNonNull(value, "DNF 输入不能为空");
        return conditions.stream().allMatch(condition -> condition.test(value));
    }

    /** 并行判断 Clause 内全部条件。 */
    public @NotNull CompletionStage<Boolean> matchesAsync(
            @NotNull T value,
            @NotNull Executor executor) {
        Objects.requireNonNull(value, "DNF 输入不能为空");
        Objects.requireNonNull(executor, "DNF 执行器不能为空");
        List<CompletionStage<Boolean>> stages = conditions.stream()
                .map(condition -> condition.testAsync(value, executor))
                .toList();
        CompletableFuture<?>[] futures = stages.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures)
                .thenApply(ignored -> stages.stream().allMatch(stage -> stage.toCompletableFuture().join()));
    }

    private static String requireCode(String code) {
        String checked = Objects.requireNonNull(code, "DNF Clause 编码不能为空").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("DNF Clause 编码不能为空");
        }
        return checked;
    }
}
