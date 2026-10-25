package com.sca4d.core.spring.setting;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sca4d.core.api.codec.Codec;
import com.sca4d.core.api.codec.JsonCodec;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/** 通过 YAML 解析器在 JsonCodec 的 JSON 树与 YAML 文本之间转换内容，不访问文件也不启用 Java 多态类型。 */
public final class YamlCodec<T> implements Codec<T, byte[]> {
    private final JsonCodec<T> domain;

    private final ObjectMapper mapper = new ObjectMapper(
            YAMLFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 保存 JsonCodec 的字段解析规则；YAML 语法本身不决定字段含义。 */
    public YamlCodec(@NotNull JsonCodec<T> domain) {
        this.domain = Objects.requireNonNull(domain);
    }

    @Override
    public byte @NotNull [] encode(@NotNull T value) {
        try {
            return mapper.writeValueAsBytes(domain.encode(value));
        } catch (IOException failure) {
            throw new IllegalArgumentException("设置 YAML 编码失败", failure);
        }
    }

    @Override
    public @NotNull T decode(byte @NotNull [] value) {
        if (value.length > 1_048_576)
            throw new IllegalArgumentException("设置 YAML 超过 1 MiB");
        try {
            JsonNode document = mapper.readerFor(JsonNode.class).readValue(value);
            if (document == null)
                throw new IllegalArgumentException("设置 YAML 内容为空");
            return domain.decode(document);
        } catch (IOException failure) {
            throw new IllegalArgumentException("设置 YAML 格式无效", failure);
        }
    }
}
