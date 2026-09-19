package com.sca4d.iot.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证设备定时任务对 PostgreSQL 到期扫描结果执行确定的批次和失败处理。 */
class DeviceScheduleTaskTest {

    private final DefaultDeviceScheduleService schedules = mock(DefaultDeviceScheduleService.class);

    @Test
    @DisplayName("连续处理到期规则直到 PostgreSQL 返回空")
    void handlesDueSchedulesUntilEmpty() {
        when(schedules.triggerNextDueSchedule()).thenReturn(true, true, false);

        new DeviceScheduleTask(schedules, 100).execute();

        verify(schedules, org.mockito.Mockito.times(3)).triggerNextDueSchedule();
    }

    @Test
    @DisplayName("业务失败在独立调用中暂停同版本规则")
    void pausesBusinessFailure() {
        DeviceScheduleTriggerFailure failure = new DeviceScheduleTriggerFailure(
                10,
                3,
                Instant.parse("2026-08-05T10:00:00Z"),
                "INVALID_INPUT",
                new IllegalArgumentException("invalid command"));
        when(schedules.triggerNextDueSchedule()).thenThrow(failure).thenReturn(false);

        new DeviceScheduleTask(schedules, 100).execute();

        verify(schedules).pauseFailedSchedule(failure);
    }
}
