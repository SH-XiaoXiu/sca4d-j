package com.sca4d.common.web.view;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * View 层数据操作入口。
 *
 * <p>该类只服务响应视图的展示数据处理，用于减少 {@code View.from(...)} 内的重复转换代码。
 * 普通业务代码应直接使用 Hutool、JDK 或明确的业务方法，不应把该类当成全局工具。
 */
public final class DataOps {

    private DataOps() {
    }

    /**
     * 创建文本处理管道。
     *
     * @param value 原始值
     * @return 文本处理管道
     */
    public static @NotNull Text text(@Nullable Object value) {
        return new Text(Convert.toStr(value, null));
    }

    /**
     * 创建值处理管道。
     *
     * @param value 原始值
     * @param <T> 值类型
     * @return 值处理管道
     */
    public static <T> @NotNull Value<T> value(@Nullable T value) {
        return new Value<>(value);
    }

    /**
     * 创建集合处理管道。
     *
     * @param values 原始集合
     * @param <T> 元素类型
     * @return 集合处理管道
     */
    public static <T> @NotNull Seq<T> seq(@Nullable Collection<T> values) {
        return new Seq<>(values);
    }

    /**
     * 创建空 JSON 修改对象。
     *
     * @param objectMapper Jackson ObjectMapper
     * @return JSON 修改对象
     */
    public static @NotNull ViewJson json(@NotNull ObjectMapper objectMapper) {
        return new ViewJson(objectMapper, JsonNodeFactory.instance.objectNode());
    }

    /**
     * 文本处理管道。
     */
    public static final class Text {

        private final String value;

        private Text(@Nullable String value) {
            this.value = value;
        }

        /**
         * 去除首尾空白。
         *
         * @return 新文本管道
         */
        public @NotNull Text trim() {
            return new Text(CharSequenceUtil.trim(value));
        }

        /**
         * 转小写。
         *
         * @return 新文本管道
         */
        public @NotNull Text lower() {
            return new Text(Convert.toStr(value, "").toLowerCase(Locale.ROOT));
        }

        /**
         * 转大写。
         *
         * @return 新文本管道
         */
        public @NotNull Text upper() {
            return new Text(Convert.toStr(value, "").toUpperCase(Locale.ROOT));
        }

        /**
         * 截断文本。
         *
         * @param maxLength 最大长度，小于等于 0 时返回空字符串
         * @return 新文本管道
         */
        public @NotNull Text cut(int maxLength) {
            String text = Convert.toStr(value, "");
            if (maxLength <= 0) {
                return new Text("");
            }
            return new Text(text.length() <= maxLength ? text : text.substring(0, maxLength));
        }

        /**
         * 空白文本转 null。
         *
         * @return 新文本管道
         */
        public @NotNull Text nil() {
            return new Text(CharSequenceUtil.isBlank(value) ? null : value);
        }

        /**
         * 空白文本使用默认值。
         *
         * @param defaultValue 默认值
         * @return 新文本管道
         */
        public @NotNull Text or(@Nullable String defaultValue) {
            return new Text(CharSequenceUtil.isBlank(value) ? defaultValue : value);
        }

        /**
         * 判断是否有有效文本。
         *
         * @return 有有效文本时返回 true
         */
        public boolean has() {
            return CharSequenceUtil.isNotBlank(value);
        }

        /**
         * 获取字符串，null 会转为空字符串。
         *
         * @return 字符串
         */
        public @NotNull String get() {
            return Convert.toStr(value, "");
        }

        /**
         * 获取字符串，空白文本返回 null。
         *
         * @return 字符串或 null
         */
        public @Nullable String opt() {
            return CharSequenceUtil.isBlank(value) ? null : value;
        }
    }

    /**
     * 值处理管道。
     *
     * @param <T> 值类型
     */
    public static final class Value<T> {

        private final T value;

        private Value(@Nullable T value) {
            this.value = value;
        }

        /**
         * 映射值。
         *
         * @param mapper 映射函数
         * @param <R> 结果类型
         * @return 新值管道
         */
        public <R> @NotNull Value<R> map(@NotNull Function<T, R> mapper) {
            if (value == null) {
                return new Value<>(null);
            }
            return new Value<>(mapper.apply(value));
        }

        /**
         * 仅在满足条件时保留值。
         *
         * @param predicate 条件函数
         * @return 新值管道
         */
        public @NotNull Value<T> filter(@NotNull Predicate<T> predicate) {
            if (value == null || !predicate.test(value)) {
                return new Value<>(null);
            }
            return this;
        }

        /**
         * 转为文本管道。
         *
         * @return 文本管道
         */
        public @NotNull Text text() {
            return DataOps.text(value);
        }

        /**
         * 转为 Long。
         *
         * @return Long 值管道
         */
        public @NotNull Value<Long> lng() {
            return new Value<>(Convert.toLong(value, null));
        }

        /**
         * 转为 BigDecimal。
         *
         * @return BigDecimal 值管道
         */
        public @NotNull Value<BigDecimal> dec() {
            return new Value<>(Convert.toBigDecimal(value, null));
        }

        /**
         * 转为 Boolean。
         *
         * @return Boolean 值管道
         */
        public @NotNull Value<Boolean> bool() {
            return new Value<>(Convert.toBool(value, null));
        }

        /**
         * 获取值，为 null 时使用默认值。
         *
         * @param defaultValue 默认值
         * @return 当前值或默认值
         */
        public @Nullable T or(@Nullable T defaultValue) {
            return value == null ? defaultValue : value;
        }

        /**
         * 获取值。
         *
         * @return 当前值
         */
        public @Nullable T get() {
            return value;
        }
    }

    /**
     * 集合处理管道。
     *
     * @param <T> 元素类型
     */
    public static final class Seq<T> {

        private final Collection<T> values;

        private Seq(@Nullable Collection<T> values) {
            this.values = values;
        }

        /**
         * 映射集合元素。
         *
         * @param mapper 映射函数
         * @param <R> 结果元素类型
         * @return 新集合管道
         */
        public <R> @NotNull Seq<R> map(@NotNull Function<T, R> mapper) {
            return new Seq<>(list().stream().map(mapper).toList());
        }

        /**
         * 过滤集合元素。
         *
         * @param predicate 条件函数
         * @return 新集合管道
         */
        public @NotNull Seq<T> filter(@NotNull Predicate<T> predicate) {
            return new Seq<>(list().stream().filter(predicate).toList());
        }

        /**
         * 获取安全列表。
         *
         * @return 列表
         */
        public @NotNull List<T> list() {
            if (CollUtil.isEmpty(values)) {
                return List.of();
            }
            return values.stream().toList();
        }

        /**
         * 获取集合大小。
         *
         * @return 集合大小
         */
        public long size() {
            return CollUtil.size(values);
        }
    }
}
