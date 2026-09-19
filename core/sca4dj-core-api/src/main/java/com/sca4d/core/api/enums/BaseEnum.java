package com.sca4d.core.api.enums;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;

import java.util.Locale;

/** 表示具有外部值和中文说明的业务枚举。 */
public interface BaseEnum {

    /**
     * 返回用于传输和持久化的值。
     *
     * @return 用于传输和持久化的值
     */
    String value();

    /**
     * 返回供代码阅读、日志和调试使用的中文说明。
     *
     * @return 供代码阅读、日志和调试使用的中文说明
     */
    String description();

    /**
     * 将枚举外部值转换为大写并清除首尾空白。
     *
     * @param value 原始外部值
     * @return 大写值
     */
    @Contract(pure = true)
    static String normalize(String value) {
        return TextUtils.requireText(value, "枚举值").toUpperCase(Locale.ROOT);
    }

    /**
     * 按外部值查找枚举常量。
     *
     * @param enumType 业务枚举类型
     * @param value 外部值
     * @param <E> 枚举类型
     * @return 匹配常量
     */
    @Contract(pure = true)
    static <E extends Enum<E> & BaseEnum> E fromValue(
            Class<E> enumType,
            String value) {
        String normalized = normalize(value);
        if (enumType == null) {
            throw new NullPointerException("枚举类型不能为空");
        }
        for (E item : enumType.getEnumConstants()) {
            if (normalize(item.value()).equals(normalized)) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知枚举值: " + enumType.getSimpleName() + "." + value);
    }
}
