package com.sca4d.core.api.id;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** 保存严格大于零的正整数标识，并提供无需拆出原始数值的比较操作。 */
public class Id extends Number implements Comparable<Id> {

    private final long value;

    /**
     * 创建正整数标识。
     *
     * @param value 正整数标识
     * @throws IllegalArgumentException value 不是正数时抛出
     */
    protected Id(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Id 必须大于零");
        }
        this.value = value;
    }

    /** @param value 正整数标识 @return 通用标识 */
    @Contract("_ -> new")
    public static @NotNull Id of(long value) {
        return new Id(value);
    }

    /** @return 供数据库、JSON 和外部适配边界使用的正整数值 */
    @Contract(pure = true)
    public final long value() {
        return value;
    }

    /** @return 是否与另一个 Id 数值相同 */
    @Contract(value = "null -> false", pure = true)
    public boolean isEqualTo(@Nullable Id other) {
        return other != null && value == other.value;
    }

    /** @return 是否与可精确转换为正整数的 Number 数值相同 */
    @Contract(value = "null -> false", pure = true)
    public boolean isEqualTo(@Nullable Number other) {
        return other != null && value == requireIntegralLong(other, "比较值");
    }

    /** @return 是否小于另一个 Id */
    @Contract(value = "null -> false", pure = true)
    public boolean isLessThan(@Nullable Id other) {
        return other != null && value < other.value;
    }

    /** @return 是否小于 Number */
    @Contract(value = "null -> false", pure = true)
    public boolean isLessThan(@Nullable Number other) {
        return other != null && value < requireIntegralLong(other, "比较值");
    }

    /** @return 是否大于另一个 Id */
    @Contract(value = "null -> false", pure = true)
    public boolean isGreaterThan(@Nullable Id other) {
        return other != null && value > other.value;
    }

    /** @return 是否大于 Number */
    @Contract(value = "null -> false", pure = true)
    public boolean isGreaterThan(@Nullable Number other) {
        return other != null && value > requireIntegralLong(other, "比较值");
    }

    /** @return 是否小于或等于另一个 Id */
    @Contract(value = "null -> false", pure = true)
    public boolean isAtMost(@Nullable Id other) {
        return other != null && value <= other.value;
    }

    /** @return 是否小于或等于 Number */
    @Contract(value = "null -> false", pure = true)
    public boolean isAtMost(@Nullable Number other) {
        return other != null && value <= requireIntegralLong(other, "比较值");
    }

    /** @return 是否大于或等于另一个 Id */
    @Contract(value = "null -> false", pure = true)
    public boolean isAtLeast(@Nullable Id other) {
        return other != null && value >= other.value;
    }

    /** @return 是否大于或等于 Number */
    @Contract(value = "null -> false", pure = true)
    public boolean isAtLeast(@Nullable Number other) {
        return other != null && value >= requireIntegralLong(other, "比较值");
    }

    @Override
    @Contract(pure = true)
    public int compareTo(@NotNull Id other) {
        return Long.compare(value, Objects.requireNonNull(other, "比较 Id 不能为空").value);
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other != null && getClass() == other.getClass() && value == ((Id) other).value;
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return Long.toString(value);
    }

    private static long requireIntegralLong(@NotNull Number value, @NotNull String name) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return positive(value.longValue(), name);
        }
        if (value instanceof BigInteger integer) {
            try {
                return positive(integer.longValueExact(), name);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(name + "超出正整数范围", exception);
            }
        }
        if (value instanceof BigDecimal decimal) {
            try {
                return positive(decimal.toBigIntegerExact().longValueExact(), name);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(name + "必须是 long 范围内的正整数", exception);
            }
        }
        if (value instanceof Float || value instanceof Double) {
            double doubleValue = value.doubleValue();
            long longValue = value.longValue();
            if (!Double.isFinite(doubleValue) || doubleValue != longValue) {
                throw new IllegalArgumentException(name + "必须是正整数");
            }
            return positive(longValue, name);
        }
        return positive(value.longValue(), name);
    }

    private static long positive(long value, @NotNull String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须大于零");
        }
        return value;
    }
}
