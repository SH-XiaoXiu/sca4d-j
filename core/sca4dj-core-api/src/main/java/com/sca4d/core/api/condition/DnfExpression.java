package com.sca4d.core.api.condition;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 保存任意一个 Clause 满足即可通过的析取范式表达式。
 *
 * @param <T> 各 Clause 检查的输入类型
 */
@SuppressWarnings({"ClassCanBeRecord", "unused"})
public final class DnfExpression<T> implements Predicate<T> {

    private final List<DnfClause<T>> clauses;

    /**
     * 创建按登记顺序执行的析取表达式。
     *
     * @param clauses 任意一个满足即可通过的 Clause；空集合表示恒假
     */
    public DnfExpression(@NotNull List<DnfClause<T>> clauses) {
        Objects.requireNonNull(clauses, "DNF Clause 不能为空");
        this.clauses = List.copyOf(clauses);
        if (this.clauses.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("DNF Clause 不能包含 null");
        }
    }

    /**
     * 创建包含指定 Clause 的析取表达式。
     *
     * @param clauses 任意一个满足即可通过的 Clause
     * @param <T> 待判断值类型
     * @return 新的析取表达式
     */
    @SafeVarargs
    @CheckReturnValue
    public static <T> @NotNull DnfExpression<T> anyOf(@NotNull DnfClause<T>... clauses) {
        return new DnfExpression<>(List.of(clauses));
    }

    /**
     * 创建恒真表达式。
     *
     * @param <T> 待判断值类型
     * @return 包含一个空 Clause 的表达式
     */
    @CheckReturnValue
    public static <T> @NotNull DnfExpression<T> always() {
        return new DnfExpression<>(List.of(new DnfClause<>(List.of())));
    }

    /**
     * 读取该表达式按登记顺序检查的全部 Clause。
     *
     * @return 保持登记顺序的不可变 Clause 列表
     */
    public @Unmodifiable @NotNull List<DnfClause<T>> clauses() {
        return clauses;
    }

    /**
     * 按登记顺序短路判断全部 Clause。
     *
     * @param value 待判断值
     * @return 任意 Clause 满足时返回 {@code true}
     */
    @Override
    public boolean test(@NotNull T value) {
        Objects.requireNonNull(value, "DNF 待判断值不能为空");
        return clauses.stream().anyMatch(clause -> clause.test(value));
    }
}
