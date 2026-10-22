package com.sca4d.core.api.audit;

import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 保存一次操作的行为主体、目标资源、结果和发生时间。 */
public class AuditRecord {

    private final AuditActor actor;

    private final AuditOperation operation;

    private final AuditPayload payload;

    private final AuditResult result;

    private final HttpCall httpCall;

    private final Instant occurredAt;

    private final List<Resource.Ref> relatedResources;

    private final Resource target;

    private final String auditId;

    private final String auditParentId;

    private final String auditTrailId;

    private final String requestId;

    /**
     * 创建审计记录。
     *
     * @param requestId 调用链标识
     * @param actor 行为主体
     * @param operation 业务操作
     * @param target 可选目标资源
     * @param result 操作结果
     * @param payload 可选结构化载荷
     * @param httpCall 可选 HTTP 请求快照
     * @param occurredAt 事件发生时间
     */
    public AuditRecord(
            String requestId,
            AuditActor actor,
            AuditOperation operation,
            Resource target,
            AuditResult result,
            AuditPayload payload,
            HttpCall httpCall,
            Instant occurredAt) {
        this(
                requestId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                actor,
                operation,
                target,
                result,
                payload,
                httpCall,
                occurredAt,
                List.of());
    }

    /** 创建没有链路元数据的固定审计记录；仅供已有非调用生命周期写入点使用。 */
    public AuditRecord(
            @NotNull String requestId,
            @NotNull AuditActor actor,
            @NotNull AuditOperation operation,
            @Nullable Resource target,
            @NotNull AuditResult result,
            @Nullable AuditPayload payload,
            @Nullable HttpCall httpCall,
            @NotNull Instant occurredAt,
            @NotNull Collection<? extends Resource> relatedResources) {
        this(
                requestId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                actor,
                operation,
                target,
                result,
                payload,
                httpCall,
                occurredAt,
                relatedResources);
    }

    /** 创建审计记录并复制操作当时关联的资源标识；后续资源移动不改变快照。 */
    public AuditRecord(
            @NotNull String requestId,
            @NotNull String auditId,
            @NotNull String auditTrailId,
            @Nullable String auditParentId,
            @NotNull AuditActor actor,
            @NotNull AuditOperation operation,
            @Nullable Resource target,
            @NotNull AuditResult result,
            @Nullable AuditPayload payload,
            @Nullable HttpCall httpCall,
            @NotNull Instant occurredAt,
            @NotNull Collection<? extends Resource> relatedResources) {
        this.auditId = Objects.requireNonNull(auditId, "审计记录标识不能为空");
        this.auditTrailId = Objects.requireNonNull(auditTrailId, "审计链标识不能为空");
        this.auditParentId = auditParentId;
        this.relatedResources = Objects.requireNonNull(relatedResources, "关联资源不能为空")
                .stream()
                .map(resource -> {
                    Resource checked = Objects.requireNonNull(resource, "关联资源不能为 null");
                    return new Resource.Ref(checked.type(), checked.id());
                })
                .distinct()
                .toList();
        this.requestId = Objects.requireNonNull(requestId, "调用链标识不能为空");
        this.actor = Objects.requireNonNull(actor, "审计行为主体不能为空");
        this.operation = Objects.requireNonNull(operation, "审计操作不能为空");
        this.target = target;
        this.result = Objects.requireNonNull(result, "审计结果不能为空");
        this.payload = payload;
        this.httpCall = httpCall;
        this.occurredAt = Objects.requireNonNull(occurredAt, "审计时间不能为空");
    }

    /** @return 当前审计记录逻辑标识 */
    public @NotNull String auditId() {
        return auditId;
    }

    /** @return 当前审计链标识 */
    public @NotNull String auditTrailId() {
        return auditTrailId;
    }

    /** @return 父审计记录逻辑标识 */
    public @Nullable String auditParentId() {
        return auditParentId;
    }

    /** 返回操作当时的关联资源，不包含可变业务对象。 */
    public @NotNull @Unmodifiable List<Resource.Ref> relatedResources() {
        return relatedResources;
    }

    /**
     * 调用链标识。
     *
     * @return 调用链标识
     */
    public String requestId() {
        return requestId;
    }

    /**
     * 行为主体。
     *
     * @return 行为主体
     */
    public AuditActor actor() {
        return actor;
    }

    /**
     * 业务操作。
     *
     * @return 业务操作
     */
    public AuditOperation operation() {
        return operation;
    }

    /**
     * 返回可选目标资源。
     *
     * @return 可选目标资源
     */
    public Optional<Resource> target() {
        return Optional.ofNullable(target);
    }

    /**
     * 操作结果。
     *
     * @return 操作结果
     */
    public AuditResult result() {
        return result;
    }

    /**
     * 返回可选结构化载荷。
     *
     * @return 可选结构化载荷
     */
    public Optional<AuditPayload> payload() {
        return Optional.ofNullable(payload);
    }

    /**
     * 返回可选 HTTP 请求快照。
     *
     * @return HTTP 调用返回请求快照，消息和任务调用返回空
     */
    public Optional<HttpCall> httpCall() {
        return Optional.ofNullable(httpCall);
    }

    /**
     * 事件发生时间。
     *
     * @return 事件发生时间
     */
    public Instant occurredAt() {
        return occurredAt;
    }
}
