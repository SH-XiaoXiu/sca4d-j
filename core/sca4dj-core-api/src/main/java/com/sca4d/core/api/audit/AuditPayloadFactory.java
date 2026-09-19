package com.sca4d.core.api.audit;

/** 为复杂审计声明生成经过业务筛选的结构化 payload。 */
@FunctionalInterface
public interface AuditPayloadFactory {

    /**
     * 从完整调用快照中生成审计 payload。
     *
     * @param invocation 当前调用快照
     * @return 结构化审计 payload
     */
    AuditPayload create(AuditInvocation invocation);
}
