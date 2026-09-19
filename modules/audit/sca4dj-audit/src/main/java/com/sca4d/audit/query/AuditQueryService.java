package com.sca4d.audit.query;

import com.sca4d.audit.recording.AuditRepository;
import com.sca4d.core.api.query.Page;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 按标识或筛选条件读取 `audit_record` 中的审计记录。 */
public class AuditQueryService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditQueryService.class);

    private final @NotNull AuditRepository repository;

    /** 创建只读取 Audit 模块数据的查询服务。 */
    public AuditQueryService(@NotNull AuditRepository repository) {
        this.repository = Objects.requireNonNull(repository, "审计 Repository 不能为空");
    }

    /**
     * 读取必须存在的审计记录。
     *
     * @param id 正数审计记录标识
     * @return 完整审计记录和原始 payload
     * @throws IllegalArgumentException id 不是正数或记录不存在时抛出
     */
    @Transactional(readOnly = true)
    public @NotNull AuditEntry loadById(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("审计记录标识必须为正数");
        }
        AuditEntry entry = repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException("审计记录不存在: " + id));
        LOG.trace(
                "审计记录已读取 | auditId={} principalId={} operation={} result={}",
                entry.id(),
                entry.actor().principalId(),
                entry.operation().id(),
                entry.result());
        return entry;
    }

    /**
     * 按可选身份、操作、结果、目标和时间条件分页读取审计记录。
     *
     * @param query 已校验的查询条件
     * @return 按发生时间和标识倒序的分页结果
     */
    @Transactional(readOnly = true)
    public @NotNull Page<AuditEntry> page(@NotNull AuditQuery query) {
        AuditQuery checked = Objects.requireNonNull(query, "审计查询不能为空");
        Page<AuditEntry> page = repository.page(checked);
        LOG.debug(
                "审计记录分页查询完成 | requestId={} accountId={} principalId={} operation={} result={} targetType={} targetId={} page={} size={} total={} returnedCount={}",
                checked.requestId(),
                checked.accountId(),
                checked.principalId(),
                checked.operation(),
                checked.result(),
                checked.targetType(),
                checked.targetId(),
                checked.page().page(),
                checked.page().size(),
                page.getTotal(),
                page.getItems().size());
        return page;
    }

}
