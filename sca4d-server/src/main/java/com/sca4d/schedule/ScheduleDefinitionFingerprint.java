package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/** 根据 Schedule 完整执行定义生成各节点一致的 SHA-256 指纹。 */
public final class ScheduleDefinitionFingerprint {

    private ScheduleDefinitionFingerprint() {
    }

    /**
     * 计算数据库业务数据与 Quartz 投影之间使用的固定指纹。
     *
     * @param scheduleType 一次或 Cron 计划类型
     * @param executeAt ONCE 的执行时间
     * @param cronExpression CRON 的 Quartz 表达式
     * @param timeZone CRON 的 IANA 时区
     * @param status 当前 ACTIVE 或 PAUSED 状态
     * @param taskType 已注册开放任务类型
     * @param payloadVersion 持久化任务参数版本
     * @param payloadJson 转换任务参数 JSON
     * @return 固定小写十六进制 SHA-256
     */
    public static @NotNull String calculate(
            @NotNull ScheduleType scheduleType,
            @Nullable OffsetDateTime executeAt,
            @Nullable String cronExpression,
            @Nullable String timeZone,
            @NotNull ScheduleStatus status,
            @NotNull String taskType,
            int payloadVersion,
            @NotNull String payloadJson) {
        String source = scheduleType.value() + "|" + executeAt + "|" + cronExpression + "|" + timeZone + "|"
                + status.value() + "|" + taskType + "|" + payloadVersion + "|" + payloadJson;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256", exception);
        }
    }
}
