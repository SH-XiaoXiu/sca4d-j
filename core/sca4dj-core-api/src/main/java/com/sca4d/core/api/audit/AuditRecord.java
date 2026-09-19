package com.sca4d.core.api.audit;

import com.sca4d.core.api.resource.Resource;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 保存一次操作的行为主体、目标资源、结果和发生时间。 */
public final class AuditRecord {

    private final String requestId;

    private final AuditActor actor;

    private final AuditOperation operation;

    private final Resource target;

    private final AuditResult result;

    private final AuditPayload payload;

    private final HttpCall httpCall;

    private final Instant occurredAt;

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
        this.requestId = Objects.requireNonNull(requestId, "调用链标识不能为空");
        this.actor = Objects.requireNonNull(actor, "审计行为主体不能为空");
        this.operation = Objects.requireNonNull(operation, "审计操作不能为空");
        this.target = target;
        this.result = Objects.requireNonNull(result, "审计结果不能为空");
        this.payload = payload;
        this.httpCall = httpCall;
        this.occurredAt = Objects.requireNonNull(occurredAt, "审计时间不能为空");
    }

    /**
     * 返回调用链标识。
     *
     * @return 调用链标识
     */
    public String requestId() {
        return requestId;
    }

    /**
     * 返回行为主体。
     *
     * @return 行为主体
     */
    public AuditActor actor() {
        return actor;
    }

    /**
     * 返回业务操作。
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
     * 返回操作结果。
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
     * 返回事件发生时间。
     *
     * @return 事件发生时间
     */
    public Instant occurredAt() {
        return occurredAt;
    }
}
