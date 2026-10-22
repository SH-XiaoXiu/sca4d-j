package com.sca4d.audit.query;

import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** 保存审计记录分页查询使用的可选身份、操作、结果、目标和时间条件。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class AuditQuery {
    private final @Nullable AuditResult result;

    private final @Nullable Instant occurredFrom;

    private final @Nullable Instant occurredTo;

    private final @Nullable List<Resource.Ref> resourceMatches;

    private final @Nullable Long accountId;

    private final @Nullable Long principalId;

    private final @NotNull PageRequest page;

    private final @Nullable String operation;

    private final @Nullable String parentId;

    private final @Nullable String relatedId;

    private final @Nullable String relatedType;

    private final @Nullable String requestId;

    private final @Nullable String targetId;

    private final @Nullable String targetType;

    private final @Nullable String trailId;

    /**
     * 创建审计记录分页查询。
     *
     * @param requestId 可选精确调用标识
     * @param accountId 可选 Account 标识
     * @param principalId 可选 Principal 标识
     * @param operation 可选精确 Operation 标识
     * @param result 可选结果
     * @param targetType 可选目标 Resource 类型；与 targetId 同时提供
     * @param targetId 可选目标 Resource 标识；与 targetType 同时提供
     * @param occurredFrom 可选发生时间下界，包含该时间
     * @param occurredTo 可选发生时间上界，不包含该时间
     * @param page 从 1 开始且每页数量不超过 100 的分页要求
     * @throws IllegalArgumentException Account 或 Principal 标识、目标组合、时间区间或分页参数无效时抛出
     */
    public AuditQuery(
            @Nullable String requestId,
            @Nullable Long accountId,
            @Nullable Long principalId,
            @Nullable String operation,
            @Nullable AuditResult result,
            @Nullable String targetType,
            @Nullable String targetId,
            @Nullable Instant occurredFrom,
            @Nullable Instant occurredTo,
            @NotNull PageRequest page) {
        this(
                requestId,
                accountId,
                principalId,
                operation,
                result,
                targetType,
                targetId,
                occurredFrom,
                occurredTo,
                page,
                null,
                null);
    }

    /** 在普通条件外按已冻结的关联资源过滤；必须同时提供类型和标识。 */
    public AuditQuery(
            @Nullable String requestId,
            @Nullable Long accountId,
            @Nullable Long principalId,
            @Nullable String operation,
            @Nullable AuditResult result,
            @Nullable String targetType,
            @Nullable String targetId,
            @Nullable Instant occurredFrom,
            @Nullable Instant occurredTo,
            @NotNull PageRequest page,
            @Nullable String relatedType,
            @Nullable String relatedId) {
        this(
                requestId,
                accountId,
                principalId,
                operation,
                result,
                targetType,
                targetId,
                occurredFrom,
                occurredTo,
                page,
                relatedType,
                relatedId,
                null,
                null,
                null);
    }

    /** 在身份、资源和时间条件外按审计链或直接父记录筛选。 */
    public AuditQuery(
            @Nullable String requestId,
            @Nullable Long accountId,
            @Nullable Long principalId,
            @Nullable String operation,
            @Nullable AuditResult result,
            @Nullable String targetType,
            @Nullable String targetId,
            @Nullable Instant occurredFrom,
            @Nullable Instant occurredTo,
            @NotNull PageRequest page,
            @Nullable String relatedType,
            @Nullable String relatedId,
            @Nullable String trailId,
            @Nullable String parentId) {
        this(
                requestId,
                accountId,
                principalId,
                operation,
                result,
                targetType,
                targetId,
                occurredFrom,
                occurredTo,
                page,
                relatedType,
                relatedId,
                null,
                trailId,
                parentId);
    }

    private AuditQuery(
            @Nullable String requestId,
            @Nullable Long accountId,
            @Nullable Long principalId,
            @Nullable String operation,
            @Nullable AuditResult result,
            @Nullable String targetType,
            @Nullable String targetId,
            @Nullable Instant occurredFrom,
            @Nullable Instant occurredTo,
            @NotNull PageRequest page,
            @Nullable String relatedType,
            @Nullable String relatedId,
            @Nullable List<Resource.Ref> resourceMatches,
            @Nullable String trailId,
            @Nullable String parentId) {
        this.resourceMatches = resourceMatches == null ? null : List.copyOf(resourceMatches);
        this.relatedType = optional(relatedType);
        this.relatedId = optional(relatedId);
        if ((this.relatedType == null) != (this.relatedId == null)) {
            throw new IllegalArgumentException("关联资源类型和标识必须同时提供");
        }
        if (accountId != null && accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        if (principalId != null && principalId <= 0) {
            throw new IllegalArgumentException("Principal 标识必须为正数");
        }
        this.requestId = optional(requestId);
        this.accountId = accountId;
        this.principalId = principalId;
        this.operation = optional(operation);
        this.result = result;
        this.targetType = optional(targetType);
        this.targetId = optional(targetId);
        if ((this.targetType == null) != (this.targetId == null)) {
            throw new IllegalArgumentException("目标资源类型和标识必须同时提供");
        }
        if (occurredFrom != null && occurredTo != null && !occurredFrom.isBefore(occurredTo)) {
            throw new IllegalArgumentException("审计时间下界必须早于上界");
        }
        this.occurredFrom = occurredFrom;
        this.occurredTo = occurredTo;
        this.page = page.verifyMaxSize(100);
        this.trailId = optional(trailId);
        this.parentId = optional(parentId);
    }

    /** 限定主目标或关联资源；空集合明确不匹配记录，不会取消过滤。 */
    public @NotNull AuditQuery matchingResources(@NotNull List<Resource.Ref> resources) {
        return new AuditQuery(
                requestId,
                accountId,
                principalId,
                operation,
                result,
                targetType,
                targetId,
                occurredFrom,
                occurredTo,
                page,
                relatedType,
                relatedId,
                List.copyOf(resources),
                trailId,
                parentId);
    }

    /** @return 限定同一业务审计链 */
    public @NotNull AuditQuery trail(@NotNull String value) {
        return new AuditQuery(
                requestId,
                accountId,
                principalId,
                operation,
                result,
                targetType,
                targetId,
                occurredFrom,
                occurredTo,
                page,
                relatedType,
                relatedId,
                resourceMatches,
                value,
                null);
    }

    /** @return 限定直接父审计记录 */
    public @NotNull AuditQuery parent(@NotNull String value) {
        return new AuditQuery(
                requestId,
                accountId,
                principalId,
                operation,
                result,
                targetType,
                targetId,
                occurredFrom,
                occurredTo,
                page,
                relatedType,
                relatedId,
                resourceMatches,
                null,
                value);
    }

    public @Nullable List<Resource.Ref> resourceMatches() {
        return resourceMatches;
    }

    public @Nullable String relatedType() {
        return relatedType;
    }

    public @Nullable String relatedId() {
        return relatedId;
    }

    /** @return 可选精确调用标识 */
    public @Nullable String requestId() {
        return requestId;
    }

    /** @return 可选 Account 标识 */
    public @Nullable Long accountId() {
        return accountId;
    }

    /** @return 可选 Principal 标识 */
    public @Nullable Long principalId() {
        return principalId;
    }

    /** @return 可选精确 Operation 标识 */
    public @Nullable String operation() {
        return operation;
    }

    /** @return 可选结果 */
    public @Nullable AuditResult result() {
        return result;
    }

    /** @return 可选目标 Resource 类型 */
    public @Nullable String targetType() {
        return targetType;
    }

    /** @return 可选目标 Resource 标识 */
    public @Nullable String targetId() {
        return targetId;
    }

    public @Nullable String trailId() {
        return trailId;
    }

    public @Nullable String parentId() {
        return parentId;
    }

    /** @return 可选发生时间下界；MyBatis XML 通过 OGNL 读取 */
    @SuppressWarnings("unused") // MyBatis XML 通过 OGNL 调用，IDEA 无法静态识别。
    public @Nullable Instant occurredFrom() {
        return occurredFrom;
    }

    /** @return 可选发生时间上界；MyBatis XML 通过 OGNL 读取 */
    @SuppressWarnings("unused") // MyBatis XML 通过 OGNL 调用，IDEA 无法静态识别。
    public @Nullable Instant occurredTo() {
        return occurredTo;
    }

    /** @return 从 1 开始且每页数量不超过 100 的分页要求 */
    public @NotNull PageRequest page() {
        return page;
    }

    /** 把空值或空白查询文本转换为空条件，其余文本去除首尾空白。 */
    private static @Nullable String optional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String checked = value.trim();
        return checked.isEmpty() ? null : checked;
    }
}
