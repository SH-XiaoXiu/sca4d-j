package com.sca4d.job.persistence;

import org.jetbrains.annotations.NotNull;

/** 从持久化广播头读取标识及触发时的目标快照。 */
public class JobDispatchRecord {

    private final @NotNull String targetsJson;

    private final long id;

    /**
     * 创建一次定时广播的持久化目标快照。
     *
     * @param id 广播标识
     * @param targetsJson 提交时保存的目标快照 JSON
     */
    public JobDispatchRecord(long id, @NotNull String targetsJson) {
        this.id = id;
        this.targetsJson = targetsJson;
    }

    /** @return 广播标识 */
    public long id() {
        return id;
    }

    /** @return 提交时保存的目标快照 JSON */
    public @NotNull String targetsJson() {
        return targetsJson;
    }
}
