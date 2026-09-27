package com.sca4d.app.server.service;

import com.sca4d.app.server.audit.OperationLog;
import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.audit.query.AuditEntry;
import com.sca4d.audit.query.AuditQuery;
import com.sca4d.audit.query.AuditQueryService;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.query.Page;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 查询 Audit 模块记录，并补充 Sca4d Server 拥有的 Principal 展示名称。 */
@Service
public class OperationLogService {
    private static final Logger LOG = LoggerFactory.getLogger(OperationLogService.class);

    private final @NotNull AuditQueryService audit;

    private final @NotNull PlatformUserProfileService profiles;

    /**
     * 创建管理端操作日志服务。
     *
     * @param audit Audit 模块查询服务
     * @param profiles Sca4d Server 用户资料服务
     */
    public OperationLogService(
            @NotNull AuditQueryService audit,
            @NotNull PlatformUserProfileService profiles) {
        this.audit = Objects.requireNonNull(audit, "审计查询服务不能为空");
        this.profiles = Objects.requireNonNull(profiles, "Platform 用户资料服务不能为空");
    }

    /**
     * 查询一条必须存在的操作日志。
     *
     * @param id 正数审计记录标识
     * @return 已补充可选 Principal 展示名称的记录
     * @throws BusinessException 记录不存在时抛出统一资源不存在结果
     */
    public @NotNull OperationLog loadById(long id) {
        AuditEntry entry;
        try {
            entry = audit.loadById(id);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CoreErrorCode.NOT_FOUND, exception);
        }
        Map<Long, String> names = principalNames(List.of(entry.actor().principalId()));
        LOG.debug("操作日志详情读取完成 | auditId={} principalId={}", id, entry.actor().principalId());
        return new OperationLog(entry, names.get(entry.actor().principalId()));
    }

    /**
     * 按 Audit 查询条件分页读取操作日志。
     *
     * @param query 已校验的审计查询条件
     * @return 已补充可选 Principal 展示名称的分页结果
     */
    public @NotNull Page<OperationLog> page(@NotNull AuditQuery query) {
        Page<AuditEntry> page = audit.page(Objects.requireNonNull(query, "审计查询不能为空"));
        List<Long> principalIds = page.getItems()
                .stream()
                .map(entry -> entry.actor().principalId())
                .distinct()
                .toList();
        Map<Long, String> names = principalNames(principalIds);
        LOG.debug(
                "操作日志分页读取完成 | page={} size={} total={} returnedCount={}",
                page.getPage(),
                page.getSize(),
                page.getTotal(),
                page.getItems().size());
        return page.map(entry -> new OperationLog(entry, names.get(entry.actor().principalId())));
    }

    /** 从 Sca4d Server 用户资料中读取仍然存在的 Principal 展示名称。 */
    private @NotNull Map<Long, String> principalNames(@NotNull Collection<Long> principalIds) {
        if (principalIds.isEmpty()) {
            return Map.of();
        }
        return profiles.findAll(principalIds)
                .stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                PlatformUserProfile::principalId,
                                PlatformUserProfile::displayName,
                                (first, ignored) -> first));
    }
}
