package com.sca4d.common.id;

import cn.hutool.core.lang.Snowflake;

/**
 * Snowflake 集群 ID 生成器。
 */
public class ClusterIdGenerator implements IdGenerator {

    /** Hutool Snowflake workerId 最大值。 */
    public static final long MAX_WORKER_ID = Snowflake.MAX_WORKER_ID;

    /** Hutool Snowflake dataCenterId 最大值。 */
    public static final long MAX_DATA_CENTER_ID = Snowflake.MAX_DATA_CENTER_ID;

    private final long workerId;
    private final long dataCenterId;
    private final Snowflake snowflake;

    /**
     * 创建 Snowflake 集群 ID 生成器。
     *
     * @param workerId 工作节点 ID
     * @param dataCenterId 数据中心 ID
     */
    public ClusterIdGenerator(long workerId, long dataCenterId) {
        validate(workerId, MAX_WORKER_ID, "workerId");
        validate(dataCenterId, MAX_DATA_CENTER_ID, "dataCenterId");
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
        this.snowflake = new Snowflake(workerId, dataCenterId);
    }

    /**
     * 生成当前 worker 和 data center 组合下的下一个集群唯一主键。
     *
     * @return Hutool Snowflake 生成的正整数主键
     */
    @Override
    public long nextId() {
        return snowflake.nextId();
    }

    /** @return 当前实例配置的 Snowflake workerId */
    public long getWorkerId() {
        return workerId;
    }

    /** @return 当前实例配置的 Snowflake dataCenterId */
    public long getDataCenterId() {
        return dataCenterId;
    }

    /**
     * 校验 Snowflake 节点维度处于 Hutool 实现支持的闭区间。
     *
     * @param value 待校验节点维度值
     * @param maxValue 当前维度允许的最大值
     * @param fieldName 失败消息使用的配置字段名
     * @throws IllegalArgumentException 值小于零或超过最大值时抛出
     */
    private static void validate(long value, long maxValue, String fieldName) {
        if (value < 0 || value > maxValue) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and " + maxValue);
        }
    }
}
