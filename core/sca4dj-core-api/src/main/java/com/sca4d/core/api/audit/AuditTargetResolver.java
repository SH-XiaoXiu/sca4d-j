package com.sca4d.core.api.audit;

import com.sca4d.core.api.resource.Resource;

/** 为复杂审计声明解析目标 Resource。 */
@FunctionalInterface
public interface AuditTargetResolver {

    /**
     * 从完整调用快照中取得审计目标。
     *
     * @param invocation 当前调用快照
     * @return 目标 Resource
     */
    Resource resolve(AuditInvocation invocation);
}
