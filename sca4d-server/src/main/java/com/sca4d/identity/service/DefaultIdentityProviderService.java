package com.sca4d.identity.service;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.identity.IdentityClientStateTargets;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderActivationCheck;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderService;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.identity.repository.IdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** 使用乐观锁管理 Provider 通用字段，并在启用前调用所属协议的配置检查。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultIdentityProviderService implements IdentityProviderService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z](?:[a-z0-9-]{0,62}[a-z0-9])?");

    private final IdentityProviderRepository repository;
    private final List<IdentityProviderActivationCheck> activationChecks;
    private final IdGenerator idGenerator;

    @Override
    public @NotNull List<IdentityProvider> providers(@NotNull IdentityProviderScope scope) {
        return repository.findProviders(scope);
    }

    @Override
    public @NotNull IdentityProvider require(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = repository.findProvider(scope, providerId);
        if (provider == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_NOT_FOUND);
        }
        return provider;
    }

    @Override
    public @NotNull Long create(
            @NotNull IdentityProviderScope scope,
            @NotNull String code,
            @NotNull String name,
            @NotNull IdentityProviderProtocol protocol,
            @NotNull IdentityProviderRegistrationPolicy registrationPolicy,
            boolean emailAutoLink) {
        String checkedCode = code.trim();
        String checkedName = name.trim();
        if (!CODE_PATTERN.matcher(checkedCode).matches()
                || checkedName.isEmpty()
                || checkedName.length() > 128) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        Long providerId = idGenerator.nextId();
        repository.createProvider(
                providerId,
                scope,
                checkedCode,
                checkedName,
                protocol,
                registrationPolicy,
                emailAutoLink);
        log.info(
                "IdentityProvider 创建完成 | providerId={} scopeType={} scopeId={} protocol={} status={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                protocol,
                IdentityProviderStatus.DISABLED);
        return providerId;
    }

    @Override
    public void update(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull String name,
            @NotNull IdentityProviderRegistrationPolicy registrationPolicy,
            boolean emailAutoLink,
            long version) {
        IdentityProvider provider = requireVersion(scope, providerId, version);
        String checkedName = name.trim();
        if (checkedName.isEmpty() || checkedName.length() > 128) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        repository.updateProvider(
                providerId, scope, checkedName, registrationPolicy, emailAutoLink, version);
        log.info(
                "IdentityProvider 通用配置更新完成 | providerId={} scopeType={} scopeId={} protocol={} version={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                provider.getProtocol(),
                version + 1);
    }

    @Override
    public void changeStatus(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull IdentityProviderStatus status,
            long version) {
        IdentityProvider provider = requireVersion(scope, providerId, version);
        if (provider.getStatus() == status) {
            return;
        }
        if (status == IdentityProviderStatus.ACTIVE) {
            IdentityProviderActivationCheck check = activationChecks.stream()
                    .filter(candidate -> candidate.protocol() == provider.getProtocol())
                    .findFirst()
                    .orElse(null);
            if (check == null || !check.canActivate(providerId)) {
                throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_CONFIGURATION_REQUIRED);
            }
        }
        repository.updateStatus(providerId, scope, provider.getStatus(), status, version);
        log.info(
                "IdentityProvider 状态更新完成 | providerId={} scopeType={} scopeId={} protocol={} status={} version={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                provider.getProtocol(),
                status,
                version + 1);
    }

    @Override
    @Transactional
    public void delete(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId, long version) {
        IdentityProvider provider = requireVersion(scope, providerId, version);
        if (provider.getStatus() == IdentityProviderStatus.ACTIVE) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_ACTIVE);
        }
        if (repository.hasExternalIdentities(providerId)) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_IN_USE);
        }
        repository.deleteProvider(providerId, scope, provider.getProtocol(), version);
        log.info(
                "IdentityProvider 删除已写入事务 | providerId={} scopeType={} scopeId={} protocol={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                provider.getProtocol());
    }

    /** @return 版本匹配的 Provider；不匹配时返回携带刷新目标的 412 */
    private @NotNull IdentityProvider requireVersion(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId, long version) {
        IdentityProvider provider = require(scope, providerId);
        if (!provider.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    IdentityProviderErrorCode.PROVIDER_CHANGED,
                    IdentityClientStateTargets.PROVIDERS);
        }
        return provider;
    }
}
