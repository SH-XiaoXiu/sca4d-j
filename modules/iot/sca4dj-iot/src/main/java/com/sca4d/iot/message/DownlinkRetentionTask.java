package com.sca4d.iot.message;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.iot.api.IotCapabilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 分批删除超过保留期的最终状态下行消息，使 PostgreSQL 只保留可靠投递所需状态和近期查询数据。 */
public class DownlinkRetentionTask {

    private static final Logger LOG = LoggerFactory.getLogger(DownlinkRetentionTask.class);

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull Clock clock;

    private final @NotNull Duration retention;

    private final int batchSize;

    /**
     * 创建删除一批终态下行消息的集群任务。
     *
     * @param downlinks 下行业务状态 Repository
     * @param clock 计算保留期截止时间的时钟
     * @param retention 最终状态记录保留时间
     * @param batchSize 每次执行最多删除的记录数，范围为 1 到 10000
     * @throws NullPointerException Repository、时钟或保留期为空时抛出
     * @throws IllegalArgumentException 保留期或批量无效时抛出
     */
    public DownlinkRetentionTask(
            @NotNull DownlinkRepository downlinks,
            @NotNull Clock clock,
            @NotNull Duration retention,
            int batchSize) {
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "下行清理时钟不能为空");
        Duration checkedRetention = Objects.requireNonNull(retention, "下行消息保留期不能为空");
        if (checkedRetention.isZero() || checkedRetention.isNegative()) {
            throw new IllegalArgumentException("下行消息保留期必须为正数");
        }
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("下行消息清理批量必须在 1 到 10000 之间");
        }
        this.retention = checkedRetention;
        this.batchSize = batchSize;
    }

    /** 删除一批早于保留截止时间且已经进入最终状态的下行消息。 */
    @Transactional
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public void execute() {
        Instant completedBefore = clock.instant().minus(retention);
        int deleted = downlinks.deleteCompletedBefore(completedBefore, batchSize);
        if (deleted > 0) {
            LOG.debug(
                    "终态 Device 下行消息已清理 | deleted={} completedBefore={}",
                    deleted,
                    completedBefore);
        }
    }
}
