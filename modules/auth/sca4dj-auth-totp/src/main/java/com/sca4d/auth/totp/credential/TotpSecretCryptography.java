package com.sca4d.auth.totp.credential;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 在 TOTP 内部集中选择加密密钥并拒绝未知历史 key。 */
@ApiStatus.Internal
public class TotpSecretCryptography {

    private final @NotNull SecretKeyring keyring;

    /** 创建使用当前应用 SecretKeyring 的 TOTP Secret 加解密器。 */
    public TotpSecretCryptography(@NotNull SecretKeyring keyring) {
        this.keyring = Objects.requireNonNull(keyring, "Secret keyring 不能为空");
    }

    /** 使用当前活动密钥和指定 purpose 加密 Base32 Secret。 */
    public @NotNull EncryptedSecret encrypt(@NotNull String purpose, @NotNull String secret) {
        return keyring.activeCipher()
                .encrypt(
                        purpose,
                        Objects.requireNonNull(secret, "TOTP 密钥不能为空").getBytes(StandardCharsets.UTF_8));
    }

    /** 按密文记录的 key ID 选择历史密钥并使用相同 purpose 解密 Secret。 */
    public @NotNull String decrypt(@NotNull String purpose, @NotNull EncryptedSecret secret) {
        EncryptedSecret checked = Objects.requireNonNull(secret, "TOTP 加密密钥不能为空");
        SecretCipher cipher = keyring.findCipher(checked.keyId())
                .orElseThrow(() -> new IllegalStateException("Secret keyring 缺少解密密钥: " + checked.keyId()));
        return new String(cipher.decrypt(purpose, checked), StandardCharsets.UTF_8);
    }
}
