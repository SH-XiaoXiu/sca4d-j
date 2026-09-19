package com.sca4d.iot.persistence.mapper;

import com.sca4d.core.api.persistence.Patch;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleStatus;
import com.sca4d.iot.persistence.EncodedDeviceSchedule;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** 读写设备定时规则、下一次触发时间和最新执行结果。 */
public interface DeviceScheduleMapper {

    /** 新增设备定时规则。 */
    int insertSchedule(
            @Param("scheduleId") long scheduleId,
            @Param("deviceId") long deviceId,
            @Param("commandCode") @NotNull String commandCode,
            @Param("commandInputsJson") @NotNull String commandInputsJson,
            @Param("triggerType") @NotNull String triggerType,
            @Param("executeAt") @Nullable Instant executeAt,
            @Param("cronExpression") @Nullable String cronExpression,
            @Param("zoneId") @Nullable String zoneId,
            @Param("validForMillis") long validForMillis,
            @Param("nextFireAt") @NotNull Instant nextFireAt);

    /** 按可选标识、Device 或到期时间读取规则；到期读取按触发时间排序，其余按标识倒序。 */
    @NotNull
    List<EncodedDeviceSchedule> selectSchedules(
            @Param("scheduleId") @Nullable Long scheduleId,
            @Param("deviceId") @Nullable Long deviceId,
            @Param("dueAt") @Nullable Instant dueAt,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 按乐观锁版本完整替换规则并恢复 ACTIVE。 */
    int replaceSchedule(
            @Param("scheduleId") long scheduleId,
            @Param("expectedVersion") long expectedVersion,
            @Param("commandCode") @NotNull String commandCode,
            @Param("commandInputsJson") @NotNull String commandInputsJson,
            @Param("triggerType") @NotNull String triggerType,
            @Param("executeAt") @NotNull Patch<Instant> executeAt,
            @Param("cronExpression") @NotNull Patch<String> cronExpression,
            @Param("zoneId") @NotNull Patch<String> zoneId,
            @Param("validForMillis") long validForMillis,
            @Param("status") @NotNull DeviceScheduleStatus status,
            @Param("nextFireAt") @NotNull Instant nextFireAt);

    /** 按版本及可选当前状态更新调度进展；取消后的规则不可修改。 */
    int updateProgress(
            @Param("scheduleId") long scheduleId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedStatus") @Nullable DeviceScheduleStatus expectedStatus,
            @Param("status") @NotNull Patch<DeviceScheduleStatus> status,
            @Param("nextFireAt") @NotNull Patch<Instant> nextFireAt,
            @Param("plannedAt") @NotNull Patch<Instant> plannedAt,
            @Param("triggeredAt") @NotNull Patch<Instant> triggeredAt,
            @Param("executionStatus") @NotNull Patch<DeviceScheduleExecutionStatus> executionStatus,
            @Param("messageId") @NotNull Patch<Long> messageId,
            @Param("failureCode") @NotNull Patch<String> failureCode);

    /** 统计 Device 的定时规则。 */
    long countDeviceSchedules(@Param("deviceId") long deviceId);

}
