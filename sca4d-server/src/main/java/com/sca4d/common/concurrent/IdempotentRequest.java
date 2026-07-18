package com.sca4d.common.concurrent;

import org.jetbrains.annotations.Nullable;

/**
 * 带幂等键的写请求。
 *
 * <p>用于表达一次客户端提交意图。前端发起新的提交意图时生成新的幂等键；
 * 网络重试或页面重复提交同一意图时必须复用同一个幂等键。
 */
public interface IdempotentRequest {

    /**
     * 返回客户端为一次提交意图生成的固定幂等键。
     *
     * @return 未提供时返回 null；启用幂等保护的方法会在业务执行前拒绝缺失值
     */
    @Nullable
    String getIdempotencyKey();
}
