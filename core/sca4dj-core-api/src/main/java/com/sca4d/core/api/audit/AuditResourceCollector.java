package com.sca4d.core.api.audit;

import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;

/** 从调用已经产生的业务事实补充关联资源；不得根据后来的资源归属改写历史。 */
public interface AuditResourceCollector {
    /**
     * 在最终结果确定后收集资源；拒绝和失败调用也可能进入，不能假设已经授权或已经提交。
     * 返回值只用于审计快照，不得执行业务写入。没有可靠证据时返回空集合。
     */
    @NotNull
    Collection<? extends Resource> collect(
            @NotNull AuditInvocation invocation,
            @Nullable Resource target,
            @NotNull Instant occurredAt);
}
