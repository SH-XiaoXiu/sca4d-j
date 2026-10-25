package com.sca4d.core.spring.security;

import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.spring.exception.CoreSpringException;
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
     * <p>启动时解码所有配置的 key；active key 不存在于配置中时立即失败，不回退到其他 key。
     *
     * @param properties 当前部署的密钥环配置
     * @throws NullPointerException 配置对象为空时抛出
     * @throws IllegalStateException active key 缺失，或任一 key 不是有效 Base64 或不是 32 字节时抛出
     * @throws NullPointerException 配置的 key-id 或密钥为 null 时抛出
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

    /**
     * 把一个配置项解码为 AES-256-GCM 加密器。
     *
     * @param keyId 配置中的 key-id
     * @param encodedKey Base64 编码的 32 字节密钥
     * @return 绑定该 key-id 的加密器
     * @throws IllegalStateException 密钥为 null 或 Base64 解码、密钥长度验证失败时抛出
     */
    private static SecretCipher cipher(String keyId, String encodedKey) {
        if (encodedKey == null) {
            throw new IllegalStateException("Secret key 不能为空: " + keyId);
        }
        try {
            return new AesGcmSecretCipher(keyId, Base64.getDecoder().decode(encodedKey));
        } catch (IllegalArgumentException exception) {
            throw new CoreSpringException("Secret key 配置无效: " + keyId, exception);
        }
    }
}
