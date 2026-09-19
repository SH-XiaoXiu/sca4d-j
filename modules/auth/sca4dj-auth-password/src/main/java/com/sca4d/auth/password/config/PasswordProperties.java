package com.sca4d.auth.password.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 配置 Password 用户名认证域和本地初始账号凭据。 */
@ConfigurationProperties("sca4dj.auth.password")
public class PasswordProperties {

    private boolean bootstrapEnabled = true;

    private String accountRealm = "platform";

    private List<String> loginIdentifierTypes = List.of("USERNAME");

    private String bootstrapUsername = "admin";

    private String bootstrapPassword;

    /** 创建使用默认 bootstrap 设置的 Password 配置。 */
    public PasswordProperties() {
    }

    /**
     * 返回是否在启动时初始化密码凭据。
     *
     * @return 启动时初始化密码凭据时为 {@code true}
     */
    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    /**
     * 设置是否在启动时初始化密码凭据。
     *
     * @param bootstrapEnabled 启动时是否初始化密码凭据
     */
    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }

    /**
     * 返回用户名登录和 bootstrap 查找 Account 使用的认证域。
     *
     * @return 认证域，默认为 {@code platform}
     */
    public String getAccountRealm() {
        return accountRealm;
    }

    /**
     * 设置用户名登录和 bootstrap 查找 Account 使用的认证域。
     *
     * @param accountRealm 非空白认证域
     */
    public void setAccountRealm(String accountRealm) {
        if (accountRealm == null || accountRealm.isBlank()) {
            throw new IllegalArgumentException("Password 账号认证域不能为空");
        }
        this.accountRealm = accountRealm;
    }

    /**
     * 返回 Password 登录依次尝试的开放标识类型。
     *
     * @return 至少包含一个且不重复的稳定类型值
     */
    public List<String> getLoginIdentifierTypes() {
        return loginIdentifierTypes;
    }

    /**
     * 设置 Password 登录使用的开放标识类型。
     *
     * @param loginIdentifierTypes 至少一个非空白且不重复的稳定类型值
     */
    public void setLoginIdentifierTypes(List<String> loginIdentifierTypes) {
        List<String> checked = Objects.requireNonNull(loginIdentifierTypes, "Password 登录标识类型不能为空")
                .stream()
                .map(value -> Objects.requireNonNull(value, "Password 登录标识类型不能包含 null").trim())
                .toList();
        if (checked.isEmpty() || checked.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Password 至少需要一个非空白登录标识类型");
        }
        if (new LinkedHashSet<>(checked).size() != checked.size()) {
            throw new IllegalArgumentException("Password 登录标识类型不能重复");
        }
        this.loginIdentifierTypes = List.copyOf(checked);
    }

    /**
     * 返回 bootstrap 账号用户名。
     *
     * @return bootstrap 账号用户名
     */
    public String getBootstrapUsername() {
        return bootstrapUsername;
    }

    /**
     * 设置 bootstrap 账号用户名。
     *
     * @param bootstrapUsername bootstrap 账号用户名
     */
    public void setBootstrapUsername(String bootstrapUsername) {
        this.bootstrapUsername = bootstrapUsername;
    }

    /**
     * 返回 bootstrap 原始密码。
     *
     * @return bootstrap 原始密码；未配置时为 {@code null}
     */
    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    /**
     * 设置 bootstrap 原始密码。
     *
     * @param bootstrapPassword bootstrap 原始密码，不写入日志
     */
    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }
}
