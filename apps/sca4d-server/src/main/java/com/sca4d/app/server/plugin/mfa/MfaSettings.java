package com.sca4d.app.server.plugin.mfa;

import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginApiType;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 保存不依赖认证模块实现的账号 MFA 配置快照。 */
@PluginApiType
public final class MfaSettings {
    private final Duration reusableAuthenticationMaximumAge;

    private final List<AuthenticationMethodSettings> methods;

    private final int loginFactorCount;

    private final int sensitiveActionFactorCount;

    /**
     * 校验并保存不可变配置快照，方法编码不得重复。
     *
     * @param loginFactorCount 登录所需独立因素数，范围为 1 到 3
     * @param sensitiveActionFactorCount 敏感动作所需独立因素数，范围为 0 到 3
     * @param reusableAuthenticationMaximumAge 已有认证允许复用的最大时长，必须大于零
     * @param methods 各认证方法的用途开关；不检查方法是否安装或账号凭据是否可用
     */
    public MfaSettings(
            int loginFactorCount,
            int sensitiveActionFactorCount,
            @NotNull Duration reusableAuthenticationMaximumAge,
            @NotNull List<AuthenticationMethodSettings> methods) {
        if (loginFactorCount < 1
                || loginFactorCount > 3
                || sensitiveActionFactorCount < 0
                || sensitiveActionFactorCount > 3) {
            throw new IllegalArgumentException("登录因素必须为 1 到 3，敏感动作因素必须为 0 到 3");
        }
        Objects.requireNonNull(reusableAuthenticationMaximumAge);
        if (reusableAuthenticationMaximumAge.isZero() || reusableAuthenticationMaximumAge.isNegative()) {
            throw new IllegalArgumentException("认证复用年龄必须为正数");
        }
        var codes = new HashSet<TreeCode>();
        for (AuthenticationMethodSettings method : methods) {
            if (!codes.add(method.methodCode()))
                throw new IllegalArgumentException("认证方法开关重复");
        }
        this.loginFactorCount = loginFactorCount;
        this.sensitiveActionFactorCount = sensitiveActionFactorCount;
        this.reusableAuthenticationMaximumAge = reusableAuthenticationMaximumAge;
        this.methods = List.copyOf(methods);
    }

    /** 返回登录所需独立因素数。 */
    public int loginFactorCount() {
        return loginFactorCount;
    }

    /** 返回敏感动作所需独立因素数。 */
    public int sensitiveActionFactorCount() {
        return sensitiveActionFactorCount;
    }

    /** 返回已有认证的最大复用年龄。 */
    public @NotNull Duration reusableAuthenticationMaximumAge() {
        return reusableAuthenticationMaximumAge;
    }

    /** 返回三个用途开关的不可变集合。 */
    public @NotNull List<AuthenticationMethodSettings> methods() {
        return methods;
    }
}
