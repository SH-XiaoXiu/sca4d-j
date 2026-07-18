package com.sca4d.identity.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamIdentityProviderEntity;
import com.sca4d.identity.IdentityClientStateTargets;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.mapper.IdentityProviderMapper;
import com.sca4d.mapper.LdapProviderMapper;
import com.sca4d.mapper.Oauth2ProviderMapper;
import com.sca4d.mapper.OidcProviderMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 读写 IdentityProvider 通用字段，并隐藏作用域条件、唯一索引和乐观锁结果。 */
@Repository
@RequiredArgsConstructor
public class IdentityProviderRepository {

    private final IdentityProviderMapper mapper;
    private final OidcProviderMapper oidcProviderMapper;
    private final Oauth2ProviderMapper oauth2ProviderMapper;
    private final LdapProviderMapper ldapProviderMapper;

    /** @return 指定作用域的全部未删除 Provider，固定执行一条单表 SQL */
    public @NotNull List<IdentityProvider> findProviders(@NotNull IdentityProviderScope scope) {
        QueryWrapper query = providersIn(scope)
                .orderBy(IamIdentityProviderEntity::getName)
                .asc()
                .orderBy(IamIdentityProviderEntity::getId)
                .asc();
        return mapper.selectListByQuery(query).stream().map(this::provider).toList();
    }

    /** @return 指定作用域中未删除的 Provider；不存在时返回 {@code null} */
    public @Nullable IdentityProvider findProvider(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        QueryWrapper query = providersIn(scope)
                .and(IamIdentityProviderEntity::getId)
                .eq(providerId);
        IamIdentityProviderEntity entity = mapper.selectOneByQuery(query);
        return entity == null ? null : provider(entity);
    }

    /** @return 指定作用域中使用不可变 code 的未删除 Provider；不存在时返回 {@code null} */
    public @Nullable IdentityProvider findProvider(
            @NotNull IdentityProviderScope scope, @NotNull String code) {
        QueryWrapper query = providersIn(scope)
                .and(IamIdentityProviderEntity::getCode)
                .eq(code);
        IamIdentityProviderEntity entity = mapper.selectOneByQuery(query);
        return entity == null ? null : provider(entity);
    }

    /**
     * 写入默认停用的 Provider 通用字段。
     *
     * @throws BizException 同一作用域已经存在相同 code 时抛出
     */
    public void createProvider(
            @NotNull Long providerId,
            @NotNull IdentityProviderScope scope,
            @NotNull String code,
            @NotNull String name,
            @NotNull IdentityProviderProtocol protocol,
            @NotNull IdentityProviderRegistrationPolicy registrationPolicy,
            boolean emailAutoLink) {
        try {
            mapper.insertProvider(new IamIdentityProviderEntity()
                    .setId(providerId)
                    .setScopeType(scope.getScopeType())
                    .setScopeId(scope.getScopeId())
                    .setCode(code)
                    .setName(name)
                    .setProtocol(protocol)
                    .setRegistrationPolicy(registrationPolicy)
                    .setEmailAutoLink(emailAutoLink)
                    .setStatus(IdentityProviderStatus.DISABLED));
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(
                    exception, "23505", "uk_iam_identity_provider_global_code")
                    || PostgresConstraintViolation.matches(
                            exception, "23505", "uk_iam_identity_provider_tenant_code")) {
                throw new BizException(IdentityProviderErrorCode.PROVIDER_CODE_EXISTS);
            }
            throw exception;
        }
    }

    /** 原子修改可变通用字段，并把影响行数转换为 412 并发冲突。 */
    public void updateProvider(
            @NotNull Long providerId,
            @NotNull IdentityProviderScope scope,
            @NotNull String name,
            @NotNull IdentityProviderRegistrationPolicy registrationPolicy,
            boolean emailAutoLink,
            long version) {
        if (mapper.updateProvider(
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                name,
                registrationPolicy,
                emailAutoLink,
                version) != 1) {
            throw changed();
        }
    }

    /** 原子推进启停状态，并把作用域、状态或版本变化转换为 412 并发冲突。 */
    public void updateStatus(
            @NotNull Long providerId,
            @NotNull IdentityProviderScope scope,
            @NotNull IdentityProviderStatus expectedStatus,
            @NotNull IdentityProviderStatus nextStatus,
            long version) {
        if (mapper.updateStatus(
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                expectedStatus,
                nextStatus,
                version) != 1) {
            throw changed();
        }
    }

    /**
     * 在 Provider 仍停用且协议、作用域和版本未变化时推进聚合版本。
     *
     * <p>协议 Repository 的写入与本方法处于同一事务；协议写入失败时父 Provider 版本一起回滚。
     */
    public void updateConfigurationVersion(
            @NotNull Long providerId,
            @NotNull IdentityProviderScope scope,
            @NotNull IdentityProviderProtocol protocol,
            long version) {
        if (mapper.updateConfigurationVersion(
                providerId, scope.getScopeType(), scope.getScopeId(), protocol, version) != 1) {
            throw changed();
        }
    }

    /** @return Provider 当前是否仍关联未删除 ExternalIdentity */
    public boolean hasExternalIdentities(@NotNull Long providerId) {
        return mapper.countExternalIdentities(providerId) > 0;
    }

    /**
     * 删除当前协议子表并在停用、无关联且版本匹配时逻辑删除父 Provider。
     *
     * <p>调用方必须在同一事务中执行；父记录条件更新失败时，协议子表删除随事务回滚。
     */
    public void deleteProvider(
            @NotNull Long providerId,
            @NotNull IdentityProviderScope scope,
            @NotNull IdentityProviderProtocol protocol,
            long version) {
        switch (protocol) {
            case OIDC -> oidcProviderMapper.deleteById(providerId);
            case OAUTH2 -> oauth2ProviderMapper.deleteById(providerId);
            case LDAP -> ldapProviderMapper.deleteById(providerId);
        }
        if (mapper.markDeleted(
                providerId, scope.getScopeType(), scope.getScopeId(), version) != 1) {
            throw changed();
        }
    }

    /** @return 管理查询允许读取且不包含协议秘密的固定列 */
    private @NotNull QueryWrapper commonColumns() {
        return QueryWrapper.create().select(
                column(IamIdentityProviderEntity::getId),
                column(IamIdentityProviderEntity::getScopeType),
                column(IamIdentityProviderEntity::getScopeId),
                column(IamIdentityProviderEntity::getCode),
                column(IamIdentityProviderEntity::getName),
                column(IamIdentityProviderEntity::getProtocol),
                column(IamIdentityProviderEntity::getRegistrationPolicy),
                column(IamIdentityProviderEntity::getEmailAutoLink),
                column(IamIdentityProviderEntity::getStatus),
                column(IamIdentityProviderEntity::getVersion));
    }

    /** @return 同时包含通用安全列和精确 GLOBAL/TENANT 作用域条件的查询 */
    private @NotNull QueryWrapper providersIn(@NotNull IdentityProviderScope scope) {
        QueryWrapper query = commonColumns()
                .where(IamIdentityProviderEntity::getScopeType)
                .eq(scope.getScopeType());
        if (scope.getScopeId() == null) {
            query.and(IamIdentityProviderEntity::getScopeId).isNull();
        } else {
            query.and(IamIdentityProviderEntity::getScopeId).eq(scope.getScopeId());
        }
        return query.and(IamIdentityProviderEntity::getDeleted).eq(0);
    }

    /** @return 持久化实体对应的安全管理对象 */
    private @NotNull IdentityProvider provider(@NotNull IamIdentityProviderEntity entity) {
        IdentityProviderScope scope = new IdentityProviderScope(entity.getScopeType(), entity.getScopeId());
        return new IdentityProvider(
                entity.getId(),
                scope,
                entity.getCode(),
                entity.getName(),
                entity.getProtocol(),
                entity.getRegistrationPolicy(),
                entity.getEmailAutoLink(),
                entity.getStatus(),
                entity.getVersion());
    }

    /** @return IdentityProvider 列表刷新目标对应的统一并发异常 */
    private @NotNull ConcurrencyConflictException changed() {
        return new ConcurrencyConflictException(
                IdentityProviderErrorCode.PROVIDER_CHANGED,
                IdentityClientStateTargets.PROVIDERS);
    }
}
