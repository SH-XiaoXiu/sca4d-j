package com.sca4d.iot.api.model;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 使用一个不可变递归对象表达物模型允许的值及其约束。 */
public final class ThingValueType {

    private final @NotNull ThingValueKind kind;

    private final @Nullable String minimum;

    private final @Nullable String maximum;

    private final @Nullable Integer scale;

    private final @Nullable String unit;

    private final @Nullable Integer maximumLength;

    private final @Nullable String pattern;

    private final @Nullable Pattern compiledPattern;

    private final @Nullable Integer maximumItems;

    private final @NotNull Set<String> enumValues;

    private final @NotNull Map<String, ThingValueType> requiredFields;

    private final @NotNull Map<String, ThingValueType> optionalFields;

    private final @Nullable ThingValueType elementType;

    private ThingValueType(@NotNull Builder builder) {
        this.kind = builder.kind;
        this.minimum = builder.minimum;
        this.maximum = builder.maximum;
        this.scale = builder.scale;
        this.unit = builder.unit;
        this.maximumLength = builder.maximumLength;
        this.pattern = builder.pattern;
        this.compiledPattern = builder.compiledPattern;
        this.maximumItems = builder.maximumItems;
        this.enumValues = Collections.unmodifiableSet(new LinkedHashSet<>(builder.enumValues));
        this.requiredFields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.requiredFields));
        this.optionalFields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.optionalFields));
        this.elementType = builder.elementType;
    }

    /** @return 不带额外约束的 boolean 类型 */
    @CheckReturnValue
    public static @NotNull ThingValueType booleanType() {
        return new Builder(ThingValueKind.BOOLEAN).build();
    }

    /** @return 可继续声明范围的 integer 类型 Builder */
    @CheckReturnValue
    public static @NotNull Builder integerType() {
        return new Builder(ThingValueKind.INTEGER);
    }

    /** @return 可继续声明范围、精度和单位的 decimal 类型 Builder */
    @CheckReturnValue
    public static @NotNull Builder decimalType() {
        return new Builder(ThingValueKind.DECIMAL);
    }

    /** @return 可继续声明最大长度的 string 类型 Builder */
    @CheckReturnValue
    public static @NotNull Builder stringType() {
        return new Builder(ThingValueKind.STRING);
    }

    /** @return 可继续声明允许值的 enum 类型 Builder */
    @CheckReturnValue
    public static @NotNull Builder enumType() {
        return new Builder(ThingValueKind.NUM);
    }

    /** @return 使用 {@link Instant} 的 {@code timestamp} 类型 */
    @CheckReturnValue
    public static @NotNull ThingValueType timestampType() {
        return new Builder(ThingValueKind.TIMESTAMP).build();
    }

    /** @return 可继续声明递归字段的 object 类型 Builder */
    @CheckReturnValue
    public static @NotNull Builder objectType() {
        return new Builder(ThingValueKind.OBJECT);
    }

    /**
     * 创建可继续声明数量限制的 array 类型 Builder。
     *
     * @param elementType 每个元素采用的递归值类型
     * @return array Builder
     */
    @CheckReturnValue
    public static @NotNull Builder arrayType(@NotNull ThingValueType elementType) {
        Builder builder = new Builder(ThingValueKind.ARRAY);
        builder.elementType = Objects.requireNonNull(elementType, "数组元素类型不能为空");
        return builder;
    }

    /** @return 固定值类型种类 */
    public @NotNull ThingValueKind kind() {
        return kind;
    }

    /** @return 可选最小值十进制文本 */
    public @NotNull Optional<String> minimum() {
        return Optional.ofNullable(minimum);
    }

    /** @return 可选最大值十进制文本 */
    public @NotNull Optional<String> maximum() {
        return Optional.ofNullable(maximum);
    }

    /** @return 可选 decimal 小数位数 */
    public @NotNull Optional<Integer> scale() {
        return Optional.ofNullable(scale);
    }

    /** @return 可选展示单位 */
    public @NotNull Optional<String> unit() {
        return Optional.ofNullable(unit);
    }

    /** @return 可选字符串最大长度 */
    public @NotNull Optional<Integer> maximumLength() {
        return Optional.ofNullable(maximumLength);
    }

    /** @return 可选 Java 正则表达式；校验时要求字符串完整匹配 */
    @Contract(pure = true)
    public @NotNull Optional<String> pattern() {
        return Optional.ofNullable(pattern);
    }

    /**
     * 使用构建物模型时已经校验并编译的正则表达式检查字符串。
     *
     * @param value 已通过 `maximumLength` 检查的字符串
     * @return 没有 pattern 或字符串完整匹配时为 {@code true}
     */
    @CheckReturnValue
    @Contract(pure = true)
    public boolean matchesPattern(@NotNull String value) {
        Pattern compiled = compiledPattern;
        return compiled == null || compiled.matcher(Objects.requireNonNull(value, "字符串不能为空")).matches();
    }

    /** @return 可选数组最大元素数 */
    public @NotNull Optional<Integer> maximumItems() {
        return Optional.ofNullable(maximumItems);
    }

    /** @return enum 稳定 code 的不可修改集合 */
    public @NotNull @Unmodifiable Set<String> enumValues() {
        return enumValues;
    }

    /** @return object 必需字段的不可修改映射 */
    public @NotNull @Unmodifiable Map<String, ThingValueType> requiredFields() {
        return requiredFields;
    }

    /** @return object 可选字段的不可修改映射 */
    public @NotNull @Unmodifiable Map<String, ThingValueType> optionalFields() {
        return optionalFields;
    }

    /** @return array 元素类型；其他类型为空 */
    public @NotNull Optional<ThingValueType> elementType() {
        return Optional.ofNullable(elementType);
    }

    /** 逐项构造一个递归物模型值类型。 */
    public static final class Builder {

        private final @NotNull ThingValueKind kind;

        private @Nullable String minimum;

        private @Nullable String maximum;

        private @Nullable Integer scale;

        private @Nullable String unit;

        private @Nullable Integer maximumLength;

        private @Nullable String pattern;

        private @Nullable Pattern compiledPattern;

        private @Nullable Integer maximumItems;

        private final @NotNull Set<String> enumValues = new LinkedHashSet<>();

        private final @NotNull Map<String, ThingValueType> requiredFields = new LinkedHashMap<>();

        private final @NotNull Map<String, ThingValueType> optionalFields = new LinkedHashMap<>();

        private @Nullable ThingValueType elementType;

        private Builder(@NotNull ThingValueKind kind) {
            this.kind = kind;
        }

        /** @param value 最小值十进制文本 @return 当前 Builder */
        public @NotNull Builder minimum(@NotNull String value) {
            assertNumeric();
            this.minimum = decimal(value, "最小值");
            return this;
        }

        /** @param value 最大值十进制文本 @return 当前 Builder */
        public @NotNull Builder maximum(@NotNull String value) {
            assertNumeric();
            this.maximum = decimal(value, "最大值");
            return this;
        }

        /** @param value decimal 允许的小数位数 @return 当前 Builder */
        public @NotNull Builder scale(int value) {
            if (kind != ThingValueKind.DECIMAL || value < 0) {
                throw new IllegalArgumentException("scale 只适用于 decimal 且不能为负数");
            }
            this.scale = value;
            return this;
        }

        /** @param value 展示单位 @return 当前 Builder */
        public @NotNull Builder unit(@NotNull String value) {
            assertNumeric();
            this.unit = TextUtils.requireText(value, "数值单位");
            return this;
        }

        /** @param value 字符串最大长度 @return 当前 Builder */
        public @NotNull Builder maximumLength(int value) {
            if (kind != ThingValueKind.STRING || value < 1) {
                throw new IllegalArgumentException("maximumLength 只适用于 string 且必须为正数");
            }
            this.maximumLength = value;
            return this;
        }

        /**
         * 声明字符串必须完整匹配的 Java 正则表达式。
         *
         * <p>调用 `build()` 前必须同时声明 `maximumLength`，运行校验先检查长度，再执行已经编译的表达式。
         *
         * @param value Java 正则表达式
         * @return 当前 Builder
         */
        public @NotNull Builder pattern(@NotNull String value) {
            if (kind != ThingValueKind.STRING) {
                throw new IllegalStateException("pattern 只适用于 string");
            }
            String checked = TextUtils.requireText(value, "字符串 pattern");
            try {
                this.compiledPattern = Pattern.compile(checked);
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException("字符串 pattern 不是合法 Java 正则表达式", exception);
            }
            this.pattern = checked;
            return this;
        }

        /** @param value 数组最大元素数 @return 当前 Builder */
        public @NotNull Builder maximumItems(int value) {
            if (kind != ThingValueKind.ARRAY || value < 1) {
                throw new IllegalArgumentException("maximumItems 只适用于 array 且必须为正数");
            }
            this.maximumItems = value;
            return this;
        }

        /** @param code enum 稳定 code @return 当前 Builder */
        public @NotNull Builder value(@NotNull String code) {
            if (kind != ThingValueKind.NUM) {
                throw new IllegalStateException("value 只适用于 enum");
            }
            String checkedCode = TextUtils.requireText(code, "enum code");
            if (!enumValues.add(checkedCode)) {
                throw new IllegalArgumentException("enum code 重复: " + checkedCode);
            }
            return this;
        }

        /** @param code 必需字段 code @param type 字段值类型 @return 当前 Builder */
        public @NotNull Builder requiredField(@NotNull String code, @NotNull ThingValueType type) {
            assertObject();
            putField(requiredFields, code, type);
            return this;
        }

        /** @param code 可选字段 code @param type 字段值类型 @return 当前 Builder */
        public @NotNull Builder optionalField(@NotNull String code, @NotNull ThingValueType type) {
            assertObject();
            putField(optionalFields, code, type);
            return this;
        }

        /** @return 已校验并且不可修改的值类型 */
        @CheckReturnValue
        public @NotNull ThingValueType build() {
            if (pattern != null && maximumLength == null) {
                throw new IllegalArgumentException("string pattern 必须同时声明 maximumLength");
            }
            if (minimum != null
                    && maximum != null
                    && new BigDecimal(minimum).compareTo(new BigDecimal(maximum)) > 0) {
                throw new IllegalArgumentException("最小值不能大于最大值");
            }
            if (kind == ThingValueKind.NUM && enumValues.isEmpty()) {
                throw new IllegalArgumentException("enum 必须至少包含一个值");
            }
            if (kind == ThingValueKind.OBJECT) {
                for (String code : requiredFields.keySet()) {
                    if (optionalFields.containsKey(code)) {
                        throw new IllegalArgumentException("object 字段不能同时为必需和可选: " + code);
                    }
                }
            }
            if (kind == ThingValueKind.ARRAY && elementType == null) {
                throw new IllegalArgumentException("array 必须声明元素类型");
            }
            return new ThingValueType(this);
        }

        /** 要求当前 Builder 表示允许声明数值约束的类型。 */
        private void assertNumeric() {
            if (kind != ThingValueKind.INTEGER && kind != ThingValueKind.DECIMAL) {
                throw new IllegalStateException("数值约束只适用于 integer 或 decimal");
            }
        }

        /** 要求当前 Builder 表示允许声明具名字段的 object 类型。 */
        private void assertObject() {
            if (kind != ThingValueKind.OBJECT) {
                throw new IllegalStateException("字段只适用于 object");
            }
        }

        /** 加入一个 object 字段，并拒绝必需字段和可选字段之间出现重复 code。 */
        private void putField(Map<String, ThingValueType> fields, String code, ThingValueType type) {
            String checkedCode = TextUtils.requireText(code, "object 字段 code");
            if (requiredFields.containsKey(checkedCode) || optionalFields.containsKey(checkedCode)) {
                throw new IllegalArgumentException("object 字段重复: " + checkedCode);
            }
            fields.put(checkedCode, Objects.requireNonNull(type, "object 字段类型不能为空"));
        }

        /** 把十进制文本转换成无指数格式，无法解析时报告对应约束名称。 */
        private static String decimal(String value, String name) {
            String checked = TextUtils.requireText(value, name);
            try {
                return new BigDecimal(checked).toPlainString();
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + "必须是十进制数", exception);
            }
        }

    }
}
