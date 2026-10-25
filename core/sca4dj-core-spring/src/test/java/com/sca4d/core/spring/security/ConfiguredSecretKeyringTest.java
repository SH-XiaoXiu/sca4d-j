package com.sca4d.core.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.spring.exception.CoreSpringException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** 验证 AES-GCM 用途绑定、随机 nonce、轮换读取和配置失败关闭。 */
class ConfiguredSecretKeyringTest {

    @Test
    @DisplayName("使用随机 nonce 加密，并拒绝篡改内容或错误用途")
    void encryptsWithRandomNonceAndRejectsTamperingOrWrongPurpose() {
        ConfiguredSecretKeyring keyring = keyring("current", Map.of("current", key((byte) 1)));
        SecretCipher cipher = keyring.activeCipher();

        EncryptedSecret first = cipher.encrypt("totp.credential.1.secret", secretBytes());
        EncryptedSecret second = cipher.encrypt("totp.credential.1.secret", secretBytes());

        assertThat(first.keyId()).isEqualTo("current");
        assertThat(first.ciphertext()).hasSizeGreaterThan(12).isNotEqualTo(second.ciphertext());
        assertThat(cipher.decrypt("totp.credential.1.secret", first)).isEqualTo(secretBytes());
        assertThatThrownBy(() -> cipher.decrypt("totp.credential.2.secret", first))
                .isInstanceOf(CoreSpringException.class)
                .hasMessage("加密秘密无法验证或解密");

        byte[] tampered = first.ciphertext();
        tampered[tampered.length - 1] ^= 1;
        EncryptedSecret changed = new EncryptedSecret(first.keyId(), tampered);
        assertThatThrownBy(() -> cipher.decrypt("totp.credential.1.secret", changed))
                .isInstanceOf(CoreSpringException.class)
                .hasMessage("加密秘密无法验证或解密");
    }

    @Test
    @DisplayName("读取历史密钥，但只使用当前密钥加密新内容")
    void readsHistoricalKeyButUsesOnlyActiveKeyForNewCiphertext() {
        ConfiguredSecretKeyring oldKeyring = keyring("old", Map.of("old", key((byte) 2)));
        EncryptedSecret encrypted = oldKeyring.activeCipher()
                .encrypt("totp.credential.1.secret", secretBytes());
        ConfiguredSecretKeyring rotated = keyring(
                "current",
                Map.of("old", key((byte) 2), "current", key((byte) 3)));

        assertThat(rotated.activeCipher().keyId()).isEqualTo("current");
        assertThat(rotated.findCipher("old")).isPresent();
        assertThat(
                rotated.findCipher("old")
                        .orElseThrow()
                        .decrypt("totp.credential.1.secret", encrypted))
                .isEqualTo(secretBytes());
        assertThat(rotated.findCipher("unknown")).isEmpty();
    }

    @Test
    @DisplayName("拒绝缺失的当前密钥、非法 Base64 和错误密钥长度")
    void rejectsMissingActiveKeyMalformedBase64AndWrongKeyLength() {
        assertThatThrownBy(() -> keyring("current", Map.of("old", key((byte) 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Secret keyring 缺少 active key: current");
        assertThatThrownBy(() -> keyring("current", Map.of("current", "not-base64")))
                .isInstanceOf(CoreSpringException.class)
                .hasMessage("Secret key 配置无效: current");
        assertThatThrownBy(
                () -> keyring(
                        "current",
                        Map.of("current", Base64.getEncoder().encodeToString(new byte[31]))))
                .isInstanceOf(CoreSpringException.class)
                .hasMessage("Secret key 配置无效: current");
    }

    /** 用指定的 active key id 和 Base64 编码的密钥表构造 ConfiguredSecretKeyring，构造时即校验配置。 */
    private static ConfiguredSecretKeyring keyring(String active, Map<String, String> keys) {
        SecretKeyringProperties properties = new SecretKeyringProperties();
        properties.setActiveKeyId(active);
        properties.setKeys(keys);
        return new ConfiguredSecretKeyring(properties);
    }

    /** 生成 32 字节且每个字节都为 value 的 Base64 密钥，便于测试按 key id 区分密钥。 */
    private static String key(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }

    /** 返回固定明文 SECRET 的 UTF-8 字节，供加解密往返比较。 */
    private static byte[] secretBytes() {
        return "SECRET".getBytes(StandardCharsets.UTF_8);
    }
}
