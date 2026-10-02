package com.sca4d.core.spring.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 验证当前设置密文绑定 owner，不能由其他 owner 解密。 */
class SettingSecretCodecTest {

    private static final String KEY = "auth.key";

    private static final SettingOwner OWNER = new SettingOwner(
            TreeCode.of("IAM/ACCOUNT"),
            "123");

    @Test
    @DisplayName("V2 purpose 绑定 owner 类型、owner ID 和 key")
    void bindsV2PurposeToOwner() {
        FakeCipher cipher = new FakeCipher();
        SettingSecretCodecV2 codec = new SettingSecretCodecV2(cipher, OWNER, KEY);

        EncryptedSecret encrypted = codec.encode(bytes());

        assertThat(codec.version()).isEqualTo(2);
        assertThat(cipher.lastPurpose).isEqualTo("setting:v2:11:IAM/ACCOUNT:3:123:auth.key");
        assertThat(new String(codec.decode(encrypted), StandardCharsets.UTF_8)).isEqualTo("secret");
        assertThatThrownBy(
                () -> new SettingSecretCodecV2(
                        cipher,
                        new SettingOwner(TreeCode.of("IAM/ACCOUNT"), "456"),
                        KEY).decode(encrypted))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("版本目录写入当前格式，拒绝未登记的历史格式")
    void selectsRegisteredCodecAndRejectsUnsupportedVersion() {
        SettingSecretCodecCatalog catalog = new SettingSecretCodecCatalog(new FakeCipher(), OWNER, KEY);
        EncryptedSecret encrypted = catalog.current().encode(bytes());

        assertThat(catalog.select(catalog.current().version()).decode(encrypted)).containsExactly(bytes());
        assertThatThrownBy(() -> catalog.select(1)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 返回本测试使用的 UTF-8 明文。 */
    private static byte[] bytes() {
        return "secret".getBytes(StandardCharsets.UTF_8);
    }

    /** 校验加密用途并保存测试密文，隔离真实密钥生成。 */
    private static class FakeCipher implements SecretCipher {
        private String lastPurpose;

        @Override
        public @NotNull String keyId() {
            return "test-key";
        }

        @Override
        public @NotNull EncryptedSecret encrypt(
                @NotNull String purpose,
                byte @NotNull [] plaintext) {
            lastPurpose = purpose;
            String encoded = purpose + "|"
                    + Base64.getEncoder().encodeToString(plaintext);
            return new EncryptedSecret(keyId(), encoded.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public byte @NotNull [] decrypt(
                @NotNull String purpose,
                @NotNull EncryptedSecret secret) {
            lastPurpose = purpose;
            String encoded = new String(secret.ciphertext(), StandardCharsets.UTF_8);
            String prefix = purpose + "|";
            if (!encoded.startsWith(prefix)) {
                throw new IllegalArgumentException("加密用途不匹配");
            }
            return Base64.getDecoder().decode(encoded.substring(prefix.length()));
        }
    }
}
