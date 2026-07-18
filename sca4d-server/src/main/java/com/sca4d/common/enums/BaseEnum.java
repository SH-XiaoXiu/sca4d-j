package com.sca4d.common.enums;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 字符串值枚举规范。
 *
 * <p>Java 代码使用枚举，HTTP 参数和数据库字段使用固定字符串值。默认值使用大写字符串。
 */
public interface BaseEnum {

    /**
     * 获取固定字符串值。
     *
     * @return 写入数据库、日志或 HTTP 响应时使用的固定值
     */
    default @NotNull String getValue() {
        return value();
    }

    /**
     * 枚举固定值。
     *
     * @return 写入数据库、日志或 HTTP 响应时使用的固定值
     */
    @NotNull
    String value();

    /**
     * 枚举说明。
     *
     * <p>该说明只用于代码阅读、日志和调试，不参与业务操作，也不作为前端展示标签。
     * 前端标签和国际化文案应由字典或 i18n 体系提供。
     *
     * @return 枚举说明
     */
    @NotNull
    String description();

    /**
     * 把 HTTP 参数或数据库字段中的字符串转换为大写值。
     *
     * @param value 原始字符串 或空白
     * @return 标准大写值
     * @throws IllegalArgumentException value 为空时抛出
     */
    static @NotNull String normalize(@NotNull String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("枚举值不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 按 value 忽略大小写查找枚举。
     *
     * @param enumType 枚举类型
     * @param value 枚举固定值 或空白
     * @param <E> 枚举类型
     * @return 匹配的枚举
     * @throws IllegalArgumentException 找不到匹配枚举时抛出
     */
    static <E extends Enum<E> & BaseEnum> @NotNull E fromValue(@NotNull Class<E> enumType, @NotNull String value) {
        String normalized = normalize(value);
        // 遍历枚举常量，按固定 value 做大小写无关匹配。
        for (E item : enumType.getEnumConstants()) {
            if (item.value().equalsIgnoreCase(normalized)) {
                return item;
            }
        }
        // 找不到时抛出明确异常，调用方可以转换为参数错误。
        throw new IllegalArgumentException("未知枚举值: " + enumType.getSimpleName() + "." + value);
    }
}
