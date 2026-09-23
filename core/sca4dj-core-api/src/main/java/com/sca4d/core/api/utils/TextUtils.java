package com.sca4d.core.api.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 判断、校验和规范化普通文本输入。 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * 判断文本是否包含至少一个非空白字符。
     *
     * @param value 待检查文本
     * @return 文本非空且不全是 Unicode 空白字符时为 {@code true}
     */
    @Contract(pure = true)
    public static boolean hasText(@Nullable CharSequence value) {
        return value != null && !value.toString().isBlank();
    }

    /**
     * 校验文本非空白但保留原始字符。
     *
     * <p>资源标识、Token、协议键等需要精确匹配的值使用此方法，不清除首尾空白。
     *
     * @param value 原始文本
     * @param name 异常中使用的字段名称
     * @return 未修改的非空白文本
     */
    @Contract(value = "null, _ -> fail; !null, _ -> param1", pure = true)
    public static @NotNull String requireExactText(@Nullable String value, @NotNull String name) {
        String checkedName = Objects.requireNonNull(name, "字段名称不能为空");
        String checked = Objects.requireNonNull(value, checkedName + "不能为空");
        if (!hasText(checked)) {
            throw new IllegalArgumentException(checkedName + "不能为空白");
        }
        return checked;
    }

    /**
     * 校验并返回去除首尾 Unicode 空白字符的文本。
     *
     * @param value 原始文本
     * @param name 异常中使用的字段名称
     * @return 非空白文本
     */
    @Contract(pure = true)
    public static @NotNull String requireText(@Nullable String value, @NotNull String name) {
        return requireExactText(value, name).strip();
    }

    /**
     * 校验必填文本并限制规范化后的最大 UTF-16 长度。
     *
     * @param value 原始文本
     * @param name 异常中使用的字段名称
     * @param maximumLength 允许的最大长度，必须为正数
     * @return 去除首尾空白后的文本
     */
    @Contract(pure = true)
    public static @NotNull String requireText(
            @Nullable String value,
            @NotNull String name,
            int maximumLength) {
        String checkedName = Objects.requireNonNull(name, "字段名称不能为空");
        if (maximumLength <= 0) {
            throw new IllegalArgumentException(checkedName + "最大长度必须为正数");
        }
        String checked = requireText(value, checkedName);
        if (checked.length() > maximumLength) {
            throw new IllegalArgumentException(checkedName + "长度不能超过 " + maximumLength);
        }
        return checked;
    }

    /**
     * 规范化可选文本。
     *
     * @param value 原始文本
     * @return 去除首尾空白后的文本；空值或空白文本返回空值
     */
    @Contract(value = "null -> null", pure = true)
    public static @Nullable String optionalText(@Nullable String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.strip();
    }

    /**
     * 规范化可选文本并限制最大 UTF-16 长度。
     *
     * @param value 原始文本
     * @param name 异常中使用的字段名称
     * @param maximumLength 允许的最大长度，必须为正数
     * @return 去除首尾空白后的文本；空值或空白文本返回空值
     */
    @Contract(pure = true)
    public static @Nullable String optionalText(
            @Nullable String value,
            @NotNull String name,
            int maximumLength) {
        String checkedName = Objects.requireNonNull(name, "字段名称不能为空");
        if (maximumLength <= 0) {
            throw new IllegalArgumentException(checkedName + "最大长度必须为正数");
        }
        String checked = optionalText(value);
        if (checked != null && checked.length() > maximumLength) {
            throw new IllegalArgumentException(checkedName + "长度不能超过 " + maximumLength);
        }
        return checked;
    }
}
