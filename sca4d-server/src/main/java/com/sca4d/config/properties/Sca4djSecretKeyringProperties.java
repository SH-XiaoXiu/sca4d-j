package com.sca4d.config.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** SMTP、Provider 与 TOTP 加密使用的 active key 和可读取密钥集合。 */
@ConfigurationProperties(prefix = "sca4dj.secret-keyring")
public class Sca4djSecretKeyringProperties {

    /** 新密文写入的开放 key-id，由部署配置维护。 */
    private String activeKeyId;

    /** key-id 到 Base64 AES-256 密钥的配置映射；轮换期间保留仍有密文引用的旧 key。 */
    private Map<String, String> keys = new LinkedHashMap<>();

    public @Nullable String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(@Nullable String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public @NotNull Map<String, String> getKeys() {
        return keys;
    }

    /** @param keys 新配置映射；配置缺失时恢复为空集合并由 keyring 拒绝启动 */
    public void setKeys(@Nullable Map<String, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }
}
