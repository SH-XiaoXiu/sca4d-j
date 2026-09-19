package com.sca4d.core.api.security;

import java.util.Optional;

/** 选择新数据加密使用的密钥，并按密钥标识查找解密密码器。 */
public interface SecretKeyring {

    /**
     * 返回新数据加密使用的密码器。
     *
     * @return 新数据加密使用的密码器
     */
    SecretCipher activeCipher();

    /**
     * 按密钥标识查找密码器。
     *
     * @param keyId 密钥标识
     * @return 对应密码器
     */
    Optional<SecretCipher> findCipher(String keyId);
}
