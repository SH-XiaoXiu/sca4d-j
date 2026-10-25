package com.sca4d.core.api.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.exception.CoreApiException;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/** 使用项目统一 ObjectMapper 编解码一个具有固定 TreeCode 和版本的 Message 类型。 */
public class JacksonMessageCodec<M extends Message> implements MessageCodec<M> {

    private final @NotNull ObjectMapper json;

    private final @NotNull Class<M> valueType;

    private final @NotNull TreeCode code;

    private final int version;

    /** 创建使用指定 JSON 映射器的消息编解码器。 */
    public JacksonMessageCodec(
            @NotNull TreeCode code,
            int version,
            @NotNull Class<M> valueType,
            @NotNull ObjectMapper json) {
        this.code = Objects.requireNonNull(code, "消息编码不能为空");
        if (version <= 0) {
            throw new IllegalArgumentException("消息协议版本必须为正数");
        }
        this.version = version;
        this.valueType = Objects.requireNonNull(valueType, "消息 Java 类型不能为空");
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return code;
    }

    @Override
    public int version() {
        return version;
    }

    @Override
    public @NotNull Class<M> valueType() {
        return valueType;
    }

    @Override
    public byte @NotNull [] encode(@NotNull M value) {
        try {
            return json.writeValueAsBytes(Objects.requireNonNull(value, "消息不能为空"));
        } catch (JsonProcessingException exception) {
            throw new CoreApiException("消息无法编码: " + code, exception);
        }
    }

    @Override
    public @NotNull M decode(byte @NotNull [] wire) {
        try {
            return json.readValue(Objects.requireNonNull(wire, "消息编码不能为空"), valueType);
        } catch (IOException exception) {
            throw new IllegalArgumentException("消息无法解码: " + code, exception);
        }
    }
}
