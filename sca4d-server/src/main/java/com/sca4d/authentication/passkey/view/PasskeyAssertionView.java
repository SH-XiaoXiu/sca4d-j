package com.sca4d.authentication.passkey.view;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.passkey.PasskeyAssertionStart;
import com.sca4d.common.exception.InternalServiceException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 浏览器开始 Passkey assertion 需要的资源标识、过期时间和 request options。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyAssertionView {
    private String assertionId;
    private Instant expiresAt;
    private JsonNode options;

    /** @return 不包含 Redis request JSON 包装的浏览器 WebAuthn options */
    public static @NotNull PasskeyAssertionView from(
            @NotNull PasskeyAssertionStart start, @NotNull ObjectMapper objectMapper) {
        try {
            return new PasskeyAssertionView(
                    start.getAssertion().getAssertionId(),
                    start.getAssertion().getExpiresAt(),
                    objectMapper.readTree(start.getBrowserOptionsJson()));
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey assertion options 解析失败", exception);
        }
    }
}
