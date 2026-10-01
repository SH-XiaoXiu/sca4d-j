package com.sca4d.core.api.job;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存 JobPlan 两个并发分支均成功后产生的类型化结果。
 *
 * @param <L> 左分支输出类型
 * @param <R> 右分支输出类型
 */
public class ParallelResults<L, R> {

    private final @NotNull L left;

    private final @NotNull R right;

    /**
     * 创建二路成功结果。
     *
     * @param left 左分支成功输出
     * @param right 右分支成功输出
     * @throws NullPointerException 任一分支输出为空时抛出
     */
    public ParallelResults(@NotNull L left, @NotNull R right) {
        this.left = Objects.requireNonNull(left, "左分支结果不能为空");
        this.right = Objects.requireNonNull(right, "右分支结果不能为空");
    }

    /**
     * 左分支结果。
     *
     * @return {@code parallel(...)} 中先声明的分支输出
     */
    @Contract(pure = true)
    public @NotNull L left() {
        return left;
    }

    /**
     * 右分支结果。
     *
     * @return {@code parallel(...)} 中后声明的分支输出
     */
    @Contract(pure = true)
    public @NotNull R right() {
        return right;
    }

    /**
     * 返回声明合并 Pipeline 输入类型时使用的具体 Class。
     *
     * @param <L> 左分支输出类型
     * @param <R> 右分支输出类型
     * @return 保留泛型约束的 ParallelResults Class
     */
    @SuppressWarnings("unchecked")
    @CheckReturnValue
    @Contract(pure = true)
    public static <L, R> @NotNull Class<ParallelResults<L, R>> type() {
        return (Class<ParallelResults<L, R>>) (Class<?>) ParallelResults.class;
    }
}
