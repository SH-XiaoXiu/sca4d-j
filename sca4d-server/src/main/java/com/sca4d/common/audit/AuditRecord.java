package com.sca4d.common.audit;

import com.sca4d.common.i18n.MessageReference;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/** 从业务执行现场采集、等待审计持久化的完整数据快照。 */
public class AuditRecord {

    private Long id;
    private String moduleCode;
    private String actionCode;
    private AuditResult result;
    private AuditActor actor;
    private AuditTarget target;
    private AuditRequest request;
    private MessageReference summary;
    private String payloadType;
    private Integer payloadVersion;
    private AuditPayload payload;
    private AuditError error;
    private OffsetDateTime createdAt;

    /** @return 审计持久化标识，写入器尚未分配时为 null */
    public @Nullable Long getId() {
        return id;
    }

    /** @param id 审计持久化标识；预分配或重放时使用 */
    public void setId(@Nullable Long id) {
        this.id = id;
    }

    /** @return 固定模块编码，记录尚未完成采集时为 null */
    public @Nullable String getModuleCode() {
        return moduleCode;
    }

    /** @param moduleCode 固定模块编码 */
    public void setModuleCode(@Nullable String moduleCode) {
        this.moduleCode = moduleCode;
    }

    /** @return 模块内固定动作编码，记录尚未完成采集时为 null */
    public @Nullable String getActionCode() {
        return actionCode;
    }

    /** @param actionCode 模块内固定动作编码 */
    public void setActionCode(@Nullable String actionCode) {
        this.actionCode = actionCode;
    }

    /** @return SUCCESS 或 FAILED 执行结果，记录尚未完成采集时为 null */
    public @Nullable AuditResult getResult() {
        return result;
    }

    /** @param result SUCCESS 或 FAILED 执行结果 */
    public void setResult(@Nullable AuditResult result) {
        this.result = result;
    }

    /** @return 操作发生时的执行主体快照，尚未采集时为 null */
    public @Nullable AuditActor getActor() {
        return actor;
    }

    /** @param actor 操作发生时的执行主体快照 */
    public void setActor(@Nullable AuditActor actor) {
        this.actor = actor;
    }

    /** @return 被操作资源，操作不对应明确目标时为 null */
    public @Nullable AuditTarget getTarget() {
        return target;
    }

    /** @param target 被操作资源，操作不对应明确目标时使用 null */
    public void setTarget(@Nullable AuditTarget target) {
        this.target = target;
    }

    /** @return 操作发生时的请求摘要，尚未采集时为 null */
    public @Nullable AuditRequest getRequest() {
        return request;
    }

    /** @param request 操作发生时的请求摘要 */
    public void setRequest(@Nullable AuditRequest request) {
        this.request = request;
    }

    /** @return 尚未绑定请求语言的操作摘要消息引用，未提供时为 null */
    public @Nullable MessageReference getSummary() {
        return summary;
    }

    /** @param summary 由审计目录或业务场景声明的固定摘要消息引用 */
    public void setSummary(@Nullable MessageReference summary) {
        this.summary = summary;
    }

    /** @return 持久化载荷类型，尚未推断时为 null */
    public @Nullable String getPayloadType() {
        return payloadType;
    }

    /** @param payloadType 跨 Java 类重命名保持固定的载荷类型 */
    public void setPayloadType(@Nullable String payloadType) {
        this.payloadType = payloadType;
    }

    /** @return 载荷结构版本，尚未推断时为 null */
    public @Nullable Integer getPayloadVersion() {
        return payloadVersion;
    }

    /** @param payloadVersion 正整数载荷结构版本 */
    public void setPayloadVersion(@Nullable Integer payloadVersion) {
        this.payloadVersion = payloadVersion;
    }

    /** @return 场景载荷，没有场景字段时为 null */
    public @Nullable AuditPayload getPayload() {
        return payload;
    }

    /** @param payload 由场景定义的审计展示载荷 */
    public void setPayload(@Nullable AuditPayload payload) {
        this.payload = payload;
    }

    /** @return 失败异常快照，成功记录时为 null */
    public @Nullable AuditError getError() {
        return error;
    }

    /** @param error 失败异常快照，成功记录时使用 null */
    public void setError(@Nullable AuditError error) {
        this.error = error;
    }

    /** @return 业务执行完成并采集记录的时间，尚未采集时为 null */
    public @Nullable OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt 业务执行完成并采集记录的时间 */
    public void setCreatedAt(@Nullable OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
