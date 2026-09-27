package com.sca4d.core.spring.setting;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 读取设置编码迁移前的密文用途，写入始终使用当前设置编码；不迁移密钥材料。 */
public final class SettingSecretCodecAdapter {
    private SettingSecretCodecAdapter() {
    }

    /** 先用当前编码尝试，认证失败后用旧点式编码读取历史密文。 */
    public static byte @NotNull [] decode(
            @NotNull SecretCipher cipher,
            @NotNull SettingOwner owner,
            @NotNull String currentKey,
            @NotNull String legacyKey,
            int version,
            @NotNull EncryptedSecret value) {
        Objects.requireNonNull(cipher);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(value);
        try {
            return new SettingSecretCodecCatalog(cipher, owner, currentKey).select(version).decode(value);
        } catch (RuntimeException currentFailure) {
            if (currentKey.equals(legacyKey))
                throw currentFailure;
            return new SettingSecretCodecCatalog(cipher, owner, legacyKey).select(version).decode(value);
        }
    }

    /** 使用当前设置编码生成新密文，密钥标识和密钥材料保持由 SecretKeyring 决定。 */
    public static @NotNull EncryptedSecret encode(
            @NotNull SecretCipher cipher,
            @NotNull SettingOwner owner,
            @NotNull String currentKey,
            byte @NotNull [] value) {
        return new SettingSecretCodecCatalog(cipher, owner, currentKey).current().encode(value);
    }

    /** 使用同一把密钥把旧用途密文转换为新用途密文，keyId 不发生变化。 */
    public static @NotNull EncryptedSecret rebind(
            @NotNull SecretCipher cipher,
            @NotNull SettingOwner owner,
            @NotNull String currentKey,
            @NotNull String legacyKey,
            int version,
            @NotNull EncryptedSecret value) {
        return encode(cipher, owner, currentKey, decode(cipher, owner, currentKey, legacyKey, version, value));
    }
}
