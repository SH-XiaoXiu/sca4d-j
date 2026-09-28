package com.sca4d.auth.password.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 配置 Password 用户名认证域和本地初始账号凭据。 */
@ConfigurationProperties("sca4dj.auth.password")
public class PasswordProperties {

    private final Bootstrap bootstrap = new Bootstrap();

    private String accountRealm = "platform";

    private List<String> loginIdentifierTypes = List.of("USERNAME");

    /** 创建使用默认 bootstrap 设置的 Password 配置。 */
    public PasswordProperties() {
    }

    /**
     * 返回是否在启动时初始化密码凭据。
     *
     * @return 启动时初始化密码凭据时为 {@code true}
     */
    public boolean isBootstrapEnabled() {
        return bootstrap.enabled;
    }

    /**
     * 设置是否在启动时初始化密码凭据。
     *
     * @param bootstrapEnabled 启动时是否初始化密码凭据
     */
    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        bootstrap.enabled = bootstrapEnabled;
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
        return bootstrap.username;
    }

    /**
     * 设置 bootstrap 账号用户名。
     *
     * @param bootstrapUsername bootstrap 账号用户名
     */
    public void setBootstrapUsername(String bootstrapUsername) {
        bootstrap.username = bootstrapUsername;
    }

    /**
     * 返回 bootstrap 原始密码。
     *
     * @return bootstrap 原始密码；未配置时为 {@code null}
     */
    public String getBootstrapPassword() {
        return bootstrap.password;
    }

    /**
     * 设置 bootstrap 原始密码。
     *
     * @param bootstrapPassword bootstrap 原始密码，不写入日志
     */
    public void setBootstrapPassword(String bootstrapPassword) {
        bootstrap.password = bootstrapPassword;
    }

    /** @return Password bootstrap 资源配置 */
    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    /** 配置初始账号密码凭据。 */
    public static class Bootstrap {

        private boolean enabled = true;

        private String username = "admin";

        private String password;

        /** @return 是否在启动时初始化密码凭据 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否在启动时初始化密码凭据 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return bootstrap 账号用户名 */
        public String getUsername() {
            return username;
        }

        /** @param username bootstrap 账号用户名 */
        public void setUsername(String username) {
            this.username = username;
        }

        /** @return bootstrap 原始密码 */
        public String getPassword() {
            return password;
        }

        /** @param password bootstrap 原始密码 */
        public void setPassword(String password) {
            this.password = password;
        }
    }
}
