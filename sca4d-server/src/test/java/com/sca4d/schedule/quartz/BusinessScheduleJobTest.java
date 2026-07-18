package com.sca4d.schedule.quartz;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.schedule.ScheduleExecutionService;
import com.sca4d.schedule.TriggerSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

import java.time.OffsetDateTime;

/** 验证 Quartz Job 把 Schedule 主键、指纹和触发时间交给执行服务。 */
class BusinessScheduleJobTest {

    @Test
    void shouldReuseOriginalScheduledFireTimeDuringRecovery() throws Exception {
        ScheduleExecutionService executionService = mock(ScheduleExecutionService.class);
        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class);
        Scheduler scheduler = mock(Scheduler.class);
        JobDataMap jobData = new JobDataMap();
        jobData.put("scheduleId", 41L);
        JobDataMap recoveryData = new JobDataMap();
        recoveryData.put(
                Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS,
                "1784023200000");
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(jobData);
        when(context.getMergedJobDataMap()).thenReturn(recoveryData);
        when(context.isRecovering()).thenReturn(true);
        when(context.getScheduler()).thenReturn(scheduler);
        when(scheduler.getSchedulerInstanceId()).thenReturn("node-recovery");
        BusinessScheduleJob job = new BusinessScheduleJob();
        job.setExecutionService(executionService);

        job.execute(context);

        verify(executionService).run(
                eq(41L),
                eq(null),
                eq(null),
                eq(TriggerSource.RECOVERY),
                eq(OffsetDateTime.parse("2026-07-14T10:00:00Z")),
                ArgumentMatchers.any(),
                eq("node-recovery"));
    }
}
