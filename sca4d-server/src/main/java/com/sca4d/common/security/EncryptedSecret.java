package com.sca4d.common.security;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/** 使用指定 key-id 和 AES-GCM nonce 保存的密文。 */
public final class EncryptedSecret {

    private final String keyId;
    private final byte[] nonce;
    private final byte[] ciphertext;

    /**
     * @param keyId 加密时使用的配置密钥标识
     * @param nonce 当前密文独有的 AES-GCM nonce
     * @param ciphertext 包含 GCM authentication tag 的密文
     */
    public EncryptedSecret(
            @NotNull String keyId, byte @NotNull [] nonce, byte @NotNull [] ciphertext) {
        this.keyId = keyId;
        this.nonce = Arrays.copyOf(nonce, nonce.length);
        this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public @NotNull String getKeyId() {
        return keyId;
    }

    /** @return nonce 的副本，调用方不能修改当前加密结果 */
    public byte @NotNull [] getNonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    /** @return 密文的副本，调用方不能修改当前加密结果 */
    public byte @NotNull [] getCiphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }
}
