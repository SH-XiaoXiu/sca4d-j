package com.sca4d.core.spring.setting;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;

/** 手工把一个 Setting 密文从指定版本转换到另一个版本，不读取或修改数据库。 */
public class SettingSecretEncryptionMigrator {

    /**
     * 把一条密文转换为目标版本，调用方负责保存返回结果。
     *
     * @param owner Setting owner
     * @param key Setting key
     * @param sourceVersion 源加密上下文版本
     * @param sourceCipher 源密钥
     * @param source 源密文
     * @param targetVersion 目标加密上下文版本
     * @param targetCipher 目标密钥
     * @return 目标版本密文
     */
    public @NotNull EncryptedSecret migrate(
            @NotNull SettingOwner owner,
            @NotNull String key,
            int sourceVersion,
            @NotNull SecretCipher sourceCipher,
            @NotNull EncryptedSecret source,
            int targetVersion,
            @NotNull SecretCipher targetCipher) {
        SettingSecretCodecCatalog sourceCatalog = new SettingSecretCodecCatalog(sourceCipher, owner, key);
        SettingSecretCodecCatalog targetCatalog = new SettingSecretCodecCatalog(targetCipher, owner, key);
        byte[] plaintext = sourceCatalog.select(sourceVersion).decode(source);
        return targetCatalog.select(targetVersion).encode(plaintext);
    }
}
