package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;

/** 同时保留原始信封元数据和类型化展示载荷的审计读取结果。 */
public class AuditDecodedEnvelope {

    private final AuditEnvelope envelope;
    private final AuditPayload payload;

    /**
     * 创建一次审计详情解码结果。
     *
     * @param envelope 包含操作者、请求、目标和版本元数据的原始信封
     * @param payload 已按类型版本解码或回退为 RawAuditPayload 的展示载荷
     */
    public AuditDecodedEnvelope(@NotNull AuditEnvelope envelope, @NotNull AuditPayload payload) {
        this.envelope = envelope;
        this.payload = payload;
    }

    /** @return 保留持久化元数据和原始 JSON 载荷节点的审计信封 */
    public @NotNull AuditEnvelope getEnvelope() {
        return envelope;
    }

    /** @return 类型化载荷，未知类型或版本时为 RawAuditPayload */
    public @NotNull AuditPayload getPayload() {
        return payload;
    }
}
