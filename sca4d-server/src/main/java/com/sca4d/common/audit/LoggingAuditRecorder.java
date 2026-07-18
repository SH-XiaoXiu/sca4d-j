package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 未提供持久化审计实现时，将最小审计索引数据写入应用日志的回退记录器。 */
public class LoggingAuditRecorder implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuditRecorder.class);

    /**
     * 记录不包含场景载荷和异常消息的审计索引字段。
     *
     * @param record 当前业务调用采集完成的审计数据快照
     */
    @Override
    public void record(@NotNull AuditRecord record) {
        log.info(
                "操作审计已记录 | module={} action={} result={} subjectId={} principalId={}",
                record.getModuleCode(),
                record.getActionCode(),
                record.getResult(),
                record.getActor() == null ? null : record.getActor().getSubjectId(),
                record.getActor() == null ? null : record.getActor().getPrincipalId());
    }
}
