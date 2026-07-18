package com.sca4d.common.concurrent.lock;

/**
 * 仅允许在当前线程持有分布式锁期间调用的业务操作。
 *
 * @param <T> 返回值类型
 */
@FunctionalInterface
public interface LockedOperation<T> {

    /**
     * 执行已经完成集群互斥保护的业务逻辑。
     *
     * @return 业务操作返回值
     * @throws Throwable 原业务操作失败时抛出
     */
    T execute() throws Throwable;
}
