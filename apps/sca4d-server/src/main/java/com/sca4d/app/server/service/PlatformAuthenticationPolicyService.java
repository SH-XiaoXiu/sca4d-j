package com.sca4d.app.server.service;

import com.sca4d.app.server.authentication.AccountAuthenticationSettingConfiguration;
import com.sca4d.app.server.authentication.AccountAuthenticationSettings;
import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.plugin.mfa.MfaPolicy;
import com.sca4d.app.server.plugin.mfa.MfaSettings;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginMount;
import com.sca4d.core.api.plugin.PluginScope;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 保存 App 账号认证设置，并把完整策略判断桥接到必需的 MFA 插件。 */
@Service
public class PlatformAuthenticationPolicyService implements AuthenticationPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformAuthenticationPolicyService.class);
    private final Settings settings;
    private final MfaPolicy policy;

    /** 挂载生成客户端，设置持久化仍使用宿主事务。 */
    public PlatformAuthenticationPolicyService(@NotNull Settings settings,
            @PluginMount("AUTH:MFA:POLICY") @NotNull MfaPolicy policy) {
        this.settings = Objects.requireNonNull(settings);
        this.policy = Objects.requireNonNull(policy);
    }

    /** 返回账号显式设置或 Sca4d Server 默认设置。 */
    @Transactional(readOnly = true)
    public @NotNull AccountAuthenticationSettings settingsByAccountId(long accountId) {
        return inspect(accountId).value();
    }

    /** 返回账号认证设置值、来源及版本。 */
    @Transactional(readOnly = true)
    public @NotNull SettingValue<AccountAuthenticationSettings> inspect(long accountId) {
        return settings.inspect(AccountAuthenticationSettingConfiguration.ACCOUNT_POLICY, accountOwner(accountId));
    }

    /** 在同一事务中由插件校验完整候选配置，再保存账号设置。 */
    @PluginScope
    @Transactional
    public @NotNull SettingValue<AccountAuthenticationSettings> replace(@NotNull Account account,
            int loginFactorCount, int sensitiveActionFactorCount,
            @NotNull Duration reusableAuthenticationMaximumAge,
            @NotNull List<AuthenticationMethodSettings> methodSettings) {
        Account checked = Objects.requireNonNull(account, "认证设置账号不能为空");
        // 构造完整候选配置，先检查字段范围，再由当前作用域固定的插件判断可满足性。
        AccountAuthenticationSettings replacement = new AccountAuthenticationSettings(
                loginFactorCount, sensitiveActionFactorCount, reusableAuthenticationMaximumAge, methodSettings);
        policy.validate(checked.accountId(), new MfaSettings(replacement.loginFactorCount(),
                replacement.sensitiveActionFactorCount(), replacement.reusableAuthenticationMaximumAge(),
                List.copyOf(replacement.methods().values())));
        // 插件校验通过后才写入宿主设置，校验失败不会修改账号配置。
        SettingValue<AccountAuthenticationSettings> saved = settings.replace(
                AccountAuthenticationSettingConfiguration.ACCOUNT_POLICY, SettingOwner.account(checked), replacement);
        LOG.debug("账号认证设置已写入事务 | accountId={} loginFactorCount={} sensitiveActionFactorCount={} methodCount={}",
                checked.accountId(), saved.value().loginFactorCount(), saved.value().sensitiveActionFactorCount(),
                saved.value().methods().size());
        return saved;
    }

    /** 幂等删除账号的应用认证设置。 */
    @Transactional
    public void erase(long accountId) {
        settings.erase(AccountAuthenticationSettingConfiguration.ACCOUNT_POLICY, accountOwner(accountId));
        LOG.debug("账号认证设置删除已写入事务 | accountId={}", accountId);
    }

    /** 删除方法最后一个凭据会使策略无法满足时抛出冲突。 */
    @PluginScope
    @Transactional(readOnly = true)
    public void assertMethodRemovalAllowed(@NotNull Account account, @NotNull TreeCode unavailableMethod) {
        if (!policy.allowsMethodUnavailable(account.accountId(), unavailableMethod)) {
            throw new BusinessException(CoreErrorCode.CONFLICT);
        }
    }

    /** 判断移除方法后登录及敏感动作是否仍能满足。 */
    @PluginScope
    @Transactional(readOnly = true)
    public boolean allowsMethodUnavailable(@NotNull Account account, @NotNull TreeCode unavailableMethod) {
        return policy.allowsMethodUnavailable(account.accountId(), unavailableMethod);
    }

    @Override
    @PluginScope
    @Transactional(readOnly = true)
    public @NotNull AuthenticationRequirement requirementFor(@NotNull Account account,
            @NotNull AuthenticationActionCode actionCode) {
        AuthenticationRequirement requirement = policy.requirementFor(account.accountId(), actionCode);
        if (AuthenticationActions.LOGIN_ACTION.equals(actionCode) && requirement.factorCount() < 1) {
            throw new IllegalStateException("MFA 插件不能为登录返回零因素要求");
        }
        return requirement;
    }

    @Override
    @PluginScope
    @Transactional(readOnly = true)
    public @NotNull Set<TreeCode> allowedMethods(@NotNull Account account, @NotNull AuthenticationActionCode actionCode,
            @NotNull List<Authentication> completedAuthentications) {
        return policy.allowedMethods(account.accountId(), actionCode, completedAuthentications);
    }

    /** 构造账号设置所有者并拒绝非法标识。 */
    private static @NotNull SettingOwner accountOwner(long accountId) {
        if (accountId <= 0) throw new IllegalArgumentException("Account 标识必须为正数");
        return new SettingOwner(Account.RESOURCE_TYPE, Long.toString(accountId));
    }
}
