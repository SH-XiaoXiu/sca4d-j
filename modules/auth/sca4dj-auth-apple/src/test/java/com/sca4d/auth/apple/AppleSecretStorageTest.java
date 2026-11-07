package com.sca4d.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import com.sca4d.auth.federation.FederatedSecretCryptography;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.spring.security.ConfiguredSecretKeyring;
import com.sca4d.core.spring.security.SecretKeyringProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** 验证 Apple 签名材料经 {@link FederatedSecretCryptography} 保存后只有密文，读取后仍能签发 client_secret。 */
class AppleSecretStorageTest {

    @Test
    @DisplayName("签名材料加密保存后密文不含私钥，解密后可以签发 client_secret")
    void materialIsStoredEncryptedAndStillSigns() throws Exception {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        SecretKeyringProperties properties = new SecretKeyringProperties();
        properties.setActiveKeyId("current");
        properties.setKeys(Map.of("current", Base64.getEncoder().encodeToString(key)));
        FederatedSecretCryptography secrets = new FederatedSecretCryptography(new ConfiguredSecretKeyring(properties));
        String material = AppleTestMaterial.material(
                AppleTestMaterial.TEAM_ID,
                AppleTestMaterial.KEY_ID,
                AppleTestMaterial.PRIVATE_KEY_PEM);

        EncryptedSecret stored = secrets.encrypt(1L, material);
        String restored = secrets.decrypt(1L, stored);

        String ciphertext = new String(stored.ciphertext(), StandardCharsets.ISO_8859_1);
        assertThat(ciphertext)
                .doesNotContain(AppleTestMaterial.PRIVATE_KEY_BODY.substring(0, 32))
                .doesNotContain(AppleTestMaterial.TEAM_ID)
                .doesNotContain(AppleTestMaterial.KEY_ID);
        String jwt = new AppleClientSecretGenerator(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
                .generate(AppleTestMaterial.configuration(restored));
        assertThat(SignedJWT.parse(jwt).getJWTClaimsSet().getIssuer()).isEqualTo(AppleTestMaterial.TEAM_ID);
    }
}
