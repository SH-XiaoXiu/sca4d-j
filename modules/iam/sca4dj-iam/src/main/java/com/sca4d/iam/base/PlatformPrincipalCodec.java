package com.sca4d.iam.base;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/** PlatformPrincipal 的 Session wire Codec。 */
public final class PlatformPrincipalCodec implements PrincipalCodec<PlatformPrincipal> {

    /** Platform Principal 在 Session 和设置 owner 中使用的稳定类型。 */
    public static final @NotNull String TYPE = "iam.platform-principal";

    private final @NotNull ObjectMapper objectMapper;

    /**
     * 创建 PlatformPrincipal Session Codec。
     *
     * @param objectMapper 编解码 Payload 使用的 Jackson 映射器
     */
    public PlatformPrincipalCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public @NotNull String type() {
        return TYPE;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public @NotNull Class<PlatformPrincipal> valueType() {
        return PlatformPrincipal.class;
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public byte @NotNull [] encode(@NotNull PlatformPrincipal value) {
        try {
            return objectMapper.writeValueAsBytes(new Payload(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("PlatformPrincipal payload 编码失败", exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull PlatformPrincipal decode(byte @NotNull [] payload) {
        try {
            Payload value = objectMapper.readValue(payload, Payload.class);
            return new PlatformPrincipal(value.id);
        } catch (IOException exception) {
            throw new IllegalArgumentException("PlatformPrincipal payload 解码失败", exception);
        }
    }

    /** 保存 PlatformPrincipal 的 Session JSON 字段。 */
    private static final class Payload {
        @JsonProperty("id")
        private long id;

        /** 供 Jackson 创建反序列化中间对象。 */
        @JsonCreator
        private Payload() {
        }

        /** 从完整领域对象创建待编码 payload。 */
        private Payload(@NotNull PlatformPrincipal value) {
            id = value.principalId();
        }

    }
}
