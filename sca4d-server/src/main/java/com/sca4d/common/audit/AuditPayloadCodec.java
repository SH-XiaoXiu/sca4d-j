package com.sca4d.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

/**
 * 审计场景载荷的版本化编解码扩展点。
 *
 * <p>持久化类型和版本构成长期读取契约；变更载荷结构时新增版本 Codec，不覆盖已经落库版本的解释方式。
 *
 * @param <P> 由当前 Codec 精确匹配的审计场景载荷类型
 */
public interface AuditPayloadCodec<P extends AuditPayload> {

    /**
     * 返回跨 Java 类重命名保持固定的持久化类型。
     *
     * @return 不含首尾空白的类型编码
     */
    @NotNull
    String payloadType();

    /**
     * 返回当前载荷 JSON 结构版本。
     *
     * @return 从 1 开始递增的正整数版本
     */
    default int payloadVersion() {
        return 1;
    }

    /**
     * 返回编码阶段精确匹配的 Java 载荷类型。
     *
     * @return 当前 Codec 唯一拥有的载荷类
     */
    @NotNull
    Class<P> payloadClass();

    /**
     * 使用应用共用 JSON 契约编码载荷。
     *
     * @param objectMapper 已注册项目枚举和时间契约的全局映射器
     * @param payload 与 {@link #payloadClass()} 精确匹配的场景载荷
     * @return 尚未执行共用敏感字段脱敏的 JSON 节点
     */
    default @NotNull JsonNode encode(@NotNull ObjectMapper objectMapper, @NotNull P payload) {
        return objectMapper.valueToTree(payload);
    }

    /**
     * 使用应用共用 JSON 契约还原历史载荷。
     *
     * @param objectMapper 已注册项目枚举和时间契约的全局映射器
     * @param payload 与当前类型和版本匹配的持久化 JSON 节点
     * @return 供操作日志展示层读取的场景载荷
     */
    default @NotNull P decode(@NotNull ObjectMapper objectMapper, @NotNull JsonNode payload) {
        return objectMapper.convertValue(payload, payloadClass());
    }
}
