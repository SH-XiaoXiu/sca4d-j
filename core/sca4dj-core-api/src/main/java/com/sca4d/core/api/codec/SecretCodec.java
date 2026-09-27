package com.sca4d.core.api.codec;

import com.sca4d.core.api.security.EncryptedSecret;

/** 把已经绑定加密密钥和用途的秘密值与密文互相转换，并声明密文结构版本。 */
public interface SecretCodec<V> extends VersionedCodec<V, EncryptedSecret> {
}
