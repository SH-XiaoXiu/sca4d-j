package com.sca4d.core.api.runtime;

import org.jetbrains.annotations.Contract;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** 使用固定 {@link DateTimeFormatter} 格式化日期和时间。 */
public final class TimeFormats {

    /** 日期格式 {@code yyyy-MM-dd}。 */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 时间格式 {@code HH:mm:ss}。 */
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 日期时间格式 {@code yyyy-MM-dd HH:mm:ss}。 */
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeFormats() {
    }

    /**
     * 格式化日期。
     *
     * @param value 日期
     * @return {@code yyyy-MM-dd} 格式的日期文本
     */
    @Contract(pure = true)
    public static String formatDate(LocalDate value) {
        return DATE.format(Objects.requireNonNull(value, "日期不能为空"));
    }

    /**
     * 格式化时间。
     *
     * @param value 时间
     * @return {@code HH:mm:ss} 格式的时间文本
     */
    @Contract(pure = true)
    public static String formatTime(LocalTime value) {
        return TIME.format(Objects.requireNonNull(value, "时间不能为空"));
    }

    /**
     * 格式化日期时间。
     *
     * @param value 日期时间
     * @return {@code yyyy-MM-dd HH:mm:ss} 格式的日期时间文本
     */
    @Contract(pure = true)
    public static String formatDateTime(LocalDateTime value) {
        return DATE_TIME.format(Objects.requireNonNull(value, "日期时间不能为空"));
    }

    /**
     * 在明确时区中格式化时间点。
     *
     * @param value 时间点
     * @param zoneId 目标时区
     * @return {@code yyyy-MM-dd HH:mm:ss} 格式的日期时间文本
     */
    @Contract(pure = true)
    public static String formatInstant(Instant value, ZoneId zoneId) {
        return DATE_TIME.withZone(Objects.requireNonNull(zoneId, "时区不能为空"))
                .format(Objects.requireNonNull(value, "时间点不能为空"));
    }
}
