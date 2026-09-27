package com.sca4d.auth.federation;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretKeyring;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 使用 SecretKeyring 保存和读取 Provider Client Secret。 */
public class FederatedSecretCryptography {

    private final @NotNull SecretKeyring keyring;

    /** 创建外部 Provider Secret 加密器。 */
    public FederatedSecretCryptography(@NotNull SecretKeyring keyring) {
        this.keyring = Objects.requireNonNull(keyring, "SecretKeyring 不能为空");
    }

    /** 加密 Client Secret。 */
    public @NotNull EncryptedSecret encrypt(long providerId, @NotNull String secret) {
        return keyring.activeCipher()
                .encrypt(
                        purpose(providerId),
                        Objects.requireNonNull(secret, "Client Secret 不能为空").getBytes(StandardCharsets.UTF_8));
    }

    /** 解密 Client Secret。 */
    public @NotNull String decrypt(long providerId, @NotNull EncryptedSecret encrypted) {
        EncryptedSecret checked = Objects.requireNonNull(encrypted, "加密 Secret 不能为空");
        return new String(
                keyring.findCipher(checked.keyId())
                        .orElseThrow(() -> new IllegalStateException("SecretKeyring 缺少历史密钥: " + checked.keyId()))
                        .decrypt(purpose(providerId), checked),
                StandardCharsets.UTF_8);
    }

    private static String purpose(long providerId) {
        return "auth.federation.provider." + providerId + ".client-secret";
    }
}
