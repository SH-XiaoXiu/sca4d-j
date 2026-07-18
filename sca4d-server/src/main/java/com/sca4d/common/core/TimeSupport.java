package com.sca4d.common.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 时间工具。
 *
 * <p>时间计算优先使用 Hutool 和 JDK API，数据库时间字段共用映射为 {@link OffsetDateTime}。
 */
public final class TimeSupport {

    /** 默认业务时区。 */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of(Constants.DEFAULT_ZONE_ID);

    /** 存储时区。 */
    public static final ZoneOffset STORAGE_ZONE = ZoneOffset.UTC;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeSupport() {
    }

    /**
     * 当前存储时间。
     *
     * @return 当前 OffsetDateTime
     */
    public static @NotNull OffsetDateTime now() {
        return OffsetDateTime.now(STORAGE_ZONE);
    }

    /**
     * 转换为存储时间。
     *
     * @param instant 时间点
     * @return UTC OffsetDateTime
     */
    public static @NotNull OffsetDateTime from(@NotNull Instant instant) {
        return OffsetDateTime.ofInstant(instant, STORAGE_ZONE);
    }

    /**
     * Date 转 OffsetDateTime。
     *
     * @param date Date
     * @return UTC OffsetDateTime；date 为 null 时返回 null
     */
    public static @Nullable OffsetDateTime from(@Nullable Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), STORAGE_ZONE);
    }

    /**
     * OffsetDateTime 转 Date。
     *
     * @param dateTime OffsetDateTime
     * @return Date；dateTime 为 null 时返回 null
     */
    public static @Nullable Date toDate(@Nullable OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.toInstant());
    }

    /**
     * 转换为 UTC 时间。
     *
     * @param dateTime OffsetDateTime
     * @return UTC OffsetDateTime
     */
    public static @NotNull OffsetDateTime toUtc(@NotNull OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }

    /**
     * 转换为默认业务时区时间。
     *
     * @param instant 时间点
     * @return 默认业务时区 OffsetDateTime
     */
    public static @NotNull OffsetDateTime toDefaultZone(@NotNull Instant instant) {
        return OffsetDateTime.ofInstant(instant, DEFAULT_ZONE);
    }

    /**
     * 转换为默认业务时区时间。
     *
     * @param dateTime OffsetDateTime
     * @return 默认业务时区 OffsetDateTime
     */
    public static @NotNull OffsetDateTime toDefaultZone(@NotNull OffsetDateTime dateTime) {
        return toZone(dateTime, DEFAULT_ZONE);
    }

    /**
     * 转换为指定时区时间。
     *
     * @param dateTime OffsetDateTime
     * @param zoneId 目标时区
     * @return 指定时区 OffsetDateTime
     */
    public static @NotNull OffsetDateTime toZone(@NotNull OffsetDateTime dateTime, @NotNull ZoneId zoneId) {
        return dateTime.atZoneSameInstant(zoneId).toOffsetDateTime();
    }

    /**
     * 格式化为标准日期时间文本。
     *
     * @param dateTime OffsetDateTime
     * @return 默认业务时区的标准日期时间文本；dateTime 为 null 时返回空字符串
     */
    public static @NotNull String format(@Nullable OffsetDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(toDefaultZone(dateTime));
    }
}
