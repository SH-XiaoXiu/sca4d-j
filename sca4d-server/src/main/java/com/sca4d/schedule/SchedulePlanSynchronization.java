package com.sca4d.schedule;

import com.sca4d.common.core.TimeSupport;
import com.sca4d.schedule.repository.ScheduleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 数据库事务提交后同步 Quartz Job 和 Trigger，并记录同步失败信息。 */
@Component
public class SchedulePlanSynchronization {

    private static final Logger log = LoggerFactory.getLogger(SchedulePlanSynchronization.class);

    private final SchedulePlanSynchronizer synchronizer;
    private final ScheduleRepository repository;
    private final MeterRegistry meterRegistry;

    public SchedulePlanSynchronization(
            @NotNull SchedulePlanSynchronizer synchronizer,
            @NotNull ScheduleRepository repository,
            @NotNull MeterRegistry meterRegistry) {
        this.synchronizer = synchronizer;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    /** @param scheduleId 当前事务已经创建或更新的 Schedule 主键 */
    public void synchronizeAfterCommit(@NotNull Long scheduleId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    synchronizer.synchronize(scheduleId);
                    repository.updateSynchronizationError(scheduleId, null, TimeSupport.now());
                    log.info("调度计划同步完成 | scheduleId={}", scheduleId);
                } catch (RuntimeException exception) {
                    String error = exception.getClass().getName();
                    repository.updateSynchronizationError(scheduleId, error, TimeSupport.now());
                    meterRegistry.counter("sca4dj.schedule.synchronization.failures", "operation", "synchronize")
                            .increment();
                    log.error("调度计划同步失败，保留数据库记录等待下次同步 | scheduleId={}", scheduleId, exception);
                }
            }
        });
    }

    /** @param scheduleId 当前事务已经置为取消终态的 Schedule 主键 */
    public void removeAfterCommit(@NotNull Long scheduleId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    synchronizer.remove(scheduleId);
                    repository.updateSynchronizationError(scheduleId, null, TimeSupport.now());
                    log.info("调度计划移除完成 | scheduleId={}", scheduleId);
                } catch (RuntimeException exception) {
                    String error = exception.getClass().getName();
                    repository.updateSynchronizationError(scheduleId, error, TimeSupport.now());
                    meterRegistry.counter("sca4dj.schedule.synchronization.failures", "operation", "remove")
                            .increment();
                    log.error("调度计划移除失败，保留已取消记录等待下次同步 | scheduleId={}", scheduleId, exception);
                }
            }
        });
    }

    /**
     * @param scheduleId 当前事务已经确认允许人工执行的 Schedule 主键
     * @param executionId 当前事务已经创建的 PENDING 执行主键
     */
    public void startManualAfterCommit(@NotNull Long scheduleId, @NotNull Long executionId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    synchronizer.startManualExecution(scheduleId, executionId);
                    log.info("人工调度已提交执行 | scheduleId={} executionId={}", scheduleId, executionId);
                } catch (RuntimeException exception) {
                    repository.updateSynchronizationError(scheduleId, exception.getClass().getName(), TimeSupport.now());
                    meterRegistry.counter("sca4dj.schedule.synchronization.failures", "operation", "manual")
                            .increment();
                    log.error(
                            "人工调度提交失败，保留待执行记录等待恢复 | scheduleId={} executionId={}",
                            scheduleId, executionId, exception);
                }
            }
        });
    }
}
