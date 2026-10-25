package com.sca4d.auth.federation;

import com.sca4d.auth.federation.exception.AuthFederationException;
import com.sca4d.auth.federation.identity.FederatedIdentityRepository;
import com.sca4d.auth.federation.identity.FederatedIdentityService;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * 消费已完成的 OIDC/OAuth2 授权并向认证引擎返回外部身份因素。
 */
public class FederatedAuthenticationMethod implements AuthenticationMethod<FederatedProof> {

    private static final Logger LOG = LoggerFactory.getLogger(FederatedAuthenticationMethod.class);

    /**
     * 联邦登录方法的稳定编码。
     */
    public static final TreeCode CODE = TreeCode.ofSlash("AUTHENTICATION/FEDERATED");

    private static final Set<AuthenticationFactorKind> FACTORS = Set.of(AuthenticationFactorKind.POSSESSION);

    private final FederatedIdentityService identityBindings;

    private final FederatedProviderService providers;

    private final FederatedIdentityRepository identityRepository;

    private final Clock clock;

    private final FederatedAccountResolver accounts;

    private final FederatedAuthorizationStore authorizations;

    /**
     * 创建联邦认证方法。
     */
    public FederatedAuthenticationMethod(
            FederatedAuthorizationStore authorizations,
            FederatedProviderService providers,
            FederatedAccountResolver accounts,
            FederatedIdentityService identityBindings,
            FederatedIdentityRepository identityRepository,
            Clock clock) {
        this.authorizations = Objects.requireNonNull(authorizations, "授权存储不能为空");
        this.providers = Objects.requireNonNull(providers, "Provider 服务不能为空");
        this.accounts = Objects.requireNonNull(accounts, "Account 解析器不能为空");
        this.identityBindings = Objects.requireNonNull(identityBindings, "联邦身份服务不能为空");
        this.identityRepository = Objects.requireNonNull(identityRepository, "联邦身份 Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<FederatedProof> proofType() {
        return FederatedProof.class;
    }

    @Override
    public @NotNull Set<AuthenticationFactorKind> factorKinds() {
        return FACTORS;
    }

    @Override
    public boolean canIdentifyAccount() {
        return true;
    }

    @Override
    public boolean available(@NotNull Account account) {
        return !identityBindings.findByAccount(account.accountId()).isEmpty();
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account expectedAccount,
            @NotNull FederatedProof proof) {
        FederatedCompletedAuthorization completed = authorizations.consumeCompletion(
                proof.authorizationId(),
                proof.browserBinding(),
                FederatedAuthorizationPurpose.LOGIN);
        if (completed == null) {
            throw new BusinessException(FederatedErrorCode.AUTHORIZATION_UNAVAILABLE);
        }
        FederatedProvider provider = providers.activeByCode(completed.providerCode());
        LOG.trace(
                "联邦认证结果已消费 | providerCode={} purpose={}",
                provider.code(),
                completed.purpose());
        FederatedAccountResolution resolution = accounts.resolve(provider, completed.profile());
        if (expectedAccount != null && expectedAccount.accountId() != resolution.account().accountId()) {
            throw new BusinessException(FederatedErrorCode.AUTHORIZATION_REJECTED);
        }
        Authentication authentication = new Authentication(
                resolution.account().accountId(),
                CODE,
                FACTORS,
                clock.instant());
        return new VerifiedAuthentication(
                resolution.account(),
                authentication,
                Long.toString(resolution.identityId()),
                new Version(resolution.identityVersion()),
                null);
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        long identityId;
        try {
            identityId = Long.parseLong(authentication.reference());
        } catch (NumberFormatException exception) {
            throw new AuthFederationException("联邦认证引用不是有效身份标识", exception);
        }
        accounts.verifyCurrentBinding(
                account,
                new FederatedAccountResolution(
                        account,
                        identityId,
                        authentication.version().value()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void eraseAccount(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        identityRepository.erase(accountId);
        LOG.debug("联邦身份 Credential 已擦除 | accountId={}", accountId);
    }
}
