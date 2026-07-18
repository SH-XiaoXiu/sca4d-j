package com.sca4d.common.security;

import com.sca4d.config.properties.Sca4djSecretKeyringProperties;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 使用配置密钥集合加密和解密 SMTP、Provider 与 TOTP 秘密。 */
public final class SecretKeyring {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String KEY_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
    private static final String PURPOSE_PATTERN = "[a-z][a-z0-9.-]{0,127}";

    private final String activeKeyId;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom random = new SecureRandom();

    /**
     * 解析全部 Base64 AES-256 密钥，并确认 active key 可以用于新密文。
     *
     * @param properties 当前部署注入的 key-id 与密钥内容
     * @throws IllegalStateException 密钥标识、Base64 内容或 AES 长度无效时抛出
     */
    public SecretKeyring(@NotNull Sca4djSecretKeyringProperties properties) {
        if (properties.getActiveKeyId() == null
                || !properties.getActiveKeyId().matches(KEY_ID_PATTERN)) {
            throw new IllegalStateException("Secret keyring active-key-id 无效");
        }
        Map<String, SecretKeySpec> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.getKeys().entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches(KEY_ID_PATTERN)) {
                throw new IllegalStateException("Secret keyring key-id 无效");
            }
            if (entry.getValue() == null) {
                throw new IllegalStateException("Secret keyring 密钥不能为空: " + entry.getKey());
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(entry.getValue());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Secret keyring 密钥不是合法 Base64: " + entry.getKey(), exception);
            }
            if (key.length != KEY_BYTES) {
                throw new IllegalStateException("Secret keyring 密钥必须是 32 字节: " + entry.getKey());
            }
            parsed.put(entry.getKey(), new SecretKeySpec(key, KEY_ALGORITHM));
        }
        if (!parsed.containsKey(properties.getActiveKeyId())) {
            throw new IllegalStateException("Secret keyring 缺少 active key: " + properties.getActiveKeyId());
        }
        activeKeyId = properties.getActiveKeyId();
        keys = Map.copyOf(parsed);
    }

    /**
     * 使用 active key 和随机 nonce 加密 SMTP 密码、Provider 密钥或 TOTP 密钥。
     *
     * <p>{@code purpose} 进入 GCM additional authenticated data，并应包含所属资源 ID；数据库中的密文即使被换到其他字段或记录，也不能通过解密。
     *
     * @param purpose 调用方维护的开放用途编码，例如 {@code smtp.sender.123.password}
     * @param plaintext 不写入日志、审计或响应的明文
     * @return 可以分别写入 key-id、nonce 和 ciphertext 列的结果
     */
    public @NotNull EncryptedSecret encrypt(
            @NotNull String purpose, @NotNull String plaintext) {
        if (!purpose.matches(PURPOSE_PATTERN)) {
            throw new IllegalArgumentException("Secret keyring purpose 无效");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    activeKeyId,
                    nonce,
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret keyring 加密失败", exception);
        }
    }

    /**
     * 按密文保存的 key-id 解密敏感信息，旧密钥保留在配置中即可读取轮换前的数据。
     *
     * @param purpose 必须与加密时使用的用途和所属资源 ID 完全一致
     * @param encrypted 从数据库 key-id、nonce 和 ciphertext 列恢复的内容
     * @return 只供当前外部协议调用使用的明文
     * @throws IllegalStateException key-id 已移除、密文被修改或用途不匹配时抛出
     */
    public @NotNull String decrypt(
            @NotNull String purpose, @NotNull EncryptedSecret encrypted) {
        if (!purpose.matches(PURPOSE_PATTERN)) {
            throw new IllegalArgumentException("Secret keyring purpose 无效");
        }
        SecretKeySpec key = keys.get(encrypted.getKeyId());
        if (key == null) {
            throw new IllegalStateException("Secret keyring 缺少解密密钥: " + encrypted.getKeyId());
        }
        byte[] nonce = encrypted.getNonce();
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalStateException("Secret keyring nonce 必须是 12 字节");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted.getCiphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret keyring 密文无法验证或解密", exception);
        }
    }

}
