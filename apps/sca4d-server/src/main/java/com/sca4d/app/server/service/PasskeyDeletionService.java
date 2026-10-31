package com.sca4d.app.server.service;

import com.sca4d.auth.passkey.PasskeyAuthenticationMethod;
import com.sca4d.auth.passkey.registration.PasskeyRegistrationService;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 删除 Passkey 凭据，并保证删除后的账号仍满足应用认证要求。 */
@Service
public class PasskeyDeletionService {

    private final PasskeyRegistrationService registrations;

    private final PlatformAuthenticationPolicyService policies;

    /**
     * 创建 Passkey 删除服务。
     *
     * @param registrations Passkey 凭据查询和删除操作
     * @param policies      账号认证要求查询操作
     */
    public PasskeyDeletionService(
            @NotNull PasskeyRegistrationService registrations,
            @NotNull PlatformAuthenticationPolicyService policies) {
        this.registrations = Objects.requireNonNull(registrations, "Passkey registration 服务不能为空");
        this.policies = Objects.requireNonNull(policies, "应用认证策略服务不能为空");
    }

    /**
     * 删除当前账号的 Passkey 凭据；认证要求不允许 Passkey 不可用时，模块在同一事务内至少保留一个凭据。
     *
     * @param account      凭据所属账号
     * @param credentialId 待删除的 Passkey 凭据标识
     * @throws BusinessException 凭据不存在、并发修改或删除后无法满足认证要求时抛出
     */
    @Transactional
    public void delete(@NotNull Account account, long credentialId) {
        Account checked = Objects.requireNonNull(account, "Account 不能为空");
        boolean unavailableAllowed = policies.allowsMethodUnavailable(
                checked,
                PasskeyAuthenticationMethod.CODE);
        registrations.delete(checked, credentialId, unavailableAllowed ? 0 : 1);
    }
}
