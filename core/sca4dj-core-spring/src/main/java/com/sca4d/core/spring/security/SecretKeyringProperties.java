package com.sca4d.core.spring.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** 配置新密文使用的 active key 与仍可读取旧密文的 AES-256 key。 */
@ConfigurationProperties("sca4dj.secret-keyring")
public class SecretKeyringProperties {

    private String activeKeyId;

    private Map<String, String> keys = new LinkedHashMap<>();

    public SecretKeyringProperties() {
    }

    /**
     * 新密文使用的 key-id。
     *
     * @return 新密文使用的 key-id
     */
    public @Nullable String getActiveKeyId() {
        return activeKeyId;
    }

    /**
     * 设置新密文使用的 key-id。
     *
     * @param activeKeyId 新密文使用的 key-id
     */
    public void setActiveKeyId(@Nullable String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    /**
     * 返回当前 key 与历史解密 key。
     *
     * @return key-id 到 Base64 AES-256 key 的映射
     */
    public @NotNull Map<String, String> getKeys() {
        return Map.copyOf(keys);
    }

    /**
     * 设置当前 key 与历史解密 key。
     *
     * @param keys 当前 key 与仅解密历史 key 的完整映射
     */
    public void setKeys(@Nullable Map<String, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }
}
