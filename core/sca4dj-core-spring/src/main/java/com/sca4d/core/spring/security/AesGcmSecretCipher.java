package com.sca4d.core.spring.security;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.spring.exception.CoreSpringException;
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
public class AesGcmSecretCipher implements SecretCipher {

    private static final int KEY_BYTES = 32;

    private static final int NONCE_BYTES = 12;

    private static final int TAG_BITS = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

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

    /**
     * 使用调用方提供的随机数源生成 nonce，并校验 key-id 和密钥长度。
     *
     * @param keyId 部署配置维护的密钥标识
     * @param keyBytes 精确 32 字节的 AES 密钥，构造时复制
     * @param random 每次加密生成 96-bit nonce 的随机数源
     * @throws IllegalArgumentException key-id 格式无效或密钥不是 32 字节时抛出
     */
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

    /**
     * 加密秘密明文，并把 purpose 作为 AAD 参与认证。
     *
     * @param purpose 秘密所属的业务用途，解密时必须传入相同值
     * @param plaintext 秘密明文
     * @return 包含当前 key-id 的密文，内容为 nonce 加 GCM 密文和 tag
     * @throws CoreSpringException 加密算法不可用或加密失败时抛出
     */
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
            throw new CoreSpringException("秘密值加密失败", exception);
        }
    }

    /**
     * 验证并解密由当前 key-id 加密的秘密值。
     *
     * @param purpose 加密时使用的业务用途，不一致时认证失败
     * @param secret 待解密的加密秘密
     * @return 解密后的明文字节
     * @throws IllegalArgumentException 密文的 key-id 不是当前密钥时抛出
     * @throws IllegalStateException 密文长度不足时抛出
     * @throws CoreSpringException 标签验证失败或解密失败时抛出，不返回部分明文
     */
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
            throw new CoreSpringException("加密秘密无法验证或解密", exception);
        }
    }

    /**
     * 检查 key-id 能否安全写入密文元数据。
     *
     * @param keyId 待检查的密钥标识
     * @return 通过检查的原值
     * @throws IllegalArgumentException 不符合字母数字开头且最长 64 字符的格式时抛出
     */
    private static String requireKeyId(String keyId) {
        String checked = Objects.requireNonNull(keyId, "Secret key-id 不能为空");
        if (!checked.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Secret key-id 无效");
        }
        return checked;
    }

    /**
     * 把业务用途转换为 AAD 字节。
     *
     * @param purpose 业务用途
     * @return UTF-8 编码的用途字节
     * @throws IllegalArgumentException 用途为空白时抛出
     */
    private static byte[] requirePurpose(String purpose) {
        String checked = Objects.requireNonNull(purpose, "秘密值用途不能为空");
        if (checked.isBlank()) {
            throw new IllegalArgumentException("秘密值用途不能为空");
        }
        return checked.getBytes(StandardCharsets.UTF_8);
    }
}
