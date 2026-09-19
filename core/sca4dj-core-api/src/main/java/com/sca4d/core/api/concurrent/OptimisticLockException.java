package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;

/**
 * 预期版本与当前版本不一致时抛出的乐观锁异常（Optimistic Lock Exception）。
 *
 * <p>持久化实现应在同一次原子写入中比较 {@link Version} 并更新数据；写入未命中预期版本时抛出本异常。
 */
public final class OptimisticLockException extends BusinessException {

    /**
     * 创建乐观锁异常。
     *
     * @param target 发生版本冲突的对象说明
     */
    public OptimisticLockException(String target) {
        super(CoreErrorCode.CONFLICT, null, target);
    }
}
