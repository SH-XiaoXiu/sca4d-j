package com.sca4d.core.spring.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** 配置新密文使用的 active key 与仍可读取旧密文的 AES-256 key。 */
@ConfigurationProperties("sca4dj.secret-keyring")
public class SecretKeyringProperties {

    /** 配置键 {@code sca4dj.secret-keyring.active-key-id}，默认未配置；必须与 {@code keys} 中某个 key-id 相同。 */
    private String activeKeyId;

    /**
     * 配置键 {@code sca4dj.secret-keyring.keys.<key-id>}，值为 Base64 编码的 32 字节 AES-256 密钥。
     * 默认为空；历史 key 只用于解密旧密文，不再用于新加密。
     */
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
     * 返回当前 key 与历史解密 key 的不可修改副本。
     *
     * <p>映射中包含 {@code null} 键或值时，调用本方法抛出 NullPointerException。
     *
     * @return key-id 到 Base64 AES-256 key 的映射
     */
    public @NotNull Map<String, String> getKeys() {
        return Map.copyOf(keys);
    }

    /**
     * 设置当前 key 与历史解密 key。
     *
     * @param keys 当前 key 与仅解密历史 key 的完整映射；为 null 时清空为空映射，并替换之前的全部 key
     */
    public void setKeys(@Nullable Map<String, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }
}
