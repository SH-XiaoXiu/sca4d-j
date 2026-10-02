package com.sca4d.core.spring.setting;

import com.sca4d.core.api.codec.SecretCodec;
import com.sca4d.core.api.codec.VersionedCodecCatalog;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 为一个设置所属和稳定编码选择当前写入及已登记的读取 Codec。 */
public class SettingSecretCodecCatalog {

    private final @NotNull VersionedCodecCatalog<byte[], EncryptedSecret> codecs;

    /** 创建绑定密钥、owner 和设置编码的版本目录；未登记的读取版本直接报错。 */
    public SettingSecretCodecCatalog(
            @NotNull SecretCipher cipher,
            @NotNull SettingOwner owner,
            @NotNull String key) {
        SettingSecretCodecV2 v2 = new SettingSecretCodecV2(cipher, owner, key);
        this.codecs = new VersionedCodecCatalog<>(v2, List.of(v2));
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
