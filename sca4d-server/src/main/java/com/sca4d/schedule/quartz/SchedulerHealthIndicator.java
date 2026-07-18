package com.sca4d.schedule.quartz;

import org.jetbrains.annotations.NotNull;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Quartz 启动、JDBC JobStore 和最近启动对账的 readiness 健康检查。 */
@Component("schedulerHealthIndicator")
public class SchedulerHealthIndicator implements HealthIndicator {

    private final Scheduler scheduler;
    private final QuartzBootstrapCoordinator coordinator;

    public SchedulerHealthIndicator(
            @NotNull Scheduler scheduler, @NotNull QuartzBootstrapCoordinator coordinator) {
        this.scheduler = scheduler;
        this.coordinator = coordinator;
    }

    @Override
    public @NotNull Health health() {
        try {
            boolean started = scheduler.isStarted();
            boolean shutdown = scheduler.isShutdown();
            var metadata = scheduler.getMetaData();
            boolean persistent = metadata.isJobStoreSupportsPersistence();
            boolean jobStoreReadable = scheduler.checkExists(new JobKey("subject-cleanup", SystemJobQuartzSynchronizer.GROUP));
            if (!started || shutdown || !persistent || !jobStoreReadable || !coordinator.reconciliationSucceeded()) {
                return Health.down()
                        .withDetail("started", started)
                        .withDetail("shutdown", shutdown)
                        .withDetail("persistentJobStore", persistent)
                        .withDetail("jobStoreReadable", jobStoreReadable)
                        .withDetail("reconciled", coordinator.reconciliationSucceeded())
                        .withDetail("failureType", coordinator.failureType() == null ? "none" : coordinator.failureType())
                        .build();
            }
            return Health.up()
                    .withDetail("schedulerName", metadata.getSchedulerName())
                    .withDetail("instanceId", metadata.getSchedulerInstanceId())
                    .build();
        } catch (SchedulerException exception) {
            return Health.down(exception).build();
        }
    }
}
