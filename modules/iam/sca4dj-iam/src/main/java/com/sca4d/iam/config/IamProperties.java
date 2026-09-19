package com.sca4d.iam.config;

import com.sca4d.iam.identity.UsernameAccountIdentifierNormalizer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置本地开发环境的平台身份初始化。 */
@ConfigurationProperties("sca4dj.iam.bootstrap")
public class IamProperties {

    private boolean enabled = true;

    private @NotNull String username = "admin";

    /** 创建使用本地初始化默认值的配置。 */
    public IamProperties() {
    }

    /**
     * 返回应用启动时是否确保平台账号存在。
     *
     * @return 是否初始化平台账号
     */
    @Contract(pure = true)
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置应用启动时是否确保平台账号存在。
     *
     * @param enabled 是否初始化平台账号
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回需要确保存在的平台用户名。
     *
     * @return 初始化用户名
     */
    @Contract(pure = true)
    public @NotNull String getUsername() {
        return username;
    }

    /**
     * 设置需要确保存在的平台用户名。
     *
     * @param username 原始用户名
     */
    public void setUsername(@NotNull String username) {
        this.username = new UsernameAccountIdentifierNormalizer().normalize(username);
    }
}
