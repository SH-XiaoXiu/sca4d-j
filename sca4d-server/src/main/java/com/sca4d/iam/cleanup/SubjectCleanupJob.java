package com.sca4d.iam.cleanup;

import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * 在集群中领取并执行 Subject 终态删除关系清理任务。
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class SubjectCleanupJob implements Job {

    private static final int BATCH_SIZE = 50;

    private final SubjectCleanupBatchService cleanupBatchService;

    /**
     * 定期领取未完成任务，并为每个任务保留独立的成功或重试状态。
     *
     * <p>领取 SQL 使用 {@code FOR UPDATE SKIP LOCKED}，多个应用节点不会同时处理同一个任务。失败任务恢复为
     * PENDING 并记录摘要，后续轮次可重复执行幂等清理。
     */
    public void cleanupDeletedIdentities() {
        cleanupBatchService.cleanupNextBatch(BATCH_SIZE);
    }

    /** @param context Quartz 单次触发上下文 */
    @Override
    public void execute(JobExecutionContext context) {
        cleanupDeletedIdentities();
    }
}
