package com.sca4d.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.config.properties.Sca4djSecretKeyringProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** 验证密钥轮换读取、用途绑定、随机 nonce 和配置失败关闭。 */
class SecretKeyringTest {

    @Test
    void shouldEncryptWithRandomNonceAndDecryptWithMatchingPurpose() {
        String key = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        Sca4djSecretKeyringProperties properties = new Sca4djSecretKeyringProperties();
        properties.setActiveKeyId("current");
        properties.setKeys(Map.of("current", key));
        SecretKeyring keyring = new SecretKeyring(properties);

        EncryptedSecret first = keyring.encrypt("smtp.sender.123.password", "secret-password");
        EncryptedSecret second = keyring.encrypt("smtp.sender.123.password", "secret-password");

        assertThat(first.getKeyId()).isEqualTo("current");
        assertThat(first.getNonce()).hasSize(12).isNotEqualTo(second.getNonce());
        assertThat(first.getCiphertext()).isNotEqualTo(second.getCiphertext());
        assertThat(new String(first.getCiphertext(), StandardCharsets.UTF_8))
                .doesNotContain("secret-password");
        assertThat(keyring.decrypt("smtp.sender.123.password", first)).isEqualTo("secret-password");
    }

    @Test
    void shouldReadCiphertextCreatedBeforeActiveKeyRotation() {
        String oldKey = Base64.getEncoder().encodeToString(new byte[32]);
        byte[] newKeyBytes = new byte[32];
        Arrays.fill(newKeyBytes, (byte) 7);
        String newKey = Base64.getEncoder().encodeToString(newKeyBytes);
        Sca4djSecretKeyringProperties oldProperties = new Sca4djSecretKeyringProperties();
        oldProperties.setActiveKeyId("old");
        oldProperties.setKeys(Map.of("old", oldKey));
        EncryptedSecret encrypted = new SecretKeyring(oldProperties)
                .encrypt("smtp.sender.123.password", "secret-password");
        Sca4djSecretKeyringProperties rotatedProperties = new Sca4djSecretKeyringProperties();
        rotatedProperties.setActiveKeyId("new");
        rotatedProperties.setKeys(Map.of("old", oldKey, "new", newKey));

        assertThat(new SecretKeyring(rotatedProperties).decrypt("smtp.sender.123.password", encrypted))
                .isEqualTo("secret-password");
    }

    @Test
    void shouldRejectCiphertextUsedForAnotherPurpose() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        Sca4djSecretKeyringProperties properties = new Sca4djSecretKeyringProperties();
        properties.setActiveKeyId("current");
        properties.setKeys(Map.of("current", key));
        SecretKeyring keyring = new SecretKeyring(properties);
        EncryptedSecret encrypted = keyring.encrypt("smtp.sender.123.password", "secret-password");

        assertThatThrownBy(() -> keyring.decrypt("smtp.sender.456.password", encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Secret keyring 密文无法验证或解密");
    }

    @Test
    void shouldRejectMissingActiveKeyAndWrongKeyLength() {
        Sca4djSecretKeyringProperties missing = new Sca4djSecretKeyringProperties();
        missing.setActiveKeyId("current");
        missing.setKeys(Map.of("old", Base64.getEncoder().encodeToString(new byte[32])));
        assertThatThrownBy(() -> new SecretKeyring(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Secret keyring 缺少 active key: current");

        Sca4djSecretKeyringProperties shortKey = new Sca4djSecretKeyringProperties();
        shortKey.setActiveKeyId("current");
        shortKey.setKeys(Map.of("current", Base64.getEncoder().encodeToString(new byte[31])));
        assertThatThrownBy(() -> new SecretKeyring(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Secret keyring 密钥必须是 32 字节: current");
    }

    @Test
    void shouldProtectEncryptedBytesFromCallerMutation() {
        byte[] nonce = new byte[] {1, 2, 3};
        byte[] ciphertext = new byte[] {4, 5, 6};
        EncryptedSecret encrypted = new EncryptedSecret("current", nonce, ciphertext);

        nonce[0] = 9;
        ciphertext[0] = 9;
        encrypted.getNonce()[1] = 9;
        encrypted.getCiphertext()[1] = 9;

        assertThat(encrypted.getNonce()).containsExactly(1, 2, 3);
        assertThat(encrypted.getCiphertext()).containsExactly(4, 5, 6);
    }
}
