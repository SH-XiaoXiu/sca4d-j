package com.sca4d.app.server.system.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.app.server.audit.OperationLog;
import com.sca4d.audit.payload.AuditPayloadData;
import com.sca4d.audit.query.AuditEntry;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 返回管理端展示一条操作日志所需的身份、目标、结果、payload 和 HTTP 快照。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class OperationLogView {
    private final @NotNull Instant occurredAt;

    private final @Nullable Integer payloadVersion;

    private final @Nullable Long accountId;

    private final @Nullable String clientIp;

    private final @Nullable String httpMethod;

    private final @Nullable String httpPath;

    private final @NotNull String operation;

    private final @Nullable String payloadJson;

    private final @Nullable String payloadType;

    private final @Nullable String principalDisplayName;

    private final @NotNull String requestId;

    private final @NotNull String result;

    private final @Nullable String targetId;

    private final @Nullable String targetType;

    private final @Nullable String userAgent;

    private final long id;

    private final long principalId;

    private final long subjectId;

    private OperationLogView(
            long id,
            @NotNull String requestId,
            long subjectId,
            @Nullable Long accountId,
            long principalId,
            @Nullable String principalDisplayName,
            @NotNull String operation,
            @Nullable String targetType,
            @Nullable String targetId,
            @NotNull String result,
            @Nullable String payloadType,
            @Nullable Integer payloadVersion,
            @Nullable String payloadJson,
            @Nullable String httpMethod,
            @Nullable String httpPath,
            @Nullable String clientIp,
            @Nullable String userAgent,
            @NotNull Instant occurredAt) {
        this.id = id;
        this.requestId = requestId;
        this.subjectId = subjectId;
        this.accountId = accountId;
        this.principalId = principalId;
        this.principalDisplayName = principalDisplayName;
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
    }

    /**
     * 从补充了展示名称的应用读模型创建 HTTP 视图。
     *
     * @param log 操作日志应用读模型
     * @return 管理端 HTTP 视图
     */
    @CheckReturnValue
    public static @NotNull OperationLogView from(@NotNull OperationLog log) {
        OperationLog checked = Objects.requireNonNull(log, "操作日志不能为空");
        AuditEntry entry = checked.entry();
        Resource target = entry.target().orElse(null);
        AuditPayloadData payload = entry.payload().orElse(null);
        HttpCall httpCall = entry.httpCall().orElse(null);
        return new OperationLogView(
                entry.id(),
                entry.requestId(),
                entry.actor().subjectId(),
                entry.actor().accountId().orElse(null),
                entry.actor().principalId(),
                checked.principalDisplayName(),
                entry.operation().id(),
                target == null ? null : target.type().format(SLASH),
                target == null ? null : target.id(),
                entry.result().name(),
                payload == null ? null : payload.type(),
                payload == null ? null : payload.version(),
                payload == null ? null : payload.json(),
                httpCall == null ? null : httpCall.method(),
                httpCall == null ? null : httpCall.path(),
                httpCall == null ? null : httpCall.clientIp(),
                httpCall == null ? null : httpCall.userAgent().orElse(null),
                entry.occurredAt());
    }

    /** @return 审计记录标识 */
    public long getId() {
        return id;
    }

    /** @return 调用标识 */
    public @NotNull String getRequestId() {
        return requestId;
    }

    /** @return 操作发生时的 Subject 标识 */
    public long getSubjectId() {
        return subjectId;
    }

    /** @return 操作发生时的可选 Account 标识 */
    public @Nullable Long getAccountId() {
        return accountId;
    }

    /** @return 操作发生时的 Principal 标识 */
    public long getPrincipalId() {
        return principalId;
    }

    /** @return 可选 Principal 展示名称 */
    public @Nullable String getPrincipalDisplayName() {
        return principalDisplayName;
    }

    /** @return 稳定操作编码 */
    public @NotNull String getOperation() {
        return operation;
    }

    /** @return 可选目标 Resource 类型 */
    public @Nullable String getTargetType() {
        return targetType;
    }

    /** @return 可选目标 Resource 标识 */
    public @Nullable String getTargetId() {
        return targetId;
    }

    /** @return `SUCCESS`、`DENIED` 或 `FAILED` */
    public @NotNull String getResult() {
        return result;
    }

    /** @return 可选 payload 持久化类型 */
    public @Nullable String getPayloadType() {
        return payloadType;
    }

    /** @return 可选 payload 版本 */
    public @Nullable Integer getPayloadVersion() {
        return payloadVersion;
    }

    /** @return 可选且已脱敏的 payload JSON */
    public @Nullable String getPayloadJson() {
        return payloadJson;
    }

    /** @return 可选 HTTP 方法 */
    public @Nullable String getHttpMethod() {
        return httpMethod;
    }

    /** @return 可选且不含查询参数的 HTTP 路径 */
    public @Nullable String getHttpPath() {
        return httpPath;
    }

    /** @return 可选直连客户端地址 */
    public @Nullable String getClientIp() {
        return clientIp;
    }

    /** @return 可选且已清理的 User-Agent */
    public @Nullable String getUserAgent() {
        return userAgent;
    }

    /** @return 操作发生时间 */
    public @NotNull Instant getOccurredAt() {
        return occurredAt;
    }
}
