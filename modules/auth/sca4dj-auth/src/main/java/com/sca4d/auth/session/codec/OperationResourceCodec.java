package com.sca4d.auth.session.codec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.authentication.principalsession.ResourceCodec;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/** 在 Session wire 中编解码 {@link Operation} 标识。 */
public final class OperationResourceCodec implements ResourceCodec<Operation> {

    private final @NotNull ObjectMapper objectMapper;

    /**
     * 使用 Session wire 专用 JSON 映射器创建 Codec。
     *
     * @param objectMapper 读写 Operation payload 的 JSON 映射器
     */
    public OperationResourceCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public @NotNull TreeCode type() {
        return TreeCode.of("CORE.OPERATION");
    }

    @Override
    public @NotNull Class<Operation> valueType() {
        return Operation.class;
    }

    @Override
    public byte @NotNull [] encode(@NotNull Operation value) {
        try {
            return objectMapper.writeValueAsBytes(new Payload(Objects.requireNonNull(value, "Operation 不能为空").id()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Operation payload 编码失败", exception);
        }
    }

    @Override
    public @NotNull Operation decode(byte @NotNull [] payload) {
        try {
            Payload value = objectMapper.readValue(
                    Objects.requireNonNull(payload, "Operation payload 不能为空"),
                    Payload.class);
            return new Operation(value.id);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Operation payload 解码失败", exception);
        }
    }

    /** 保存 Session JSON 中的 Operation 标识。 */
    private static final class Payload {
        @JsonProperty("id")
        private String id;

        /** 供 Jackson 创建反序列化中间对象。 */
        @JsonCreator
        private Payload() {
        }

        /** 保存待编码的 Operation 标识。 */
        private Payload(@NotNull String id) {
            this.id = id;
        }
    }
}
