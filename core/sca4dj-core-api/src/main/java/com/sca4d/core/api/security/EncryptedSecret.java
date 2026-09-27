package com.sca4d.core.api.security;

import java.util.Arrays;
import java.util.Objects;

/** 带密钥标识且不暴露具体算法的加密结果。 */
public class EncryptedSecret {

    private final String keyId;

    private final byte[] ciphertext;

    /**
     * 创建加密结果。
     *
     * @param keyId 加密所用密钥标识
     * @param ciphertext 密文
     */
    public EncryptedSecret(String keyId, byte[] ciphertext) {
        this.keyId = Objects.requireNonNull(keyId, "密钥标识不能为空");
        this.ciphertext = Objects.requireNonNull(ciphertext, "密文不能为空").clone();
        if (this.ciphertext.length == 0) {
            throw new IllegalArgumentException("密文不能为空");
        }
    }

    /**
     * 返回加密所用密钥标识。
     *
     * @return 加密所用密钥标识
     */
    public String keyId() {
        return keyId;
    }

    /**
     * 返回密文副本。
     *
     * @return 密文副本
     */
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof EncryptedSecret secret
                        && keyId.equals(secret.keyId)
                        && Arrays.equals(ciphertext, secret.ciphertext);
    }

    @Override
    public int hashCode() {
        return 31 * keyId.hashCode() + Arrays.hashCode(ciphertext);
    }
}
