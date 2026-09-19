package com.sca4d.core.api.audit;

/** 表示 Audit 声明没有复杂 payload 工厂。 */
public final class NoAuditPayloadFactory implements AuditPayloadFactory {

    /** 创建占位工厂。 */
    public NoAuditPayloadFactory() {
    }

    @Override
    public AuditPayload create(AuditInvocation invocation) {
        throw new IllegalStateException("当前 Audit 没有 payload 工厂");
    }
}
