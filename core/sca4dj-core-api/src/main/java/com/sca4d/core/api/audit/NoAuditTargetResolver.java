package com.sca4d.core.api.audit;

import com.sca4d.core.api.resource.Resource;

/** 表示 Audit 声明没有复杂目标解析器。 */
public final class NoAuditTargetResolver implements AuditTargetResolver {

    /** 不允许创建占位解析器实例。 */
    public NoAuditTargetResolver() {
    }

    @Override
    public Resource resolve(AuditInvocation invocation) {
        throw new IllegalStateException("当前 Audit 没有目标解析器");
    }
}
