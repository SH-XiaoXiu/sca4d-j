package com.sca4d.schedule.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerMetaData;
import org.springframework.boot.actuate.health.Status;

/** 验证 Quartz 启动、暂停和故障状态映射到健康检查结果。 */
class SchedulerHealthIndicatorTest {

    @Test
    void shouldRequireReadableJdbcJobStore() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        SchedulerMetaData metadata = mock(SchedulerMetaData.class);
        QuartzBootstrapCoordinator coordinator = mock(QuartzBootstrapCoordinator.class);
        when(scheduler.isStarted()).thenReturn(true);
        when(scheduler.isShutdown()).thenReturn(false);
        when(scheduler.getMetaData()).thenReturn(metadata);
        when(metadata.isJobStoreSupportsPersistence()).thenReturn(true);
        when(coordinator.reconciliationSucceeded()).thenReturn(true);
        when(scheduler.checkExists(new JobKey("subject-cleanup", SystemJobQuartzSynchronizer.GROUP)))
                .thenReturn(false);

        var health = new SchedulerHealthIndicator(scheduler, coordinator).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("jobStoreReadable", false);
    }
}
