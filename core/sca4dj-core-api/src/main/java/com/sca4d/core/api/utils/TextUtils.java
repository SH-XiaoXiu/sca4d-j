package com.sca4d.core.api.utils;

import org.jetbrains.annotations.Contract;

import java.util.Objects;

/** 校验非空白文本并清除首尾空白。 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * 校验并返回去除首尾空白的文本。
     *
     * @param value 原始文本
     * @param name 异常中使用的字段名称
     * @return 非空白文本
     */
    @Contract(pure = true)
    public static String requireText(String value, String name) {
        String checkedName = Objects.requireNonNull(name, "字段名称不能为空");
        String normalized = Objects.requireNonNull(value, checkedName + "不能为空").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(checkedName + "不能为空白");
        }
        return normalized;
    }
}
