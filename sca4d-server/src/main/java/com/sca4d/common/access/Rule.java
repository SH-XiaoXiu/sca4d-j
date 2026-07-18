package com.sca4d.common.access;

import com.cedarpolicy.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link Rules} 生成的类型安全 Cedar 条件。
 *
 * <p>业务代码通过组合 Rule 声明关系，不直接拼接 Cedar 源码。Rule 只在创建 {@link Policy} 时读取。
 *
 * @param <R> 条件读取的业务资源类型
 */
public final class Rule<R> {

    private final String expression;
    private final Map<String, Attribute<R>> attributes;
    private final boolean requestTime;

    /**
     * 保存已经由 Rules 校验并生成的 Cedar 条件。
     *
     * @param expression Cedar 条件表达式
     * @param attributes 条件引用的资源属性
     */
    Rule(@NotNull String expression, @NotNull Map<String, Attribute<R>> attributes) {
        this(expression, attributes, false);
    }

    /**
     * 保存包含请求时间条件的 Cedar 条件。
     *
     * @param expression Cedar 条件表达式
     * @param attributes 条件引用的资源属性
     * @param requestTime 是否要求把请求开始时间写入 Cedar Context
     */
    Rule(
            @NotNull String expression,
            @NotNull Map<String, Attribute<R>> attributes,
            boolean requestTime) {
        this.expression = expression;
        this.attributes = Map.copyOf(new LinkedHashMap<>(attributes));
        this.requestTime = requestTime;
    }

    /** @return 由 Rules 生成的 Cedar 条件表达式 */
    @NotNull
    String expression() {
        return expression;
    }

    /** @return 条件引用的资源属性定义 */
    @NotNull
    Map<String, Attribute<R>> attributes() {
        return attributes;
    }

    /** @return 是否要求把请求开始时间写入 Cedar Context */
    boolean requestTime() {
        return requestTime;
    }

    /**
     * 一个 Rule 引用的 Cedar Resource 属性。
     *
     * @param <R> 属性来源资源类型
     */
    static final class Attribute<R> {

        private final String name;
        private final String type;
        private final boolean optional;
        private final Function<R, Value> value;

        /**
         * 创建资源属性定义。
         *
         * @param name Cedar 属性名
         * @param type Cedar Schema 类型
         * @param optional 资源值为空时是否省略属性
         * @param value 从业务资源读取 Cedar 值的函数
         */
        Attribute(
                @NotNull String name,
                @NotNull String type,
                boolean optional,
                @NotNull Function<R, Value> value) {
            this.name = name;
            this.type = type;
            this.optional = optional;
            this.value = value;
        }

        /** @return Cedar 属性名 */
        @NotNull
        String name() {
            return name;
        }

        /** @return Cedar Schema 属性类型 */
        @NotNull
        String type() {
            return type;
        }

        /** @return 资源值为空时是否省略属性 */
        boolean optional() {
            return optional;
        }

        /**
         * 读取业务资源对应的 Cedar 值。
         *
         * @param resource 当前业务资源
         * @return 可写入 Entity 的值；可选属性没有值时返回 {@code null}
         */
        @Nullable
        Value value(@NotNull R resource) {
            return value.apply(resource);
        }
    }
}
