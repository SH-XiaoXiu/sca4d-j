package com.sca4d.storage.file.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.spring.security.ConfiguredSecretKeyring;
import com.sca4d.core.spring.security.SecretKeyringProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

/** 验证本地传输 Token 的动作、到期时间、密文完整性和 App 隔离名称隔离。 */
class TransferTokenServiceTest {

    static {
        AppName.publish("EXAMPLE_APP");
    }

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Test
    @DisplayName("只接受当前应用中动作匹配且尚未到期的 Token")
    void acceptsOnlyMatchingUnexpiredTokensFromTheCurrentApplication() {
        TransferTokenService service = service(NOW);
        URI uploadUrl = service.uploadUrl(101L, NOW.plusSeconds(600));
        String token = uploadUrl.getRawQuery().substring("token=".length());

        assertThat(uploadUrl.getPath()).isEqualTo("/files/transfers/uploads");
        assertThat(service.verify("PUT", token)).isEqualTo(101L);
        assertError(service, "GET", token, FileErrorCode.TRANSFER_INVALID);
        assertError(service(NOW.plusSeconds(601)), "PUT", token, FileErrorCode.UPLOAD_EXPIRED);

        int changedIndex = token.indexOf('.') + 2;
        char replacement = token.charAt(changedIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, changedIndex) + replacement + token.substring(changedIndex + 1);
        assertError(service, "PUT", tampered, FileErrorCode.TRANSFER_INVALID);
    }

    private static void assertError(
            TransferTokenService service,
            String action,
            String token,
            FileErrorCode errorCode) {
        assertThatThrownBy(() -> service.verify(action, token))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
    }

    private static TransferTokenService service(Instant now) {
        SecretKeyringProperties keyringProperties = new SecretKeyringProperties();
        keyringProperties.setActiveKeyId("test");
        keyringProperties.setKeys(Map.of("test", Base64.getEncoder().encodeToString(new byte[32])));
        return new TransferTokenService(
                new ConfiguredSecretKeyring(keyringProperties),
                URI.create("https://files.example.test"),
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
