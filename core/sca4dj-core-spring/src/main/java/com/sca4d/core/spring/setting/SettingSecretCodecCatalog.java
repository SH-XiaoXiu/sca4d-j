package com.sca4d.core.spring.setting;

import com.sca4d.core.api.codec.SecretCodec;
import com.sca4d.core.api.codec.VersionedCodecCatalog;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 为一个设置所属和稳定编码建立 V1/V2 密文 Codec 目录，并把 V2 作为当前写入版本。 */
public class SettingSecretCodecCatalog {

    private final @NotNull VersionedCodecCatalog<byte[], EncryptedSecret> codecs;

    /** 创建使用同一密钥读取或写入一个 Setting 的 V1/V2 Codec 目录。 */
    public SettingSecretCodecCatalog(
            @NotNull SecretCipher cipher,
            @NotNull SettingOwner owner,
            @NotNull String key) {
        SettingSecretCodecV1 v1 = new SettingSecretCodecV1(cipher, owner, key);
        SettingSecretCodecV2 v2 = new SettingSecretCodecV2(cipher, owner, key);
        this.codecs = new VersionedCodecCatalog<>(v2, List.of(v1, v2));
    }

    /** @return 当前写入使用的 V2 Codec */
    public @NotNull SettingSecretCodecV2 current() {
        return (SettingSecretCodecV2) codecs.current();
    }

    /** @param version 外部保存的加密上下文版本 @return 对应版本 Codec */
    public @NotNull SecretCodec<byte[]> select(int version) {
        return (SecretCodec<byte[]>) codecs.select(version);
    }
}
