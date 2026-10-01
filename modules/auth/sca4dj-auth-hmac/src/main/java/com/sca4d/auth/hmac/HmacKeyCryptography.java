package com.sca4d.auth.hmac;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** 使用 SecretKeyring 按 Account 和版本绑定 MAC Key 密文。 */
public class HmacKeyCryptography {

    private final @NotNull SecretKeyring keyring;

    /** @param keyring 应用保存历史解密密钥的 Keyring */
    public HmacKeyCryptography(@NotNull SecretKeyring keyring) {
        this.keyring = Objects.requireNonNull(keyring, "SecretKeyring 不能为空");
    }

    /** 加密准备写入指定 Account 下一版本的原始 Key。 */
    public @NotNull EncryptedSecret encrypt(long accountId, long version, @NotNull String key) {
        byte[] bytes = Objects.requireNonNull(key, "HMAC Key 不能为空").getBytes(StandardCharsets.UTF_8);
        try {
            return keyring.activeCipher().encrypt(purpose(accountId, version), bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /** 解密 Credential 当前 Key，并由调用方负责尽快清零。 */
    public byte @NotNull [] decrypt(@NotNull HmacCredential credential) {
        HmacCredential checked = Objects.requireNonNull(credential, "HMAC Credential 不能为空");
        EncryptedSecret encrypted = checked.encryptedKey();
        SecretCipher cipher = keyring.findCipher(encrypted.keyId())
                .orElseThrow(() -> new IllegalStateException("HMAC Credential 使用的 key-id 不存在"));
        return cipher.decrypt(purpose(checked.accountId(), checked.version().value()), encrypted);
    }

    /** 返回防止密文跨 Account 或版本替换的加密用途。 */
    private static @NotNull String purpose(long accountId, long version) {
        return "auth.hmac.account." + accountId + ".version." + version;
    }
}
