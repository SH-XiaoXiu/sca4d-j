package com.sca4d.system.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.mapper.SysOperationLogMapper;
import com.sca4d.system.audit.OperationLogOperatorOption;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 操作日志仓储。
 */
@Repository
@RequiredArgsConstructor
public class OperationLogRepository {

    private final SysOperationLogMapper mapper;

    /**
     * 分页读取操作日志历史数据中的操作人快照选项。
     *
     * <p>固定执行一条去重计数和一条分页查询。选项来自审计发生时保存的名称，不依赖当前用户是否已删除，也不逐项回查 IAM。
     *
     * @param search Subject 展示名称搜索；空白时读取全部
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 操作日志业务模块可筛选的 Subject 快照分页
     */
    public @NotNull PageData<OperationLogOperatorOption> pageOperatorOptions(
            @Nullable String search, long page, long size) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        long total = mapper.countOperationLogOperators(normalizedSearch);
        if (total == 0) {
            return PageData.empty(page, size);
        }
        return new PageData<>(
                mapper.selectOperationLogOperators(normalizedSearch, (page - 1) * size, size),
                page,
                size,
                total);
    }

    /**
     * 追加一条已经编码完成的审计记录。
     *
     * @param entity 操作日志索引字段
     * @param detailJson 完整审计详情 JSON
     */
    public void appendAudit(@NotNull SysOperationLogEntity entity, @NotNull String detailJson) {
        if (mapper.insertOperationLog(entity, detailJson) != 1) {
            throw new IllegalStateException("操作日志写入行数异常");
        }
    }

    /**
     * 分页查询操作日志。
     *
     * @param moduleCode 模块编码筛选
     * @param actionCode 动作编码筛选
     * @param result 执行结果筛选
     * @param subjectId 操作身份筛选
     * @param principalId 操作主体筛选
     * @param targetType 目标资源类型筛选
     * @param targetId 目标资源主键筛选
     * @param traceId 请求追踪标识筛选
     * @param createdAtStart 创建时间下界
     * @param createdAtEnd 创建时间上界
     * @param page 页码
     * @param size 每页记录数
     * @return 操作日志分页
     */
    public @NotNull PageData<SysOperationLogEntity> page(
            @Nullable String moduleCode,
            @Nullable String actionCode,
            @Nullable AuditResult result,
            @Nullable Long subjectId,
            @Nullable Long principalId,
            @Nullable String targetType,
            @Nullable String targetId,
            @Nullable String traceId,
            @Nullable OffsetDateTime createdAtStart,
            @Nullable OffsetDateTime createdAtEnd,
            long page,
            long size) {
        QueryWrapper wrapper = QueryWrapper.create()
                .select(
                        column(SysOperationLogEntity::getId),
                        column(SysOperationLogEntity::getPrincipalId),
                        column(SysOperationLogEntity::getSubjectId),
                        column(SysOperationLogEntity::getAccountId),
                        column(SysOperationLogEntity::getSubjectDisplayName),
                        column(SysOperationLogEntity::getPrincipalDisplayName),
                        column(SysOperationLogEntity::getModuleCode),
                        column(SysOperationLogEntity::getActionCode),
                        column(SysOperationLogEntity::getResult),
                        column(SysOperationLogEntity::getRequestUri),
                        column(SysOperationLogEntity::getTargetType),
                        column(SysOperationLogEntity::getTargetId),
                        column(SysOperationLogEntity::getDetailJson)
                                .cast("text")
                                .as(SysOperationLogEntity::getDetailJson),
                        column(SysOperationLogEntity::getCreatedAt))
                .where(SysOperationLogEntity::getModuleCode)
                .eq(moduleCode, value -> value != null && !value.isBlank())
                .and(SysOperationLogEntity::getActionCode)
                .eq(actionCode, value -> value != null && !value.isBlank())
                .and(SysOperationLogEntity::getResult)
                .eq(result == null ? null : result.value(), value -> value != null)
                .and(SysOperationLogEntity::getPrincipalId)
                .eq(principalId, value -> value != null)
                .and(SysOperationLogEntity::getSubjectId)
                .eq(subjectId, value -> value != null)
                .and(SysOperationLogEntity::getTargetType)
                .eq(targetType, value -> value != null && !value.isBlank())
                .and(SysOperationLogEntity::getTargetId)
                .eq(targetId, value -> value != null && !value.isBlank())
                .and(SysOperationLogEntity::getCreatedAt)
                .ge(createdAtStart, value -> value != null)
                .and(SysOperationLogEntity::getCreatedAt)
                .le(createdAtEnd, value -> value != null);
        if (traceId != null && !traceId.isBlank()) {
            wrapper.and("detail_json -> 'request' ->> 'traceId' = ?", traceId);
        }
        wrapper.orderBy(SysOperationLogEntity::getCreatedAt).desc()
                .orderBy(SysOperationLogEntity::getId).desc();
        return PageData.from(mapper.paginate(page, size, wrapper));
    }

    /**
     * 查询操作日志详情。
     *
     * @param id 操作日志主键
     * @return 操作日志记录
     */
    public @NotNull Optional<SysOperationLogEntity> findById(@NotNull Long id) {
        return Optional.ofNullable(mapper.findOperationLogById(id));
    }
}
