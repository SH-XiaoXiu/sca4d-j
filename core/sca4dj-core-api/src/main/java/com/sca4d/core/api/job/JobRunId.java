package com.sca4d.core.api.job;

/** 标识当前应用运行时命名空间内一次已经持久化的 Job 运行。 */
public final class JobRunId extends com.sca4d.core.api.id.Id {

    /**
     * 创建一次持久化运行的标识。
     *
     * @param value 数据库生成的正整数运行标识
     * @throws IllegalArgumentException 标识不是正整数时抛出
     */
    public JobRunId(long value) {
        super(value);
    }
}
