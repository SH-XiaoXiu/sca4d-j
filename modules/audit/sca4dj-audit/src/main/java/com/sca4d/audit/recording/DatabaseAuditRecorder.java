package com.sca4d.audit.recording;

import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 把 Core AuditRecord 同步追加到 audit_record。 */
@ApiStatus.Internal
public class DatabaseAuditRecorder implements AuditRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseAuditRecorder.class);

    private final @NotNull AuditRepository repository;

    /** 创建同步数据库审计记录器。 */
    public DatabaseAuditRecorder(
            @NotNull AuditRepository repository) {
        this.repository = Objects.requireNonNull(repository, "审计 Repository 不能为空");
    }

    /**
     * 同步保存审计记录；编码或数据库写入失败时原样终止当前调用。
     *
     * @param record Core 已完成身份、操作、目标和结果采集的记录
     */
    @Override
    @Transactional
    public void record(@NotNull AuditRecord record) {
        AuditRecord checked = Objects.requireNonNull(record, "审计记录不能为空");
        repository.append(checked);
        LOG.debug(
                "审计记录已写入当前事务 | requestId={} principalId={} operation={} result={}",
                checked.requestId(),
                checked.actor().principalId(),
                checked.operation().id(),
                checked.result());
    }
}
