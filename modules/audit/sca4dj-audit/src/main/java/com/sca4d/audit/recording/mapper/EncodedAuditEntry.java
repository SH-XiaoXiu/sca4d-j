package com.sca4d.audit.recording.mapper;

import com.sca4d.core.api.audit.AuditResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 保存 audit_record 查询和写入使用的数据库列值。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EncodedAuditEntry {
    private final long id;

    private final @NotNull String requestId;

    private final long subjectId;

    private final @Nullable Long accountId;

    private final long principalId;

    private final @NotNull String operation;

    private final @Nullable String targetType;

    private final @Nullable String targetId;

    private final @NotNull AuditResult result;

    private final @Nullable String payloadType;

    private final @Nullable Integer payloadVersion;

    private final @Nullable String payloadJson;

    private final @Nullable String httpMethod;

    private final @Nullable String httpPath;

    private final @Nullable String clientIp;

    private final @Nullable String userAgent;

    private final @NotNull Instant occurredAt;

    private final @Nullable String instanceId;

    /**
     * 创建已经完成 JSON 编码的审计字段。
     *
     * @param id 主键
     * @param requestId 调用标识
     * @param subjectId Subject 标识快照
     * @param accountId 可选 Account 标识快照
     * @param principalId Principal 标识快照
     * @param operation Operation 标识
     * @param targetType 可选目标 Resource 类型
     * @param targetId 可选目标 Resource 标识
     * @param result 操作结果
     * @param payloadType 可选 payload 持久化类型
     * @param payloadVersion 可选 payload JSON 版本
     * @param payloadJson 可选 payload JSON
     * @param httpMethod 可选 HTTP 方法
     * @param httpPath 可选不含查询参数的 HTTP 路径
     * @param clientIp 可选直连客户端地址
     * @param userAgent 可选清理后的 User-Agent
     * @param occurredAt 操作发生时间
     */
    public EncodedAuditEntry(
            long id,
            @NotNull String requestId,
            long subjectId,
            @Nullable Long accountId,
            long principalId,
            @NotNull String operation,
            @Nullable String targetType,
            @Nullable String targetId,
            @NotNull AuditResult result,
            @Nullable String payloadType,
            @Nullable Integer payloadVersion,
            @Nullable String payloadJson,
            @Nullable String httpMethod,
            @Nullable String httpPath,
            @Nullable String clientIp,
            @Nullable String userAgent,
            @NotNull Instant occurredAt,
            @Nullable String instanceId) {
        this.id = id;
        this.requestId = requestId;
        this.subjectId = subjectId;
        this.accountId = accountId;
        this.principalId = principalId;
        this.operation = operation;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.payloadType = payloadType;
        this.payloadVersion = payloadVersion;
        this.payloadJson = payloadJson;
        this.httpMethod = httpMethod;
        this.httpPath = httpPath;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.occurredAt = occurredAt;
        this.instanceId = instanceId;
    }

    /** 为冻结的审计字段分配数据库主键，其他列保持不变。 */
    public @NotNull EncodedAuditEntry withId(long id) {
        return new EncodedAuditEntry(
                id,
                requestId,
                subjectId,
                accountId,
                principalId,
                operation,
                targetType,
                targetId,
                result,
                payloadType,
                payloadVersion,
                payloadJson,
                httpMethod,
                httpPath,
                clientIp,
                userAgent,
                occurredAt,
                instanceId);
    }

    /** @return 主键 */
    public long id() {
        return id;
    }

    /** @return 调用标识 */
    public @NotNull String requestId() {
        return requestId;
    }

    /** @return Subject 标识快照 */
    public long subjectId() {
        return subjectId;
    }

    /** @return 可选 Account 标识快照 */
    public @Nullable Long accountId() {
        return accountId;
    }

    /** @return Principal 标识快照 */
    public long principalId() {
        return principalId;
    }

    /** @return Operation 标识 */
    public @NotNull String operation() {
        return operation;
    }

    /** @return 可选目标 Resource 类型 */
    public @Nullable String targetType() {
        return targetType;
    }

    /** @return 可选目标 Resource 标识 */
    public @Nullable String targetId() {
        return targetId;
    }

    /** @return 操作结果 */
    public @NotNull AuditResult result() {
        return result;
    }

    /** @return 可选 payload 持久化类型 */
    public @Nullable String payloadType() {
        return payloadType;
    }

    /** @return 可选 payload JSON 版本 */
    public @Nullable Integer payloadVersion() {
        return payloadVersion;
    }

    /** @return 可选 payload JSON */
    public @Nullable String payloadJson() {
        return payloadJson;
    }

    /** @return 可选 HTTP 方法 */
    public @Nullable String httpMethod() {
        return httpMethod;
    }

    /** @return 可选不含查询参数的 HTTP 路径 */
    public @Nullable String httpPath() {
        return httpPath;
    }

    /** @return 可选直连客户端地址 */
    public @Nullable String clientIp() {
        return clientIp;
    }

    /** @return 可选清理后的 User-Agent */
    public @Nullable String userAgent() {
        return userAgent;
    }

    /** @return 操作发生时间 */
    public @NotNull Instant occurredAt() {
        return occurredAt;
    }

    /** 返回实际执行实例；旧记录没有此字段时为空。 */
    public @Nullable String instanceId() {
        return instanceId;
    }
}
