package com.sca4d.app.server.plugin.mfa;

import com.sca4d.app.server.authentication.AccountAuthenticationSettingConfiguration;
import com.sca4d.app.server.authentication.AccountAuthenticationSettings;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginBinding;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

/** 从宿主设置和已安装方法读取策略事实，所有持久化仍由原模块完成。 */
@Service
@PluginBinding(MfaFacts.class)
public class MfaFactsService implements MfaFacts {
    private static final Logger LOG = LoggerFactory.getLogger(MfaFactsService.class);
    private final Settings settings;
    private final AccountDirectory accounts;
    private final Map<TreeCode, AuthenticationMethod<?>> methods;

    /** 检查宿主方法编码唯一性，不把实际方法对象传入插件。 */
    public MfaFactsService(Settings settings, AccountDirectory accounts, List<AuthenticationMethod<?>> methods) {
        this.settings = settings;
        this.accounts = accounts;
        Map<TreeCode, AuthenticationMethod<?>> indexed = new LinkedHashMap<>();
        for (AuthenticationMethod<?> method : methods) {
            if (indexed.putIfAbsent(method.code(), method) != null) {
                throw new IllegalStateException("认证方法编码重复: " + method.code());
            }
        }
        this.methods = Map.copyOf(indexed);
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull MfaSettings settings(long accountId) {
        if (accountId <= 0) throw new IllegalArgumentException("Account 标识必须为正数");
        AccountAuthenticationSettings current = settings.inspect(
                AccountAuthenticationSettingConfiguration.ACCOUNT_POLICY,
                new SettingOwner(Account.RESOURCE_TYPE, Long.toString(accountId))).value();
        LOG.trace("插件策略已读取账号设置 | accountId={}", accountId);
        return new MfaSettings(current.loginFactorCount(), current.sensitiveActionFactorCount(),
                current.reusableAuthenticationMaximumAge(), List.copyOf(current.methods().values()));
    }

    @Override
    public @NotNull List<MfaMethodFacts> methods() {
        return methods.values().stream().map(method ->
                new MfaMethodFacts(method.code(), method.factorKinds(), method.canIdentifyAccount())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull Set<TreeCode> availableMethods(long accountId) {
        Account account = accounts.activeById(accountId);
        Set<TreeCode> result = methods.values().stream().filter(method -> method.available(account))
                .map(AuthenticationMethod::code).collect(Collectors.toUnmodifiableSet());
        LOG.trace("插件策略已检查可用方法 | accountId={} methodCount={}", accountId, result.size());
        return result;
    }
}
