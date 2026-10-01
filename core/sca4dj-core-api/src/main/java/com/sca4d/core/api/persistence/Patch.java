package com.sca4d.core.api.persistence;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 表达 Repository 执行部分更新时对一个数据库字段的写入意图。
 *
 * <p>{@link #ignore()} 保留数据库原值，{@link #set(Object)} 写入非空值，{@link #clear()} 写入数据库
 * {@code NULL}。数值零、{@code false}、空字符串和空集合都是可以写入的普通值。
 *
 * @param <T> 字段写入值的 Java 类型
 */
public class Patch<T> {

    private static final Patch<?> IGNORE = new Patch<>(State.IGNORE, null);

    private static final Patch<?> CLEAR = new Patch<>(State.CLEAR, null);

    private final @NotNull State state;

    private final @Nullable T value;

    private Patch(@NotNull State state, @Nullable T value) {
        this.state = state;
        this.value = value;
    }

    /**
     * 创建不修改数据库字段的意图。
     *
     * @param <T> 字段值类型
     * @return 可复用的忽略意图
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static <T> @NotNull Patch<T> ignore() {
        return cast(IGNORE);
    }

    /**
     * 创建把非空值写入数据库字段的意图。
     *
     * @param value 要写入的值；数值零、{@code false}、空字符串和空集合不会转成忽略或清除
     * @param <T> 字段值类型
     * @return 包含指定值的写入意图
     * @throws NullPointerException value 为 {@code null} 时抛出；写入数据库 {@code NULL} 应使用 {@link #clear()}
     */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static <T> @NotNull Patch<T> set(@NotNull T value) {
        return new Patch<>(State.SET, Objects.requireNonNull(value, "Patch SET 值不能为空"));
    }

    /**
     * 创建把数据库字段写为 {@code NULL} 的意图。
     *
     * @param <T> 字段值类型
     * @return 可复用的清除意图
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static <T> @NotNull Patch<T> clear() {
        return cast(CLEAR);
    }

    /**
     * 使用回调把业务处理结果解析成字段写入意图。
     *
     * <p>source 可以为 {@code null}，回调必须明确决定此时返回忽略、写入还是清除。当前方法不校验 HTTP
     * Request、实体字段或业务规则。
     *
     * @param source 回调需要读取的业务处理结果
     * @param resolver 返回明确写入意图的回调
     * @param <S> 业务处理结果类型
     * @param <T> 字段值类型
     * @return 回调返回的写入意图
     * @throws NullPointerException resolver 或回调结果为 {@code null} 时抛出
     */
    @CheckReturnValue
    public static <S, T> @NotNull Patch<T> resolve(
            @Nullable S source,
            @NotNull Function<? super S, ? extends Patch<T>> resolver) {
        Function<? super S, ? extends Patch<T>> checked = Objects.requireNonNull(resolver, "Patch 解析回调不能为空");
        return Objects.requireNonNull(checked.apply(source), "Patch 解析回调不能返回空值");
    }

    /**
     * 创建按条件选择一个字段写入意图的声明式构造器。
     *
     * @param <T> 字段值类型
     * @return 尚未登记条件的构造器
     */
    @CheckReturnValue
    @Contract(value = "-> new", pure = true)
    public static <T> @NotNull Choice<T> choose() {
        return new Choice<>();
    }

    /**
     * 判断当前意图是否保留数据库原值。
     *
     * @return 保留数据库原值时为 {@code true}
     */
    @Contract(pure = true)
    public boolean isIgnore() {
        return state == State.IGNORE;
    }

    /**
     * 判断当前意图是否写入一个非空值。
     *
     * @return 写入非空值时为 {@code true}
     */
    @Contract(pure = true)
    public boolean isSet() {
        return state == State.SET;
    }

    /**
     * 判断当前意图是否把数据库字段写为 {@code NULL}。
     *
     * @return 写入数据库 {@code NULL} 时为 {@code true}
     */
    @Contract(pure = true)
    public boolean isClear() {
        return state == State.CLEAR;
    }

    /**
     * 读取 SET 状态携带的非空值。
     *
     * @return 要写入数据库字段的非空值
     * @throws IllegalStateException 当前状态不是 SET 时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull T value() {
        if (!isSet()) {
            throw new IllegalStateException("只有 SET 状态的 Patch 包含值");
        }
        return Objects.requireNonNull(value);
    }

    /**
     * 通过 JavaBean 属性读取 SET 状态携带的非空值，供 MyBatis 等映射框架绑定参数。
     *
     * @return 要写入数据库字段的非空值
     * @throws IllegalStateException 当前状态不是 SET 时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull T getValue() {
        return value();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Patch<?> patch
                        && state == patch.state
                        && Objects.equals(value, patch.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, value);
    }

    @Override
    public String toString() {
        return isSet() ? "Patch[SET " + value + "]" : "Patch[" + state + "]";
    }

    /** 把只读单例转换成调用方需要的字段值泛型；单例不保存 T 类型的值。 */
    @SuppressWarnings("unchecked")
    private static <T> @NotNull Patch<T> cast(@NotNull Patch<?> patch) {
        return (Patch<T>) patch;
    }

    /** 保存字段更新的三个互斥状态。 */
    private enum State {
        /** 保留数据库原值。 */
        IGNORE,

        /** 写入非空值。 */
        SET,

        /** 写入数据库 NULL。 */
        CLEAR
    }

    /**
     * 按声明顺序收集条件，并在结束时生成唯一字段写入意图。
     *
     * <p>条件允许零个或一个命中；第二个条件命中时立即抛出异常。值 Supplier 只在对应条件唯一命中并调用结束方法时执行。
     *
     * @param <T> 字段值类型
     */
    public static class Choice<T> {

        private @Nullable Supplier<Patch<T>> selected;

        private Choice() {
        }

        /**
         * 条件命中时选择写入给定非空值。
         *
         * @param condition 是否选择当前值
         * @param value 条件唯一命中时写入的值
         * @return 当前构造器
         * @throws IllegalStateException 已有另一个条件命中时抛出
         * @throws NullPointerException value 为 {@code null} 时抛出
         */
        @Contract(value = "_, _ -> this", mutates = "this")
        public @NotNull Choice<T> setIf(boolean condition, @NotNull T value) {
            T checked = Objects.requireNonNull(value, "Patch SET 值不能为空");
            return selectIf(condition, () -> Patch.set(checked));
        }

        /**
         * 条件命中时延迟计算并写入非空值。
         *
         * @param condition 是否选择当前值
         * @param value 条件唯一命中时才执行的值 Supplier
         * @return 当前构造器
         * @throws IllegalStateException 已有另一个条件命中时抛出
         * @throws NullPointerException value 或 Supplier 结果为 {@code null} 时抛出
         */
        @Contract(value = "_, _ -> this", mutates = "this")
        public @NotNull Choice<T> setIf(
                boolean condition,
                @NotNull Supplier<? extends T> value) {
            Supplier<? extends T> checked = Objects.requireNonNull(value, "Patch SET Supplier 不能为空");
            return selectIf(condition, () -> Patch.set(checked.get()));
        }

        /**
         * 条件命中时选择把数据库字段写为 {@code NULL}。
         *
         * @param condition 是否清除数据库字段
         * @return 当前构造器
         * @throws IllegalStateException 已有其他条件命中时抛出
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Choice<T> clearIf(boolean condition) {
            return selectIf(condition, Patch::clear);
        }

        /**
         * 在没有条件命中时保留数据库原值。
         *
         * @return 唯一命中的意图，或者 IGNORE
         */
        @CheckReturnValue
        public @NotNull Patch<T> otherwiseIgnore() {
            return finish(Patch::ignore);
        }

        /**
         * 在没有条件命中时写入给定非空值。
         *
         * @param value 没有条件命中时写入的非空值
         * @return 唯一命中的意图，或者包含 value 的 SET
         * @throws NullPointerException value 为 {@code null} 时抛出
         */
        @CheckReturnValue
        public @NotNull Patch<T> otherwiseSet(@NotNull T value) {
            T checked = Objects.requireNonNull(value, "Patch SET 值不能为空");
            return finish(() -> Patch.set(checked));
        }

        /**
         * 在没有条件命中时延迟计算并写入非空值。
         *
         * @param value 没有条件命中时才执行的值 Supplier
         * @return 唯一命中的意图，或者包含 Supplier 结果的 SET
         * @throws NullPointerException value 或 Supplier 结果为 {@code null} 时抛出
         */
        @CheckReturnValue
        public @NotNull Patch<T> otherwiseSet(@NotNull Supplier<? extends T> value) {
            Supplier<? extends T> checked = Objects.requireNonNull(value, "Patch SET Supplier 不能为空");
            return finish(() -> Patch.set(checked.get()));
        }

        /**
         * 在没有条件命中时把数据库字段写为 {@code NULL}。
         *
         * @return 唯一命中的意图，或者 CLEAR
         */
        @CheckReturnValue
        public @NotNull Patch<T> otherwiseClear() {
            return finish(Patch::clear);
        }

        /** 登记唯一命中的条件分支，第二个命中分支直接失败。 */
        private @NotNull Choice<T> selectIf(
                boolean condition,
                @NotNull Supplier<Patch<T>> candidate) {
            if (!condition) {
                return this;
            }
            if (selected != null) {
                throw new IllegalStateException("Patch 条件不能同时命中多个分支");
            }
            selected = candidate;
            return this;
        }

        /** 执行唯一命中的分支；没有分支命中时执行调用方明确给出的默认分支。 */
        private @NotNull Patch<T> finish(@NotNull Supplier<Patch<T>> fallback) {
            Supplier<Patch<T>> result = selected == null ? fallback : selected;
            return Objects.requireNonNull(result.get(), "Patch 条件分支不能返回空值");
        }
    }
}
