package com.sca4d.app.server.service;

import com.sca4d.app.server.authentication.AccountAuthenticationSettingConfiguration;
import com.sca4d.app.server.authentication.AccountAuthenticationSettings;
import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实现 Sca4d Server 的登录和敏感动作认证规则，并管理账号的方法开关。
 */
@Service
public class PlatformAuthenticationPolicyService implements AuthenticationPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformAuthenticationPolicyService.class);

    private final Settings settings;

    private final Map<AuthenticationMethodCode, AuthenticationMethod<?>> installedMethods;

    /**
     * 创建 Sca4d Server 动作认证策略。
     *
     * @param settings         统一设置读写接口
     * @param installedMethods 当前应用安装的认证方法
     */
    public PlatformAuthenticationPolicyService(
            @NotNull Settings settings,
            @NotNull List<AuthenticationMethod<?>> installedMethods) {
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        LinkedHashMap<AuthenticationMethodCode, AuthenticationMethod<?>> indexed = new LinkedHashMap<>();
        for (AuthenticationMethod<?> method : List.copyOf(
                Objects.requireNonNull(installedMethods, "已安装认证方法不能为空"))) {
            AuthenticationMethod<?> duplicate = indexed.putIfAbsent(method.code(), method);
            if (duplicate != null) {
                throw new IllegalStateException("认证方法编码重复: " + method.code().value());
            }
        }
        this.installedMethods = Map.copyOf(indexed);
    }

    /**
     * 返回账号显式设置或 Sca4d Server 默认设置。
     */
    @Transactional(readOnly = true)
    public @NotNull AccountAuthenticationSettings settingsByAccountId(long accountId) {
        return inspect(accountId).value();
    }

    /** @return 指定 Account 的认证策略值、来源和版本 */
    @Transactional(readOnly = true)
    public @NotNull SettingValue<AccountAuthenticationSettings> inspect(long accountId) {
        return settings.inspect(
                AccountAuthenticationSettingConfiguration.ACCOUNT_POLICY,
                accountOwner(accountId));
    }

    /**
     * 校验登录和敏感动作能够由当前安装方法满足后，完整替换账号设置。
     */
    @Transactional
    public @NotNull SettingValue<AccountAuthenticationSettings> replace(
            @NotNull Account account,
            int loginFactorCount,
            int sensitiveActionFactorCount,
            @NotNull java.time.Duration reusableAuthenticationMaximumAge,
            @NotNull List<AuthenticationMethodSettings> methodSettings) {
        Account checkedAccount = Objects.requireNonNull(account, "认证设置账号不能为空");

        // 先构造完整替换值，由类型校验因素数量、复用时间和重复方法。
        AccountAuthenticationSettings replacement = new AccountAuthenticationSettings(
                loginFactorCount,
                sensitiveActionFactorCount,
                reusableAuthenticationMaximumAge,
                methodSettings);

        // 再按账号当前 Credential 和应用安装方法验证三个用途开关都能满足动作要求。
        validate(replacement, checkedAccount);

        // 校验通过后在当前事务中完整替换因素设置和方法开关。
        SettingValue<AccountAuthenticationSettings> saved = settings.replace(
                AccountAuthenticationSettingConfiguration.ACCOUNT_POLICY,
                SettingOwner.account(checkedAccount),
                replacement);
        LOG.debug(
                "账号认证设置已写入事务 | accountId={} loginFactorCount={} sensitiveActionFactorCount={} methodCount={}",
                checkedAccount.accountId(),
                saved.value().loginFactorCount(),
                saved.value().sensitiveActionFactorCount(),
                saved.value().methods().size());
        return saved;
    }

    /**
     * 幂等删除账号全部应用认证设置。
     */
    @Transactional
    public void erase(long accountId) {
        settings.erase(AccountAuthenticationSettingConfiguration.ACCOUNT_POLICY, accountOwner(accountId));
        LOG.debug("账号认证设置删除已写入事务 | accountId={}", accountId);
    }

    /**
     * 确认指定认证方法完全不可用后，账号当前设置仍能满足登录和敏感动作要求。
     *
     * @param account           待修改认证凭据的账号
     * @param unavailableMethod 删除最后一个凭据后将不可用的方法
     * @throws BusinessException 删除会使账号认证设置无法满足时抛出冲突
     */
    @Transactional(readOnly = true)
    public void assertMethodRemovalAllowed(
            @NotNull Account account,
            @NotNull AuthenticationMethodCode unavailableMethod) {
        if (!allowsMethodUnavailable(account, unavailableMethod)) {
            throw new BusinessException(CoreErrorCode.CONFLICT);
        }
    }

    /**
     * 返回指定认证方法完全不可用后，账号当前设置是否仍能满足全部认证要求。
     */
    @Transactional(readOnly = true)
    public boolean allowsMethodUnavailable(
            @NotNull Account account,
            @NotNull AuthenticationMethodCode unavailableMethod) {
        Account checkedAccount = Objects.requireNonNull(account, "认证账号不能为空");
        AuthenticationMethodCode excluded = Objects.requireNonNull(
                unavailableMethod,
                "不可用认证方法编码不能为空");
        AccountAuthenticationSettings current = settingsByAccountId(checkedAccount.accountId());
        return satisfies(current, checkedAccount, excluded);
    }

    /**
     * 登录至少要求一个新因素；敏感动作按账号设置决定是否复用 Session 认证。
     */
    @Override
    @Transactional(readOnly = true)
    public @NotNull AuthenticationRequirement requirementFor(
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode) {
        AccountAuthenticationSettings current = settingsByAccountId(
                Objects.requireNonNull(account, "认证账号不能为空").accountId());
        if (AuthenticationActions.LOGIN_ACTION.equals(actionCode)) {
            return new AuthenticationRequirement(current.loginFactorCount(), null);
        }
        return new AuthenticationRequirement(
                current.sensitiveActionFactorCount(),
                current.reusableAuthenticationMaximumAge());
    }

    /**
     * 根据登录阶段或敏感动作开关返回仍能贡献新因素的认证方法。
     */
    @Override
    @Transactional(readOnly = true)
    public @NotNull Set<AuthenticationMethodCode> allowedMethods(
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode,
            @NotNull List<Authentication> completedAuthentications) {
        AccountAuthenticationSettings current = settingsByAccountId(
                Objects.requireNonNull(account, "认证账号不能为空").accountId());
        List<Authentication> completed = List.copyOf(
                Objects.requireNonNull(completedAuthentications, "已完成认证不能为空"));

        // 手机号绑定只接受发往当前 Profile 手机号的 SMS Code，不依赖普通 SMS 登录开关。
        if (AuthenticationActions.PHONE_VERIFY_CODE.equals(actionCode.value())) {
            AuthenticationMethod<?> sms = installedMethods.get(SmsCodeAuthenticationMethod.CODE);
            return sms != null && sms.available(account)
                    ? Set.of(SmsCodeAuthenticationMethod.CODE)
                    : Set.of();
        }

        // 已完成因素按类别去重，同类方法不能重复增加认证因素数量。
        EnumSet<AuthenticationFactorKind> completedKinds = EnumSet.noneOf(AuthenticationFactorKind.class);
        completed.forEach(authentication -> completedKinds.addAll(authentication.factorKinds()));
        boolean login = AuthenticationActions.LOGIN_ACTION.equals(actionCode);
        boolean loginStart = login && completed.isEmpty();

        // 先应用 Sca4d Server 的用途开关，再过滤未安装、不可用或不能贡献新因素的方法。
        return current.methods()
                .values()
                .stream()
                .filter(method -> enabled(method, login, loginStart))
                .map(AuthenticationMethodSettings::methodCode)
                .filter(methodCode -> {
                    AuthenticationMethod<?> installed = installedMethods.get(methodCode);
                    return installed != null
                            && (!loginStart || installed.canIdentifyAccount())
                            && installed.factorKinds().stream().anyMatch(kind -> !completedKinds.contains(kind));
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 确认三类开关能提供策略要求的不同因素，并拒绝未安装方法。
     */
    private void validate(AccountAuthenticationSettings replacement, Account account) {
        // 设置只能引用 Sca4d Server 已安装的认证方法。
        for (AuthenticationMethodSettings configured : replacement.methods().values()) {
            if (!installedMethods.containsKey(configured.methodCode())) {
                throw new IllegalArgumentException("认证方法未安装: " + configured.methodCode().value());
            }
        }

        // 登录必须保留至少一个能够识别账号的入口。
        Set<AuthenticationFactorKind> loginStartKinds = factorKinds(
                replacement,
                method -> method.loginStartEnabled() && installedMethods.get(method.methodCode()).canIdentifyAccount(),
                account);
        if (loginStartKinds.isEmpty()) {
            throw new IllegalArgumentException("登录必须启用至少一种能够识别账号的认证方法");
        }

        // 登录入口和后续方法合并后必须提供设置要求的不同因素。
        Set<AuthenticationFactorKind> loginKinds = factorKinds(
                replacement,
                method -> method.loginStartEnabled() || method.loginAdditionalEnabled(),
                account);
        if (loginKinds.size() < replacement.loginFactorCount()) {
            throw new IllegalArgumentException("登录启用方法无法满足登录因素数量");
        }

        // 敏感动作独立检查自己的方法用途开关和因素数量。
        Set<AuthenticationFactorKind> actionKinds = factorKinds(
                replacement,
                AuthenticationMethodSettings::sensitiveActionEnabled,
                account);
        if (actionKinds.size() < replacement.sensitiveActionFactorCount()) {
            throw new IllegalArgumentException("敏感动作启用方法无法满足认证因素数量");
        }
    }

    /**
     * 返回符合开关且账号当前可用的方法能够贡献的不同因素。
     */
    private Set<AuthenticationFactorKind> factorKinds(
            AccountAuthenticationSettings replacement,
            java.util.function.Predicate<AuthenticationMethodSettings> enabled,
            Account account) {
        return factorKinds(replacement, enabled, account, null);
    }

    /**
     * 返回排除指定方法后，符合开关且账号当前可用的方法能够贡献的不同因素。
     */
    private Set<AuthenticationFactorKind> factorKinds(
            AccountAuthenticationSettings replacement,
            java.util.function.Predicate<AuthenticationMethodSettings> enabled,
            Account account,
            AuthenticationMethodCode excludedMethod) {
        EnumSet<AuthenticationFactorKind> kinds = EnumSet.noneOf(AuthenticationFactorKind.class);
        for (AuthenticationMethodSettings configured : replacement.methods().values()) {
            AuthenticationMethod<?> installed = installedMethods.get(configured.methodCode());
            if (!configured.methodCode().equals(excludedMethod)
                    && installed != null
                    && enabled.test(configured)
                    && installed.available(account)) {
                kinds.addAll(installed.factorKinds());
            }
        }
        return Set.copyOf(kinds);
    }

    /**
     * 检查排除一个认证方法后的登录入口、登录因素和敏感动作因素。
     */
    private boolean satisfies(
            AccountAuthenticationSettings current,
            Account account,
            AuthenticationMethodCode excludedMethod) {
        // 分别计算排除方法后的登录入口、全部登录因素和敏感动作因素。
        Set<AuthenticationFactorKind> loginStartKinds = factorKinds(
                current,
                method -> method.loginStartEnabled()
                        && installedMethods.get(method.methodCode()).canIdentifyAccount(),
                account,
                excludedMethod);
        Set<AuthenticationFactorKind> loginKinds = factorKinds(
                current,
                method -> method.loginStartEnabled() || method.loginAdditionalEnabled(),
                account,
                excludedMethod);
        Set<AuthenticationFactorKind> actionKinds = factorKinds(
                current,
                AuthenticationMethodSettings::sensitiveActionEnabled,
                account,
                excludedMethod);

        // 三项必须同时满足，避免删除 Credential 后留下无法完成的动作规则。
        return !loginStartKinds.isEmpty()
                && loginKinds.size() >= current.loginFactorCount()
                && actionKinds.size() >= current.sensitiveActionFactorCount();
    }

    /**
     * 返回方法开关是否允许当前登录阶段或敏感动作使用该方法。
     */
    private static boolean enabled(
            AuthenticationMethodSettings method,
            boolean login,
            boolean loginStart) {
        if (!login) {
            return method.sensitiveActionEnabled();
        }
        return loginStart ? method.loginStartEnabled() : method.loginAdditionalEnabled();
    }

    /** 返回指定 Account 标识对应的统一设置 owner。 */
    private static @NotNull SettingOwner accountOwner(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        return new SettingOwner(Account.RESOURCE_TYPE, Long.toString(accountId));
    }
}
