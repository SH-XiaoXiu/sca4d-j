package com.sca4d.iam.cleanup;

/**
 * 在集群中领取并批量执行 Subject 关系清理任务。
 */
public interface SubjectCleanupBatchService {

    /**
     * 原子领取并处理下一批待清理任务。
     *
     * <p>每个任务独立完成或释放重试；单个任务失败不会阻止同批其他任务继续执行。
     *
     * @param batchSize 当前节点本轮最多领取的任务数
     */
    void cleanupNextBatch(int batchSize);
}
