package com.sca4d.auth.passkey.config;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/** 配置 WebAuthn Relying Party 和允许完成 ceremony 的 Origin。 */
@ConfigurationProperties("sca4dj.auth.passkey")
public final class PasskeyProperties {

    private String relyingPartyId = "localhost";

    private String relyingPartyName = "Sca4d-J";

    private Set<String> origins = new LinkedHashSet<>(Set.of("http://localhost:5173"));

    /** 创建使用本地开发默认值的 Passkey 配置。 */
    public PasskeyProperties() {
    }

    /** 返回 WebAuthn RP ID。 */
    public @NotNull String getRelyingPartyId() {
        return relyingPartyId;
    }

    /** 设置 WebAuthn RP ID，生产环境必须与站点有效域一致。 */
    public void setRelyingPartyId(String relyingPartyId) {
        this.relyingPartyId = TextUtils.requireText(relyingPartyId, "Passkey RP ID");
    }

    /** 返回浏览器展示的 Relying Party 名称。 */
    public @NotNull String getRelyingPartyName() {
        return relyingPartyName;
    }

    /** 设置浏览器展示的 Relying Party 名称。 */
    public void setRelyingPartyName(String relyingPartyName) {
        this.relyingPartyName = TextUtils.requireText(relyingPartyName, "Passkey RP 名称");
    }

    /** 返回允许完成 WebAuthn ceremony 的精确 Origin 集合。 */
    public @NotNull Set<String> getOrigins() {
        return Set.copyOf(origins);
    }

    /** 设置允许的精确 Origin 集合。 */
    public void setOrigins(Set<String> origins) {
        if (origins == null
                || origins.isEmpty()
                || origins.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Passkey Origin 至少包含一个非空值");
        }
        this.origins = new LinkedHashSet<>(origins);
    }

}
