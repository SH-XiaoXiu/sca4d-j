package com.sca4d.core.api.audit;

/** 保存或发送 {@link AuditRecord}。 */
public interface AuditRecorder {

    /**
     * 记录审计操作。
     *
     * @param record 审计记录
     */
    void record(AuditRecord record);
}
