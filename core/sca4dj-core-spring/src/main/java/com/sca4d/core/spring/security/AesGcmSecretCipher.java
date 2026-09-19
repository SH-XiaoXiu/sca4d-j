package com.sca4d.core.spring.security;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 使用 AES-256-GCM 和用途绑定加密秘密值。 */
public final class AesGcmSecretCipher implements SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int KEY_BYTES = 32;

    private static final int NONCE_BYTES = 12;

    private static final int TAG_BITS = 128;

    private final String keyId;

    private final SecretKeySpec key;

    private final SecureRandom random;

    /**
     * 创建使用指定 AES-256 密钥的秘密值加密器。
     *
     * @param keyId 部署配置维护的密钥标识
     * @param keyBytes 精确 32 字节的 AES 密钥
     */
    public AesGcmSecretCipher(@NotNull String keyId, byte @NotNull [] keyBytes) {
        this(keyId, keyBytes, new SecureRandom());
    }

    AesGcmSecretCipher(
            @NotNull String keyId,
            byte @NotNull [] keyBytes,
            @NotNull SecureRandom random) {
        this.keyId = requireKeyId(keyId);
        byte[] checkedKey = Objects.requireNonNull(keyBytes, "Secret key 不能为空").clone();
        if (checkedKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("Secret key 必须是 32 字节");
        }
        this.key = new SecretKeySpec(checkedKey, "AES");
        this.random = Objects.requireNonNull(random, "安全随机数生成器不能为空");
    }

    @Override
    public @NotNull String keyId() {
        return keyId;
    }

    @Override
    public @NotNull EncryptedSecret encrypt(
            @NotNull String purpose,
            byte @NotNull [] plaintext) {
        // 每条密文使用独立 96-bit nonce；存储格式固定为 nonce || ciphertext || 128-bit tag。
        // purpose 作为 AAD 参与认证但不进入密文，使同一密文不能跨业务字段搬用。
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(requirePurpose(purpose));
            byte[] encrypted = cipher.doFinal(Objects.requireNonNull(plaintext, "秘密明文不能为空"));
            return new EncryptedSecret(
                    keyId,
                    ByteBuffer.allocate(NONCE_BYTES + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("秘密值加密失败", exception);
        }
    }

    @Override
    public byte @NotNull [] decrypt(
            @NotNull String purpose,
            @NotNull EncryptedSecret secret) {
        EncryptedSecret checked = Objects.requireNonNull(secret, "加密秘密不能为空");
        if (!keyId.equals(checked.keyId())) {
            throw new IllegalArgumentException("加密秘密不属于当前密钥");
        }
        byte[] encoded = checked.ciphertext();
        if (encoded.length <= NONCE_BYTES) {
            throw new IllegalStateException("加密秘密格式无效");
        }
        try {
            // EncryptedSecret 的 ciphertext 不另存格式元数据，前 12 字节始终解释为 GCM nonce。
            // doFinal 同时验证尾部 tag；密文、purpose 或 nonce 任一变化都会失败关闭。
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(requirePurpose(purpose));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("加密秘密无法验证或解密", exception);
        }
    }

    private static String requireKeyId(String keyId) {
        String checked = Objects.requireNonNull(keyId, "Secret key-id 不能为空");
        if (!checked.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Secret key-id 无效");
        }
        return checked;
    }

    private static byte[] requirePurpose(String purpose) {
        String checked = Objects.requireNonNull(purpose, "秘密值用途不能为空");
        if (checked.isBlank()) {
            throw new IllegalArgumentException("秘密值用途不能为空");
        }
        return checked.getBytes(StandardCharsets.UTF_8);
    }
}
