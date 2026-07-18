package com.sca4d.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 审计详情 JSON 的版本化持久化信封，与操作日志索引列共同组成完整记录。 */
public class AuditEnvelope {

    private int schemaVersion = 1;
    private String payloadType;
    private int payloadVersion = 1;
    private AuditActor actor;
    private AuditTarget target;
    private AuditRequest request;
    private String summaryKey;
    private List<Object> summaryArgs = List.of();
    private JsonNode payload;
    private AuditError error;

    /** @return 审计信封结构版本 */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @param schemaVersion 审计信封结构版本 */
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /** @return 载荷持久化类型，历史空详情时为 null */
    public @Nullable String getPayloadType() {
        return payloadType;
    }

    /** @param payloadType 载荷持久化类型 */
    public void setPayloadType(@Nullable String payloadType) {
        this.payloadType = payloadType;
    }

    /** @return 载荷 JSON 结构版本 */
    public int getPayloadVersion() {
        return payloadVersion;
    }

    /** @param payloadVersion 载荷 JSON 结构版本 */
    public void setPayloadVersion(int payloadVersion) {
        this.payloadVersion = payloadVersion;
    }

    /** @return 操作发生时的执行主体快照，历史记录缺失时为 null */
    public @Nullable AuditActor getActor() {
        return actor;
    }

    /** @param actor 操作发生时的执行主体快照 */
    public void setActor(@Nullable AuditActor actor) {
        this.actor = actor;
    }

    /** @return 被操作资源，操作没有明确目标时为 null */
    public @Nullable AuditTarget getTarget() {
        return target;
    }

    /** @param target 被操作资源，操作没有明确目标时使用 null */
    public void setTarget(@Nullable AuditTarget target) {
        this.target = target;
    }

    /** @return 操作发生时的请求摘要，历史记录缺失时为 null */
    public @Nullable AuditRequest getRequest() {
        return request;
    }

    /** @param request 操作发生时的请求摘要 */
    public void setRequest(@Nullable AuditRequest request) {
        this.request = request;
    }

    /** @return 审计摘要消息键，历史记录尚未迁移时为 null */
    public @Nullable String getSummaryKey() {
        return summaryKey;
    }

    /** @param summaryKey 在全部支持语言资源中存在的固定摘要消息键 */
    public void setSummaryKey(@Nullable String summaryKey) {
        this.summaryKey = summaryKey;
    }

    /** @return 保持消息模板顺序且不包含秘密的摘要参数 */
    public List<Object> getSummaryArgs() {
        return summaryArgs;
    }

    /** @param summaryArgs 摘要模板参数；历史记录缺失时使用空集合 */
    public void setSummaryArgs(@Nullable List<Object> summaryArgs) {
        this.summaryArgs = summaryArgs == null ? List.of() : List.copyOf(summaryArgs);
    }

    /** @return 已脱敏的载荷 JSON，历史空详情时为 null */
    public @Nullable JsonNode getPayload() {
        return payload;
    }

    /** @param payload 已脱敏或已替换为截断标记的载荷 JSON */
    public void setPayload(@Nullable JsonNode payload) {
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
}
