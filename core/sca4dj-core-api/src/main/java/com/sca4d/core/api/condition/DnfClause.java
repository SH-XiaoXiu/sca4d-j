package com.sca4d.core.api.condition;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 保存析取范式中必须全部满足的一组条件。
 *
 * @param <T> 条件检查的输入类型
 */
@SuppressWarnings({"ClassCanBeRecord", "unused"})
public class DnfClause<T> implements Predicate<T> {

    private final List<Predicate<? super T>> conditions;

    /**
     * 创建按登记顺序执行的合取条件。
     *
     * @param conditions 必须全部满足的条件；空集合表示恒真
     */
    public DnfClause(@NotNull List<? extends Predicate<? super T>> conditions) {
        Objects.requireNonNull(conditions, "DNF 条件不能为空");
        this.conditions = List.copyOf(conditions);
        if (this.conditions.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("DNF 条件不能包含 null");
        }
    }

    /**
     * 创建包含指定条件的合取条件。
     *
     * @param conditions 必须全部满足的条件
     * @param <T> 待判断值类型
     * @return 新的合取条件
     */
    @SafeVarargs
    @CheckReturnValue
    @SuppressWarnings("Convert2Diamond")
    public static <T> @NotNull DnfClause<T> allOf(@NotNull Predicate<? super T>... conditions) {
        return new DnfClause<T>(Arrays.asList(conditions));
    }

    /**
     * 读取该子句按登记顺序检查的全部条件。
     *
     * @return 保持登记顺序的不可变条件列表
     */
    public @Unmodifiable @NotNull List<Predicate<? super T>> conditions() {
        return conditions;
    }

    /**
     * 按登记顺序短路判断全部条件。
     *
     * @param value 待判断值
     * @return 全部条件满足时返回 {@code true}
     */
    @Override
    public boolean test(@NotNull T value) {
        Objects.requireNonNull(value, "DNF 待判断值不能为空");
        return conditions.stream().allMatch(condition -> condition.test(value));
    }
}
