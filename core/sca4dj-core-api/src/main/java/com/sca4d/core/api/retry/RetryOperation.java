package com.sca4d.core.api.retry;

import org.jetbrains.annotations.Nullable;

/** 表示一次同步尝试，操作自身负责事务、幂等和副作用范围。 */
@FunctionalInterface
public interface RetryOperation<T, E extends Exception> {
    /**
     * @return 本次结果，允许为空
     * @throws E 本次操作失败
     */
    @Nullable
    T run() throws E;
}
