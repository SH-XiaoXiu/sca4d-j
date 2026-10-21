package com.sca4d.audit.query;

import com.sca4d.audit.recording.AuditRepository;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
     * 判断审计记录是否保存了指定资源的历史关联。
     *
     * <p>业务详情查询应先完成资源授权，再使用此方法限制记录；结果不读取资源当前归属。
     *
     * @param id 审计记录标识
     * @param resource 要匹配的资源
     * @return 该记录保存过同类型、同标识的资源关联时为 true
     */
    @Transactional(readOnly = true)
    public boolean relatesTo(long id, @NotNull Resource resource) {
        return repository.relatesTo(id, Objects.requireNonNull(resource, "关联资源不能为空"));
    }

    /**
     * 按持久化标识范围有界扫描审计记录。
     *
     * <p>调用方可以保存最后一条标识作为下次 {@code afterId}，避免深分页；单次最多读取 500 条。
     *
     * @param afterId 不包含的起始标识
     * @param throughId 包含的结束标识
     * @param size 本次读取上限，范围为 1 到 500
     * @return 按标识递增排列的审计记录
     * @throws IllegalArgumentException 范围或批量大小无效时抛出
     */
    @Transactional(readOnly = true)
    public List<AuditEntry> scan(long afterId, long throughId, int size) {
        if (afterId < 0 || throughId < afterId || size < 1 || size > 500) {
            throw new IllegalArgumentException("审计扫描范围或批量大小无效");
        }
        return repository.scan(afterId, throughId, size);
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
