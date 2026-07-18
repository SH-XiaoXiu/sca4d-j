package com.sca4d.im.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.InternalServiceException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * IM 消息扩展载荷编解码器。
 */
@Component
@RequiredArgsConstructor
public class ImPayloadCodec {

    private final @NotNull ObjectMapper objectMapper;

    /**
     * 把扩展载荷编码为 JSON 字符串。
     *
     * @param payload 扩展载荷
     * @return JSON 字符串
     */
    public @NotNull String encode(@Nullable Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new InternalServiceException("IM 消息载荷序列化失败", ex);
        }
    }
}
