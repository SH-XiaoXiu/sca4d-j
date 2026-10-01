package com.sca4d.core.spring.security;

import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 从部署配置构造 active key 与可解密历史 key 的 AES-256-GCM 密钥环。 */
public class ConfiguredSecretKeyring implements SecretKeyring {

    private final SecretCipher activeCipher;

    private final Map<String, SecretCipher> ciphers;

    /**
     * 从部署配置创建密钥环。
     *
     * @param properties 当前部署的密钥环配置
     */
    public ConfiguredSecretKeyring(@NotNull SecretKeyringProperties properties) {
        SecretKeyringProperties checked = Objects.requireNonNull(properties, "Secret keyring 配置不能为空");
        String activeKeyId = checked.getActiveKeyId();
        Map<String, SecretCipher> parsed = new LinkedHashMap<>();
        checked.getKeys().forEach((keyId, encodedKey) -> parsed.put(keyId, cipher(keyId, encodedKey)));
        SecretCipher active = parsed.get(activeKeyId);
        if (active == null) {
            throw new IllegalStateException("Secret keyring 缺少 active key: " + activeKeyId);
        }
        this.activeCipher = active;
        this.ciphers = Map.copyOf(parsed);
    }

    @Override
    public @NotNull SecretCipher activeCipher() {
        return activeCipher;
    }

    @Override
    public @NotNull Optional<SecretCipher> findCipher(@NotNull String keyId) {
        return Optional.ofNullable(ciphers.get(Objects.requireNonNull(keyId, "Secret key-id 不能为空")));
    }

    private static SecretCipher cipher(String keyId, String encodedKey) {
        if (encodedKey == null) {
            throw new IllegalStateException("Secret key 不能为空: " + keyId);
        }
        try {
            return new AesGcmSecretCipher(keyId, Base64.getDecoder().decode(encodedKey));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Secret key 配置无效: " + keyId, exception);
        }
    }
}
