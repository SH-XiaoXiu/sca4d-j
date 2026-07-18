package com.sca4d.config.properties;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 首次部署时初始化平台管理员的可选配置。
 */
@ConfigurationProperties(prefix = "sca4dj.bootstrap")
public class Sca4djBootstrapProperties {

    private String platformAdministratorIdentifier;
    private String platformAdministratorPassword;
    private String platformAdministratorDisplayName = "平台管理员";

    /**
     * 获取用于创建首个管理员的密码登录标识。
     *
     * @return 未配置时返回 {@code null}，启动操作不会创建管理员
     */
    public @Nullable String getPlatformAdministratorIdentifier() {
        return platformAdministratorIdentifier;
    }

    /**
     * 设置用于创建首个管理员的密码登录标识。
     *
     * @param platformAdministratorIdentifier 部署环境提供的登录标识
     */
    public void setPlatformAdministratorIdentifier(@Nullable String platformAdministratorIdentifier) {
        this.platformAdministratorIdentifier = platformAdministratorIdentifier;
    }

    /**
     * 获取用于创建首个管理员的密码。
     *
     * @return 未配置时返回 {@code null}，启动操作不会创建管理员
     */
    public @Nullable String getPlatformAdministratorPassword() {
        return platformAdministratorPassword;
    }

    /**
     * 设置用于创建首个管理员的密码。
     *
     * @param platformAdministratorPassword 部署环境提供的初始密码
     */
    public void setPlatformAdministratorPassword(@Nullable String platformAdministratorPassword) {
        this.platformAdministratorPassword = platformAdministratorPassword;
    }

    /**
     * 获取首个管理员的展示名称。
     *
     * @return 未配置时使用固定的默认展示名称
     */
    public String getPlatformAdministratorDisplayName() {
        return platformAdministratorDisplayName;
    }

    /**
     * 设置首个管理员的展示名称。
     *
     * @param platformAdministratorDisplayName 部署环境指定的展示名称
     */
    public void setPlatformAdministratorDisplayName(String platformAdministratorDisplayName) {
        this.platformAdministratorDisplayName = platformAdministratorDisplayName;
    }
}
