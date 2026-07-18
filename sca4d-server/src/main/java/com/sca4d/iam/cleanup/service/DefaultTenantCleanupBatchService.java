package com.sca4d.iam.cleanup.service;

import com.sca4d.iam.cleanup.TenantCleanupBatchService;
import com.sca4d.iam.cleanup.TenantCleanupTask;
import com.sca4d.iam.cleanup.TenantCleanupTaskService;
import com.sca4d.iam.repository.IamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 默认 Tenant 清理任务领取、执行和失败释放实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTenantCleanupBatchService implements TenantCleanupBatchService {

    private final IamRepository iamRepository;
    private final TenantCleanupTaskService cleanupTaskService;

    @Override
    public void cleanupNextBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Tenant 清理批次大小必须大于 0");
        }
        long startedAt = System.nanoTime();
        List<TenantCleanupTask> tasks = iamRepository.claimTenantCleanupTasks(batchSize);
        if (tasks.isEmpty()) {
            log.debug("Tenant 关系清理没有待处理任务 | batchSize={}", batchSize);
            return;
        }
        log.info("Tenant 关系清理批次开始 | claimedCount={} batchSize={}", tasks.size(), batchSize);
        int successCount = 0;
        int retryCount = 0;
        // 每轮固定 1 条数据库领取 SQL；最多 batchSize 个任务各进入 1 个独立事务，失败时再执行 1 次释放更新。
        for (TenantCleanupTask task : tasks) {
            try {
                cleanupTaskService.cleanup(task);
                successCount++;
                log.debug("Tenant 关系清理任务完成 | taskId={} tenantId={}", task.getTaskId(), task.getTenantId());
            } catch (RuntimeException exception) {
                iamRepository.releaseTenantCleanup(task.getTaskId(), exception.getClass().getName());
                retryCount++;
                log.warn(
                        "Tenant 关系清理失败，任务等待下轮重试 | taskId={} tenantId={}",
                        task.getTaskId(),
                        task.getTenantId(),
                        exception);
            }
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "Tenant 关系清理批次完成 | successCount={} retryCount={} durationMs={}",
                successCount,
                retryCount,
                durationMs);
    }
}
