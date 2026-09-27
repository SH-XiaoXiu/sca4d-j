package com.sca4d.core.api.runtime;

import org.jetbrains.annotations.Contract;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** 提供使用明确时钟和时区的通用时间换算。 */
public class TimeUtils {

    private TimeUtils() {
    }

    /**
     * 读取明确时钟的当前时间点。
     *
     * @param clock 明确时钟
     * @return 时钟当前时间点
     */
    public static Instant now(Clock clock) {
        return Objects.requireNonNull(clock, "时钟不能为空").instant();
    }

    /**
     * 把时间点转换为指定时区的本地日期时间。
     *
     * @param instant 时间点
     * @param zoneId 目标时区
     * @return 本地日期时间
     */
    @Contract(pure = true)
    public static LocalDateTime toLocalDateTime(
            Instant instant,
            ZoneId zoneId) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(instant, "时间点不能为空"),
                Objects.requireNonNull(zoneId, "时区不能为空"));
    }

    /**
     * 取得指定日期在指定时区中的开始时间点。
     *
     * @param date 本地日期
     * @param zoneId 目标时区
     * @return 当日零点对应的时间点
     */
    @Contract(pure = true)
    public static Instant startOfDay(LocalDate date, ZoneId zoneId) {
        return Objects.requireNonNull(date, "日期不能为空")
                .atStartOfDay(Objects.requireNonNull(zoneId, "时区不能为空"))
                .toInstant();
    }
}
