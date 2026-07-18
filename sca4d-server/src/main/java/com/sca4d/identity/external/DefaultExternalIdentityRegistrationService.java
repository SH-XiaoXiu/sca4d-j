package com.sca4d.identity.external;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.ProvisionedAccount;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.identity.IdentityProviderScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 创建仅使用 ExternalIdentity 登录的本地 Account 及其 DEFAULT Principal 和默认资料。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultExternalIdentityRegistrationService implements ExternalIdentityRegistrationService {

    private final AccountProvisioningService accountProvisioningService;
    private final PrincipalService principalService;
    private final ExternalIdentityService externalIdentityService;

    @Override
    @Transactional
    public @NotNull ExternalIdentityVerification register(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull ExternalIdentityProfile profile) {
        String displayName = profile.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = profile.getEmail();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = "外部用户";
        }
        if (displayName.length() > 128) {
            displayName = displayName.substring(0, 128);
        }

        ProvisionedAccount account = accountProvisioningService.createExternal(
                scope.getScopeType(), scope.getScopeId(), displayName);
        if (scope.getScopeType() == AccountScopeType.TENANT) {
            principalService.createTenant(
                    account.getSubject().getId(), scope.getScopeId(), displayName, List.of());
        }
        ExternalIdentityVerification verification = externalIdentityService.link(
                scope, providerId, account.getAccount().getId(), profile);
        log.info(
                "ExternalIdentity JIT 注册已写入事务 | providerId={} identityId={} subjectId={} accountId={} scopeType={} scopeId={}",
                providerId,
                verification.getIdentityId(),
                account.getSubject().getId(),
                account.getAccount().getId(),
                scope.getScopeType(),
                scope.getScopeId());
        return verification;
    }
}
