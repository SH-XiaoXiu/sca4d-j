package com.sca4d.core.api.job;

import com.sca4d.core.api.id.Id;

/** 标识当前 App 隔离名称内一次已经持久化的 Job 运行。 */
public final class JobRunId extends Id {

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
