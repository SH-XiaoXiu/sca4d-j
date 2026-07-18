package com.sca4d.common.concurrent;

import org.jetbrains.annotations.NotNull;

/**
 * 带数据版本号的写请求。
 *
 * <p>需要乐观锁保护的更新请求实现该接口，Controller 显式把 version 交给 Service，Repository 将其写入原子更新条件。
 * 更新失败时抛出 {@link ConcurrencyConflictException}，由框架共用处理前端刷新契约。
 */
public interface VersionedRequest {

    /**
     * 返回客户端读取待编辑数据时获得的版本数据。
     *
     * @return Repository 原子条件更新使用的预期版本
     */
    @NotNull
    Long getVersion();
}
