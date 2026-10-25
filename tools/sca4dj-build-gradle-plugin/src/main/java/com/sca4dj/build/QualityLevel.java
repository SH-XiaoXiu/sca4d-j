package com.sca4dj.build;

import java.util.Locale;

/** 定义仓库构建允许采用的检查深度和失败阈值。 */
public enum QualityLevel {
    /** 只要求活跃项目能够编译和打包。 */
    SHIT,

    /** 运行单元测试并阻止模块、分层和持久化技术越界。 */
    AGILE,

    /** 运行完整检查，只由架构、正确性、安全和数据问题阻断构建。 */
    ELEGANT,

    /** 把全部警告和缺失的外部检查证据都视为构建失败。 */
    OBSESSIVE;

    /**
     * 解析 Gradle 属性中的质量级别。
     *
     * @param value 属性值；空值使用 {@link #ELEGANT}
     * @return 解析后的质量级别
     * @throws IllegalArgumentException 属性值不是已声明的级别
     */
    public static QualityLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return ELEGANT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "quality.level 只能是 SHIT、AGILE、ELEGANT 或 OBSESSIVE，当前值: " + value,
                    exception);
        }
    }

    /**
     * 判断当前级别是否至少执行指定级别的检查。
     *
     * @param required 规则要求的最低级别
     * @return 当前级别包含指定检查时返回 {@code true}
     */
    public boolean includes(QualityLevel required) {
        return ordinal() >= required.ordinal();
    }

    /**
     * 返回两个质量级别中要求更严格的一项。
     *
     * @param first 第一个级别
     * @param second 第二个级别
     * @return ordinal 较大的质量级别
     */
    public static QualityLevel stricter(QualityLevel first, QualityLevel second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
