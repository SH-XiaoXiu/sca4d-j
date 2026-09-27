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

class SettingSecretCodecTest {

    private static final SettingOwner OWNER = new SettingOwner(
            TreeCode.of("IAM/ACCOUNT"),
            "123");

    private static final String KEY = "auth.key";

    @Test
    @DisplayName("V1 保持旧 Setting key 加密上下文")
    void keepsV1Purpose() {
        FakeCipher cipher = new FakeCipher();
        SettingSecretCodecV1 codec = new SettingSecretCodecV1(cipher, OWNER, KEY);

        EncryptedSecret encrypted = codec.encode(bytes());

        assertThat(codec.version()).isEqualTo(1);
        assertThat(cipher.lastPurpose).isEqualTo("setting:auth.key");
        assertThat(new String(codec.decode(encrypted), StandardCharsets.UTF_8)).isEqualTo("secret");
    }

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
    @DisplayName("目录默认写 V2，同时可以读取 V1")
    void selectsCurrentAndHistoricalCodec() {
        FakeCipher cipher = new FakeCipher();
        SettingSecretCodecCatalog catalog = new SettingSecretCodecCatalog(cipher, OWNER, KEY);

        assertThat(catalog.current().version()).isEqualTo(2);
        assertThat(catalog.select(1).version()).isEqualTo(1);
        assertThat(catalog.select(2).version()).isEqualTo(2);
    }

    @Test
    @DisplayName("迁移器只转换密文，不访问数据库")
    void migratesV1CiphertextToV2() {
        FakeCipher cipher = new FakeCipher();
        SettingSecretCodecV1 v1 = new SettingSecretCodecV1(cipher, OWNER, KEY);
        EncryptedSecret source = v1.encode(bytes());

        EncryptedSecret target = new SettingSecretEncryptionMigrator().migrate(
                OWNER,
                KEY,
                1,
                cipher,
                source,
                2,
                cipher);

        assertThat(cipher.lastPurpose).isEqualTo("setting:v2:11:IAM/ACCOUNT:3:123:auth.key");
        assertThat(
                new String(
                        new SettingSecretCodecV2(cipher, OWNER, KEY).decode(target),
                        StandardCharsets.UTF_8))
                .isEqualTo("secret");
    }

    private static byte[] bytes() {
        return "secret".getBytes(StandardCharsets.UTF_8);
    }

    private static final class FakeCipher implements SecretCipher {
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
