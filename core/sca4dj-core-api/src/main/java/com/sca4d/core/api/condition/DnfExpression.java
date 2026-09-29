package com.sca4d.core.api.condition;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** 保存按顺序选择第一个命中 Clause 的析取规则表达式。 */
public class DnfExpression<T, R> {

    private final List<DnfClause<T, R>> clauses;

    /** 创建按登记顺序执行的析取表达式；空集合表示没有命中结果。 */
    public DnfExpression(@NotNull List<DnfClause<T, R>> clauses) {
        Objects.requireNonNull(clauses, "DNF Clause 不能为空");
        this.clauses = List.copyOf(clauses);
        if (this.clauses.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("DNF Clause 不能包含 null");
        }
        long distinctCodes = this.clauses.stream().map(DnfClause::code).distinct().count();
        if (distinctCodes != this.clauses.size()) {
            throw new IllegalArgumentException("DNF Clause 编码不能重复");
        }
    }

    /** 创建任意一个 Clause 命中即可产生结果的表达式。 */
    @SafeVarargs
    @CheckReturnValue
    public static <T, R> @NotNull DnfExpression<T, R> anyOf(
            @NotNull DnfClause<T, R>... clauses) {
        return new DnfExpression<>(Arrays.asList(clauses));
    }

    /** 创建包含一个恒真 Clause 的表达式。 */
    @CheckReturnValue
    public static <T, R> @NotNull DnfExpression<T, R> always(
            @NotNull String code,
            @NotNull R result) {
        return new DnfExpression<>(List.of(DnfClause.allOf(code, result)));
    }

    /** 返回保持声明顺序的 Clause。 */
    public @Unmodifiable @NotNull List<DnfClause<T, R>> clauses() {
        return clauses;
    }

    /** 返回第一个命中的 Clause。 */
    @CheckReturnValue
    public @NotNull Optional<DnfClause<T, R>> matchingClause(@NotNull T value) {
        Objects.requireNonNull(value, "DNF 输入不能为空");
        return clauses.stream().filter(clause -> clause.matches(value)).findFirst();
    }

    /** 返回第一个命中 Clause 的结果。 */
    @CheckReturnValue
    public @NotNull Optional<R> evaluate(@NotNull T value) {
        return matchingClause(value).map(DnfClause::result);
    }

    /** 并行计算条件后按 Clause 声明顺序选择第一个命中结果。 */
    @CheckReturnValue
    public @NotNull CompletionStage<Optional<R>> evaluateAsync(
            @NotNull T value,
            @NotNull Executor executor) {
        Objects.requireNonNull(value, "DNF 输入不能为空");
        Objects.requireNonNull(executor, "DNF 执行器不能为空");
        List<CompletionStage<Boolean>> stages = clauses.stream()
                .map(clause -> clause.matchesAsync(value, executor))
                .toList();
        CompletableFuture<?>[] futures = stages.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply(ignored -> {
            for (int index = 0; index < clauses.size(); index++) {
                if (stages.get(index).toCompletableFuture().join()) {
                    return Optional.of(clauses.get(index).result());
                }
            }
            return Optional.empty();
        });
    }
}
