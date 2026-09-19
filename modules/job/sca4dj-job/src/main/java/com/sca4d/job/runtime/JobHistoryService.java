package com.sca4d.job.runtime;

import com.sca4d.job.config.JobProperties;
import com.sca4d.job.persistence.JobRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 按配置保留窗口分批删除已经成功的 Job run 历史。 */
public final class JobHistoryService {

    private static final Logger log = LoggerFactory.getLogger(JobHistoryService.class);

    private final @NotNull JobRepository repository;

    private final @NotNull JobProperties properties;

    private final @NotNull Clock clock;

    /**
     * 创建只清理成功历史的 Job 历史服务。
     *
     * @param repository 当前运行时命名空间的 Job 持久化操作
     * @param properties 成功历史保留时间和单次删除上限
     * @param clock 计算保留窗口截止时间的时钟
     */
    public JobHistoryService(
            @NotNull JobRepository repository,
            @NotNull JobProperties properties,
            @NotNull Clock clock) {
        this.repository = Objects.requireNonNull(repository, "JobRepository 不能为空");
        this.properties = Objects.requireNonNull(properties, "JobProperties 不能为空");
        this.clock = Objects.requireNonNull(clock, "Job 历史时钟不能为空");
    }

    /**
     * 删除一批超过成功保留窗口的根运行。
     *
     * <p>只删除 {@code SUCCEEDED}；{@code FAILED}、{@code QUEUED} 和 {@code RUNNING} 不受影响。
     * Repository 在同一事务中清理节点、依赖、尝试和根运行。
     *
     * @return 本次删除的根运行数量
     */
    public int cleanupSucceededRuns() {
        Instant completedBefore = clock.instant().minus(properties.getSucceededRetention());
        int deleted = repository.deleteSucceededRunsBefore(
                completedBefore,
                properties.getHistoryCleanupBatchSize());
        if (deleted > 0) {
            log.info(
                    "Job 成功历史清理完成 | deletedRunCount={} completedBefore={}",
                    deleted,
                    completedBefore);
        }
        return deleted;
    }
}
