package com.sca4d.core.api.security;

/** 使用指定密钥加解密秘密值。 */
public interface SecretCipher {

    /**
     * 返回当前密钥标识。
     *
     * @return 当前密钥标识
     */
    String keyId();

    /**
     * 加密明文。
     *
     * @param purpose 防止同一密文被用于其他用途的上下文标识
     * @param plaintext 仅在本次调用中使用的明文
     * @return 加密结果
     */
    EncryptedSecret encrypt(String purpose, byte[] plaintext);

    /**
     * 解密密文。
     *
     * @param purpose 必须与加密时一致的上下文标识
     * @param secret 待解密的加密结果
     * @return 明文副本
     */
    byte[] decrypt(String purpose, EncryptedSecret secret);
}
