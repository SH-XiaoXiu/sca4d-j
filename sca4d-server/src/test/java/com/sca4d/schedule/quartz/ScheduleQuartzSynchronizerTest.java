package com.sca4d.schedule.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.TriggerSource;
import com.sca4d.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;

/** 验证 Schedule 定义与 Quartz Job、Trigger 的创建、更新和删除同步。 */
class ScheduleQuartzSynchronizerTest {

    private Scheduler scheduler;

    @BeforeEach
    void createScheduler() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "schedule-synchronizer-test");
        properties.setProperty("org.quartz.scheduler.instanceId", "NON_CLUSTERED");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        scheduler = new StdSchedulerFactory(properties).getScheduler();
    }

    @AfterEach
    void closeScheduler() throws Exception {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown(false);
        }
    }

    @Test
    void shouldProjectOnlyScheduleIdAndRecoverPendingManualExecution() throws Exception {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(41L)
                .setScheduleType(ScheduleType.ONCE)
                .setExecuteAt(OffsetDateTime.now().plusMinutes(5))
                .setStatus(ScheduleStatus.ACTIVE)
                .setDefinitionFingerprint("fingerprint-41")
                .setDeleted(0);
        ScheduleExecutionEntity manual = new ScheduleExecutionEntity()
                .setExecutionId(91L)
                .setScheduleId(41L)
                .setTriggerSource(TriggerSource.MANUAL)
                .setStatus(ExecutionStatus.PENDING);
        when(repository.findForReconciliation()).thenReturn(List.of(schedule));
        when(repository.findPendingManualExecutions()).thenReturn(List.of(manual));
        ScheduleQuartzSynchronizer synchronizer = new ScheduleQuartzSynchronizer(scheduler, repository);

        synchronizer.reconcileAll();
        synchronizer.reconcileAll();

        JobKey jobKey = new JobKey("schedule-41", "business");
        assertThat(scheduler.getJobDetail(jobKey).getJobDataMap())
                .containsOnlyKeys("scheduleId")
                .containsEntry("scheduleId", 41L);
        Trigger planned = scheduler.getTrigger(new TriggerKey("schedule-41-trigger", "business"));
        assertThat(planned.getDescription()).isEqualTo("fingerprint-41");
        Trigger manualTrigger = scheduler.getTrigger(new TriggerKey("schedule-41-manual-91", "business"));
        assertThat(manualTrigger.getJobKey()).isEqualTo(jobKey);
        assertThat(manualTrigger.getJobDataMap()).containsOnlyKeys("executionId").containsEntry("executionId", 91L);
    }

    @Test
    void shouldRepairTriggerFingerprintAndRemoveTerminalSchedule() throws Exception {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(42L)
                .setScheduleType(ScheduleType.CRON)
                .setCronExpression("0 0/5 * * * ?")
                .setTimeZone("Asia/Shanghai")
                .setStatus(ScheduleStatus.ACTIVE)
                .setDefinitionFingerprint("first")
                .setDeleted(0);
        when(repository.findForReconciliation()).thenReturn(List.of(schedule));
        when(repository.findPendingManualExecutions()).thenReturn(List.of());
        ScheduleQuartzSynchronizer synchronizer = new ScheduleQuartzSynchronizer(scheduler, repository);

        synchronizer.reconcileAll();
        schedule.setDefinitionFingerprint("second");
        synchronizer.reconcileAll();

        TriggerKey triggerKey = new TriggerKey("schedule-42-trigger", "business");
        assertThat(scheduler.getTrigger(triggerKey).getDescription()).isEqualTo("second");
        schedule.setStatus(ScheduleStatus.COMPLETED);
        synchronizer.reconcileAll();
        assertThat(scheduler.checkExists(new JobKey("schedule-42", "business"))).isFalse();
    }

    @Test
    void shouldRejectConflictingBusinessJobData() throws Exception {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(43L)
                .setScheduleType(ScheduleType.CRON)
                .setCronExpression("0 0/5 * * * ?")
                .setTimeZone("UTC")
                .setStatus(ScheduleStatus.ACTIVE)
                .setDefinitionFingerprint("current")
                .setDeleted(0);
        scheduler.addJob(JobBuilder.newJob(BusinessScheduleJob.class)
                .withIdentity("schedule-43", "business")
                .storeDurably()
                .requestRecovery(true)
                .usingJobData("scheduleId", 99L)
                .build(), false);
        when(repository.findForReconciliation()).thenReturn(List.of(schedule));
        when(repository.findPendingManualExecutions()).thenReturn(List.of());
        ScheduleQuartzSynchronizer synchronizer = new ScheduleQuartzSynchronizer(scheduler, repository);

        assertThatThrownBy(synchronizer::reconcileAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schedule Quartz 全量对账失败");
    }
}
