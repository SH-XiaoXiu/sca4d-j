package com.sca4d.core.api.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

/**
 * 为需要强类型历史读取的审计载荷声明稳定类型和 JSON 版本。
 *
 * @param <P> 当前编解码器精确匹配的载荷类型
 */
public interface AuditPayloadCodec<P extends AuditPayload> {

    /**
     * 返回不随 Java 类名变化的持久化类型。
     *
     * @return 非空白载荷类型
     */
    @NotNull
    String payloadType();

    /**
     * 返回当前 JSON 结构版本。
     *
     * @return 从 1 开始的正整数
     */
    default int version() {
        return 1;
    }

    /**
     * 返回写入时精确匹配的载荷类。
     *
     * @return 当前编解码器负责的载荷类
     */
    @NotNull
    Class<P> payloadClass();

    /**
     * 把载荷转换成尚未脱敏的 JSON。
     *
     * @param objectMapper Audit 模块使用的 JSON 映射器
     * @param payload 与 payloadClass 精确匹配的载荷
     * @return 待脱敏和保存的 JSON
     */
    default @NotNull JsonNode encode(@NotNull ObjectMapper objectMapper, @NotNull P payload) {
        return objectMapper.valueToTree(payload);
    }

    /**
     * 把对应类型和版本的历史 JSON 还原成强类型载荷。
     *
     * @param objectMapper Audit 模块使用的 JSON 映射器
     * @param payload 已脱敏保存的历史 JSON
     * @return 解码后的载荷
     */
    default @NotNull P decode(@NotNull ObjectMapper objectMapper, @NotNull JsonNode payload) {
        return objectMapper.convertValue(payload, payloadClass());
    }
}
