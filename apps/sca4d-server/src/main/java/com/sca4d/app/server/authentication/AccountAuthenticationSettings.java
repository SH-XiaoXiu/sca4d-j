package com.sca4d.app.server.authentication;

import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.federation.FederatedAuthenticationMethod;
import com.sca4d.auth.passkey.PasskeyAuthenticationMethod;
import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.auth.totp.TotpAuthenticationMethod;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 保存一个账号在示例应用中的动作认证要求和方法开关。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class AccountAuthenticationSettings {

    private static final Duration DEFAULT_REUSABLE_MAXIMUM_AGE = Duration.ofMinutes(5);

    private final int loginFactorCount;

    private final int sensitiveActionFactorCount;

    private final Duration reusableAuthenticationMaximumAge;

    private final Map<TreeCode, AuthenticationMethodSettings> methods;

    /**
     * 创建账号认证设置。
     *
     * @param loginFactorCount 登录所需不同因素数，范围为 1 到 3
     * @param sensitiveActionFactorCount 登录后敏感动作所需不同因素数，范围为 0 到 3
     * @param reusableAuthenticationMaximumAge 敏感动作允许复用 Session 认证的最大年龄
     * @param methods 各认证方法在三类位置的开关
     */
    public AccountAuthenticationSettings(
            int loginFactorCount,
            int sensitiveActionFactorCount,
            @NotNull Duration reusableAuthenticationMaximumAge,
            @NotNull List<AuthenticationMethodSettings> methods) {
        if (loginFactorCount < 1 || loginFactorCount > 3) {
            throw new IllegalArgumentException("登录认证因素数量必须在 1 到 3 之间");
        }
        if (sensitiveActionFactorCount < 0 || sensitiveActionFactorCount > 3) {
            throw new IllegalArgumentException("敏感动作认证因素数量必须在 0 到 3 之间");
        }
        Duration maximumAge = Objects.requireNonNull(
                reusableAuthenticationMaximumAge,
                "可复用认证最大年龄不能为空");
        if (maximumAge.isZero() || maximumAge.isNegative()) {
            throw new IllegalArgumentException("可复用认证最大年龄必须为正数");
        }
        LinkedHashMap<TreeCode, AuthenticationMethodSettings> indexed = new LinkedHashMap<>();
        for (AuthenticationMethodSettings method : List.copyOf(
                Objects.requireNonNull(methods, "认证方法设置不能为空"))) {
            AuthenticationMethodSettings duplicate = indexed.putIfAbsent(method.methodCode(), method);
            if (duplicate != null) {
                throw new IllegalArgumentException("认证方法设置重复: " + method.methodCode());
            }
        }
        this.loginFactorCount = loginFactorCount;
        this.sensitiveActionFactorCount = sensitiveActionFactorCount;
        this.reusableAuthenticationMaximumAge = maximumAge;
        this.methods = Map.copyOf(indexed);
    }

    /** 返回未显式配置账号使用的示例应用默认设置。 */
    public static @NotNull AccountAuthenticationSettings defaults() {
        return new AccountAuthenticationSettings(
                1,
                1,
                DEFAULT_REUSABLE_MAXIMUM_AGE,
                List.of(
                        new AuthenticationMethodSettings(
                                PasswordAuthenticationMethod.CODE,
                                true,
                                true,
                                true),
                        new AuthenticationMethodSettings(
                                TotpAuthenticationMethod.CODE,
                                false,
                                true,
                                true),
                        new AuthenticationMethodSettings(
                                PasskeyAuthenticationMethod.CODE,
                                true,
                                true,
                                true),
                        new AuthenticationMethodSettings(
                                EmailCodeAuthenticationMethod.CODE,
                                true,
                                true,
                                true),
                        new AuthenticationMethodSettings(
                                SmsCodeAuthenticationMethod.CODE,
                                false,
                                false,
                                false),
                        new AuthenticationMethodSettings(
                                FederatedAuthenticationMethod.CODE,
                                true,
                                true,
                                true)));
    }

    /** 返回登录所需不同因素数量。 */
    public int loginFactorCount() {
        return loginFactorCount;
    }

    /** 返回登录后敏感动作所需不同因素数量。 */
    public int sensitiveActionFactorCount() {
        return sensitiveActionFactorCount;
    }

    /** 返回敏感动作允许复用 Session 认证的最大年龄。 */
    public @NotNull Duration reusableAuthenticationMaximumAge() {
        return reusableAuthenticationMaximumAge;
    }

    /** 返回按方法编码索引的不可修改认证方法设置。 */
    public @NotNull @Unmodifiable Map<TreeCode, AuthenticationMethodSettings> methods() {
        return methods;
    }
}
