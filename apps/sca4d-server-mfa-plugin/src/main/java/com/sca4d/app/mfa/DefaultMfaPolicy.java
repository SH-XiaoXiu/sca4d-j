package com.sca4d.app.mfa;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.app.server.plugin.mfa.MfaFacts;
import com.sca4d.app.server.plugin.mfa.MfaPolicy;
import com.sca4d.app.server.plugin.mfa.MfaSettings;
import com.sca4d.app.server.plugin.mfa.MfaMethodFacts;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 根据宿主返回的账号设置和方法事实判断 MFA 要求、设置有效性及凭据移除条件。 */
public final class DefaultMfaPolicy implements MfaPolicy {
    private static final TreeCode SMS = TreeCode.of("AUTHENTICATION:SMS_CODE");
    private static final AuthenticationActionCode PHONE =
            new AuthenticationActionCode(AuthenticationActions.PHONE_VERIFY_CODE);
    private final MfaFacts facts;

    /** 保存宿主事实客户端；计算策略时通过客户端读取当前账号设置和方法状态。 */
    public DefaultMfaPolicy(MfaFacts facts) { this.facts = Objects.requireNonNull(facts); }

    @Override
    public @NotNull AuthenticationRequirement requirementFor(long accountId, @NotNull AuthenticationActionCode action) {
        MfaSettings current = facts.settings(accountId);
        return AuthenticationActions.LOGIN_ACTION.equals(action)
                ? new AuthenticationRequirement(current.loginFactorCount(), null)
                : new AuthenticationRequirement(current.sensitiveActionFactorCount(), current.reusableAuthenticationMaximumAge());
    }

    @Override
    public @NotNull Set<TreeCode> allowedMethods(long accountId, @NotNull AuthenticationActionCode action,
            @NotNull List<Authentication> completed) {
        if (PHONE.equals(action)) {
            // 手机验证只允许当前账号可用的短信方法，不采用普通登录的方法开关。
            return facts.availableMethods(accountId).contains(SMS) ? Set.of(SMS) : Set.of();
        }
        // 汇总已经完成的因素类别，同类方法不能重复贡献独立因素。
        MfaSettings current = facts.settings(accountId);
        Map<TreeCode, MfaMethodFacts> installed = installedMethods();
        EnumSet<AuthenticationFactorKind> completedKinds = EnumSet.noneOf(AuthenticationFactorKind.class);
        completed.forEach(authentication -> completedKinds.addAll(authentication.factorKinds()));
        boolean login = AuthenticationActions.LOGIN_ACTION.equals(action);
        boolean first = login && completed.isEmpty();
        // 首次登录必须能够识别账号；后续认证只选择能补充尚未完成因素的方法。
        return current.methods().stream()
                .filter(method -> enabled(method, login, first))
                .map(AuthenticationMethodSettings::methodCode)
                .filter(code -> {
                    MfaMethodFacts method = installed.get(code);
                    return method != null && (!first || method.canIdentifyAccount())
                            && method.factorKinds().stream().anyMatch(kind -> !completedKinds.contains(kind));
                }).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void validate(long accountId, @NotNull MfaSettings proposed) {
        // 先拒绝未安装方法，后续因素判断只使用宿主明确声明的方法事实。
        Map<TreeCode, MfaMethodFacts> installed = installedMethods();
        for (AuthenticationMethodSettings configured : proposed.methods()) {
            if (!installed.containsKey(configured.methodCode())) {
                throw new IllegalArgumentException("认证方法未安装: " + configured.methodCode());
            }
        }
        // 用账号实际可用的方法分别检查首次登录、完整登录和敏感动作要求。
        Set<TreeCode> available = facts.availableMethods(accountId);
        if (factorKinds(proposed, installed, available, method -> method.loginStartEnabled()
                && installed.get(method.methodCode()).canIdentifyAccount(), null).isEmpty()) {
            throw new IllegalArgumentException("登录必须启用至少一种能够识别账号的认证方法");
        }
        if (factorKinds(proposed, installed, available,
                method -> method.loginStartEnabled() || method.loginAdditionalEnabled(), null).size()
                < proposed.loginFactorCount()) {
            throw new IllegalArgumentException("登录启用方法无法满足登录因素数量");
        }
        if (factorKinds(proposed, installed, available, AuthenticationMethodSettings::sensitiveActionEnabled, null).size()
                < proposed.sensitiveActionFactorCount()) {
            throw new IllegalArgumentException("敏感动作启用方法无法满足认证因素数量");
        }
    }

    @Override
    public boolean allowsMethodUnavailable(long accountId, @NotNull TreeCode excluded) {
        // 模拟移除方法后的可用因素，不修改 Credential 或账号设置。
        MfaSettings current = facts.settings(accountId);
        Map<TreeCode, MfaMethodFacts> installed = installedMethods();
        Set<TreeCode> available = facts.availableMethods(accountId);
        Set<AuthenticationFactorKind> first = factorKinds(current, installed, available,
                method -> method.loginStartEnabled() && installed.get(method.methodCode()).canIdentifyAccount(), excluded);
        Set<AuthenticationFactorKind> login = factorKinds(current, installed, available,
                method -> method.loginStartEnabled() || method.loginAdditionalEnabled(), excluded);
        Set<AuthenticationFactorKind> sensitive = factorKinds(current, installed, available,
                AuthenticationMethodSettings::sensitiveActionEnabled, excluded);
        // 必须保留账号识别入口，同时满足登录和敏感动作要求，才能移除最后一个凭据。
        return !first.isEmpty() && login.size() >= current.loginFactorCount()
                && sensitive.size() >= current.sensitiveActionFactorCount();
    }

    /** 按稳定编码索引宿主方法事实，并拒绝违反契约的重复声明。 */
    private Map<TreeCode, MfaMethodFacts> installedMethods() {
        return facts.methods().stream().collect(Collectors.toUnmodifiableMap(MfaMethodFacts::code, method -> method));
    }

    /** 按用途与凭据可用性汇总独立因素，不把两个同类方法计算成两个因素。 */
    private Set<AuthenticationFactorKind> factorKinds(MfaSettings settings,
            Map<TreeCode, MfaMethodFacts> installed, Set<TreeCode> available,
            Predicate<AuthenticationMethodSettings> enabled, TreeCode excluded) {
        EnumSet<AuthenticationFactorKind> kinds = EnumSet.noneOf(AuthenticationFactorKind.class);
        for (AuthenticationMethodSettings configured : settings.methods()) {
            MfaMethodFacts method = installed.get(configured.methodCode());
            if (!configured.methodCode().equals(excluded) && method != null
                    && enabled.test(configured) && available.contains(configured.methodCode())) {
                kinds.addAll(method.factorKinds());
            }
        }
        return Set.copyOf(kinds);
    }

    /** 根据动作与认证阶段选择原有三个用途开关。 */
    private static boolean enabled(AuthenticationMethodSettings method, boolean login, boolean first) {
        return !login ? method.sensitiveActionEnabled()
                : first ? method.loginStartEnabled() : method.loginAdditionalEnabled();
    }
}
