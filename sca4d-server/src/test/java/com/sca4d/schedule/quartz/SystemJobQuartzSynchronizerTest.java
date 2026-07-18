package com.sca4d.schedule.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.config.properties.Sca4djIamCleanupProperties;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;

import java.time.Duration;
import java.util.Set;

/** 验证系统 Job 清单与消息、文件能力开关保持一致。 */
class SystemJobQuartzSynchronizerTest {

    @Test
    void shouldOnlyExposeJobsForEnabledCapabilities() {
        Sca4djMessagingProperties messaging = new Sca4djMessagingProperties();
        messaging.getOutbox().setEnabled(false);
        Sca4djFileProperties file = new Sca4djFileProperties();
        file.setEnabled(false);
        Sca4djIamCleanupProperties iam = new Sca4djIamCleanupProperties();
        iam.setCleanupInterval(Duration.ofMinutes(1));

        SystemJobQuartzSynchronizer synchronizer = new SystemJobQuartzSynchronizer(
                mock(Scheduler.class), iam, messaging, file);

        assertThat(synchronizer.definitions())
                .extracting(SystemJobDefinition::getJobName)
                .containsExactly("subject-cleanup", "tenant-cleanup");
    }

    @Test
    void shouldDeleteJobsWhoseCapabilitiesWereDisabled() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        JobKey outboxJob = new JobKey("message-outbox-dispatch", SystemJobQuartzSynchronizer.GROUP);
        JobKey fileJob = new JobKey("file-cleanup", SystemJobQuartzSynchronizer.GROUP);
        when(scheduler.getJobKeys(GroupMatcher.jobGroupEquals(SystemJobQuartzSynchronizer.GROUP)))
                .thenReturn(Set.of(outboxJob, fileJob));
        Sca4djMessagingProperties messaging = new Sca4djMessagingProperties();
        messaging.setEnabled(false);
        Sca4djFileProperties file = new Sca4djFileProperties();
        file.setEnabled(false);
        Sca4djIamCleanupProperties iam = new Sca4djIamCleanupProperties();
        iam.setCleanupInterval(Duration.ofMinutes(1));

        SystemJobQuartzSynchronizer synchronizer = new SystemJobQuartzSynchronizer(
                scheduler, iam, messaging, file);
        synchronizer.reconcile();

        verify(scheduler).deleteJob(outboxJob);
        verify(scheduler).deleteJob(fileJob);
    }
}
