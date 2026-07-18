package com.sca4d.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 在字典 JSONB 多语言标签与不可变 DictionaryLabels 之间转换。 */
@Component
public class DictionaryLabelCodec {

    private static final TypeReference<Map<String, String>> LABEL_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * 创建复用项目全局 Jackson 契约的字典标签编解码器。
     *
     * @param objectMapper 项目共用 JSON 映射器
     */
    public DictionaryLabelCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 JSONB 文本恢复多语言标签。
     *
     * @param labelsJson 数据库返回的 JSON 对象文本
     * @return 清理空白值后的不可变标签集合
     * @throws IllegalStateException 内容不是字符串标签对象时抛出
     */
    public @NotNull DictionaryLabels decode(@Nullable String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            return new DictionaryLabels(Map.of());
        }
        try {
            return new DictionaryLabels(objectMapper.readValue(labelsJson, LABEL_MAP));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("字典多语言标签不是合法 JSON 对象", exception);
        }
    }

    /**
     * 把经过业务校验的标签集合编码为 JSONB 写入文本。
     *
     * @param labels 待持久化的多语言标签
     * @return JSON 对象文本
     * @throws IllegalStateException 全局 JSON 映射器无法编码字符串映射时抛出
     */
    public @NotNull String encode(@NotNull Map<String, String> labels) {
        try {
            return objectMapper.writeValueAsString(labels);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("字典多语言标签编码失败", exception);
        }
    }
}
