package com.sca4d.core.api.job;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 标识当前应用运行时命名空间内一次已经持久化的 Job 运行。 */
public final class JobRunId {

    private final long value;

    /**
     * 创建一次持久化运行的标识。
     *
     * @param value 数据库生成的正整数运行标识
     * @throws IllegalArgumentException 标识不是正整数时抛出
     */
    public JobRunId(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("JobRunId 必须为正整数");
        }
        this.value = value;
    }

    /**
     * 返回数据库运行主键。
     *
     * @return 数据库生成的正整数运行标识
     */
    @Contract(pure = true)
    public long value() {
        return value;
    }

    @Override
    @Contract(pure = true)
    public boolean equals(Object other) {
        return this == other || other instanceof JobRunId id && value == id.value;
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return Long.toString(value);
    }
}
