package com.sca4d.core.api.runtime;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;

import java.util.Locale;

/** 表示应用当前运行阶段（Environment）。 */
public enum Environment {
    /** 本机开发环境。 */
    LOCAL,
    /** 共享开发环境。 */
    DEV,
    /** 自动化测试环境。 */
    TEST,
    /** 上线前验证环境。 */
    STAGING,
    /** 正式生产环境。 */
    PROD;

    /**
     * 解析环境名称。
     *
     * @param value 不区分大小写的环境名称
     * @return 对应运行环境
     * @throws IllegalArgumentException 名称为空白或未知时抛出
     */
    @Contract(pure = true)
    public static Environment parse(String value) {
        String normalized = TextUtils.requireText(value, "环境名称");
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知运行环境: " + value, exception);
        }
    }

    /**
     * 返回当前环境是否为正式生产环境。
     *
     * @return 当前环境是否为正式生产环境
     */
    @Contract(pure = true)
    public boolean isProduction() {
        return this == PROD;
    }
}
