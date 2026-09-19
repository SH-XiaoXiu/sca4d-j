package com.sca4d.core.api.utils;

import org.jetbrains.annotations.Contract;

import java.time.Duration;
import java.util.Objects;

/** 校验时间长度为正数。 */
public final class DurationUtils {

    private DurationUtils() {
    }

    /**
     * 校验时间长度为正数。
     *
     * @param value 时间长度
     * @param name 异常中使用的字段名称
     * @return 原时间长度
     */
    @Contract(pure = true)
    public static Duration requirePositive(
            Duration value,
            String name) {
        Duration checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return checked;
    }
}
