package com.sca4d.core.spring.id;

import cn.hutool.core.lang.Snowflake;
import com.sca4d.core.api.id.IdGenerator;

/**
 * 使用 Hutool 雪花算法生成全局正整数标识（Snowflake ID Generator）。
 *
 * <p>外部使用一个 10 位节点编号。低 5 位映射为 Hutool worker ID，高 5 位映射为 data center ID，因此仍支持 1024
 * 个节点组合。部署系统必须保证同一集群内节点编号唯一。
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    /** 节点编号允许的最大值。 */
    public static final long MAX_NODE_ID = 1023L;

    private final long nodeId;

    private final Snowflake snowflake;

    /**
     * 创建雪花 ID 生成器。
     *
     * @param nodeId 当前进程使用的唯一节点编号，范围为 0 至 1023
     */
    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("雪花 ID 节点编号必须位于 0 至 1023 之间");
        }
        this.nodeId = nodeId;
        long workerId = nodeId & Snowflake.MAX_WORKER_ID;
        long dataCenterId = (nodeId >>> 5) & Snowflake.MAX_DATA_CENTER_ID;
        this.snowflake = new Snowflake(workerId, dataCenterId);
    }

    /**
     * 生成下一个全局正整数标识。
     *
     * @return 下一个雪花 ID
     */
    @Override
    public long nextId() {
        return snowflake.nextId();
    }

    /**
     * 返回当前生成器的节点编号。
     *
     * @return 节点编号
     */
    public long nodeId() {
        return nodeId;
    }
}
