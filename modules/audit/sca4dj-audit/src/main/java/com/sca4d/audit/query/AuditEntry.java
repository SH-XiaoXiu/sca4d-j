package com.sca4d.audit.query;

import com.sca4d.audit.payload.AuditPayloadData;
import com.sca4d.core.api.audit.AuditActor;
import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 返回一条持久化审计记录及其原始或强类型 payload。 */
public final class AuditEntry {
    private final long id;

    private final @NotNull String requestId;

    private final @NotNull AuditActor actor;

    private final @NotNull AuditOperation operation;

    private final @Nullable Resource target;

    private final @NotNull AuditResult result;

    private final @Nullable AuditPayloadData payload;

    private final @Nullable HttpCall httpCall;

    private final @NotNull Instant occurredAt;

    /**
     * 创建已从数据库恢复的审计记录。
     *
     * @param id 正数审计记录标识
     * @param requestId 调用链中的调用标识
     * @param actor 操作发生时的身份快照
     * @param operation 稳定业务操作
     * @param target 可选目标 Resource
     * @param result 操作结果
     * @param payload 可选原始和强类型 payload
     * @param httpCall 可选 HTTP 请求快照
     * @param occurredAt 操作发生时间
     * @throws NullPointerException requestId、actor、operation、result 或 occurredAt 为空时抛出
     * @throws IllegalArgumentException id 不是正数或 requestId 为空白文本时抛出
     */
    public AuditEntry(
            long id,
            @NotNull String requestId,
            @NotNull AuditActor actor,
            @NotNull AuditOperation operation,
            @Nullable Resource target,
            @NotNull AuditResult result,
            @Nullable AuditPayloadData payload,
            @Nullable HttpCall httpCall,
            @NotNull Instant occurredAt) {
        if (id <= 0) {
            throw new IllegalArgumentException("审计记录标识必须为正数");
        }
        this.id = id;
        String checkedRequestId = Objects.requireNonNull(requestId, "调用标识不能为空");
        if (checkedRequestId.isBlank()) {
            throw new IllegalArgumentException("调用标识不能为空白");
        }
        this.requestId = checkedRequestId;
        this.actor = Objects.requireNonNull(actor, "审计行为主体不能为空");
        this.operation = Objects.requireNonNull(operation, "审计操作不能为空");
        this.target = target;
        this.result = Objects.requireNonNull(result, "审计结果不能为空");
        this.payload = payload;
        this.httpCall = httpCall;
        this.occurredAt = Objects.requireNonNull(occurredAt, "审计发生时间不能为空");
    }

    /** @return 正数审计记录标识 */
    public long id() {
        return id;
    }

    /** @return 调用链中的调用标识 */
    public @NotNull String requestId() {
        return requestId;
    }

    /** @return 操作发生时的身份标识 */
    public @NotNull AuditActor actor() {
        return actor;
    }

    /** @return 稳定业务操作 */
    public @NotNull AuditOperation operation() {
        return operation;
    }

    /** @return 可选目标 Resource */
    public @NotNull Optional<Resource> target() {
        return Optional.ofNullable(target);
    }

    /** @return 操作结果 */
    public @NotNull AuditResult result() {
        return result;
    }

    /** @return 可选 payload */
    public @NotNull Optional<AuditPayloadData> payload() {
        return Optional.ofNullable(payload);
    }

    /** @return HTTP 调用返回请求快照，消息和任务调用返回空 */
    public @NotNull Optional<HttpCall> httpCall() {
        return Optional.ofNullable(httpCall);
    }

    /** @return 操作发生时间 */
    public @NotNull Instant occurredAt() {
        return occurredAt;
    }
}
