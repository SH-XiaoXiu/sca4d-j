package com.sca4d.iam.passkey.view;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.iam.passkey.PasskeyRegistrationStart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 浏览器开始 Passkey registration 需要的资源标识、过期时间和 creation options。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyRegistrationView {
    private String registrationId;
    private Instant expiresAt;
    private JsonNode options;

    /** @return 不包含 Redis request JSON 包装的浏览器 WebAuthn options */
    public static @NotNull PasskeyRegistrationView from(
            @NotNull PasskeyRegistrationStart start, @NotNull ObjectMapper objectMapper) {
        try {
            return new PasskeyRegistrationView(
                    start.getRegistration().getRegistrationId(),
                    start.getRegistration().getExpiresAt(),
                    objectMapper.readTree(start.getBrowserOptionsJson()));
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey registration options 解析失败", exception);
        }
    }
}
