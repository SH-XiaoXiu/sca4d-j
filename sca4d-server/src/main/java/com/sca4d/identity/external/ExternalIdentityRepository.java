package com.sca4d.identity.external;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamExternalIdentityEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.client.IamClientStateTargets;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.external.repository.ExternalIdentityRow;
import com.sca4d.mapper.ExternalIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 读写 ExternalIdentity 绑定，并把 Provider、Account、Subject 当前状态合并为一个查询结果。 */
@Repository
@RequiredArgsConstructor
public class ExternalIdentityRepository {

    private final ExternalIdentityMapper mapper;
    private final IdGenerator idGenerator;

    /** @return Provider 内指定 external subject 的绑定；不存在时返回 null */
    public @Nullable ExternalIdentityLink findLink(
            @NotNull Long providerId, @NotNull String externalSubject) {
        return link(mapper.selectLink(providerId, externalSubject, null));
    }

    /** @return 指定 ExternalIdentity 的完整绑定；不存在时返回 null */
    public @Nullable ExternalIdentityLink findLink(@NotNull Long identityId) {
        return link(mapper.selectLink(null, null, identityId));
    }

    /**
     * 在 Provider 与 Account 可认证且作用域一致时创建绑定。
     *
     * @throws BizException external subject 或 Account 已绑定当前 Provider 时抛出
     */
    public @NotNull ExternalIdentityLink create(
            @NotNull Long providerId,
            @NotNull Long accountId,
            @NotNull ExternalIdentityProfile profile) {
        Long identityId = idGenerator.nextId();
        try {
            int inserted = mapper.insertLink(new IamExternalIdentityEntity()
                    .setId(identityId)
                    .setProviderId(providerId)
                    .setAccountId(accountId)
                    .setExternalSubject(profile.getExternalSubject())
                    .setDisplayName(profile.getDisplayName())
                    .setEmail(profile.getEmail())
                    .setEmailVerified(profile.getEmailVerified())
                    .setAvatarUri(profile.getAvatarUri()));
            if (inserted != 1) {
                throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
            }
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(
                    exception, "23505", "uk_iam_external_identity_provider_subject")) {
                throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
            }
            if (PostgresConstraintViolation.matches(
                    exception, "23505", "uk_iam_external_identity_provider_account")) {
                throw new BizException(IdentityProviderErrorCode.EXTERNAL_ACCOUNT_ALREADY_LINKED);
            }
            throw exception;
        }
        ExternalIdentityLink created = findLink(identityId);
        if (created == null) {
            throw new IllegalStateException("ExternalIdentity 创建后无法读取: " + identityId);
        }
        return created;
    }

    /**
     * 更新一次成功认证返回的资料和时间，不推进表示绑定关系的版本。
     *
     * @return 更新后的完整绑定
     */
    public @NotNull ExternalIdentityLink recordAuthentication(
            @NotNull Long identityId,
            @NotNull Long providerId,
            @NotNull Long version,
            @NotNull ExternalIdentityProfile profile) {
        if (mapper.updateAuthentication(identityId, providerId, version, profile) != 1) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
        }
        ExternalIdentityLink updated = findLink(identityId);
        if (updated == null) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
        }
        return updated;
    }

    /** @return 指定 Account 当前全部 ExternalIdentity 及 Provider 摘要 */
    public @NotNull List<ExternalIdentityDetails> findByAccount(
            @NotNull Long accountId) {
        return mapper.selectByAccount(accountId).stream()
                .map(row -> new ExternalIdentityDetails(
                        new ExternalIdentity(
                                row.getIdentityId(),
                                row.getProviderId(),
                                row.getAccountId(),
                                new ExternalIdentityProfile(
                                        row.getExternalSubject(),
                                        row.getExternalDisplayName(),
                                        row.getEmail(),
                                        row.getEmailVerified(),
                                        row.getAvatarUri()),
                                row.getLastAuthenticatedAt(),
                                row.getIdentityVersion()),
                        row.getProviderCode(),
                        row.getProviderName(),
                        row.getProviderProtocol(),
                        row.getProviderStatus()))
                .toList();
    }

    /** @return 排除当前绑定后，Account 是否仍能通过其他 ACTIVE Provider 登录 */
    public boolean hasOtherActiveLoginIdentity(
            @NotNull Long accountId, @NotNull Long excludedIdentityId) {
        return mapper.existsOtherActiveByAccount(accountId, excludedIdentityId);
    }

    /** 使用 Account 和强版本条件解除一个 ExternalIdentity 关联。 */
    public void delete(
            @NotNull Long identityId,
            @NotNull Long accountId,
            @NotNull Long version) {
        if (mapper.deleteLink(identityId, accountId, version) != 1) {
            throw new ConcurrencyConflictException(
                    IdentityProviderErrorCode.EXTERNAL_IDENTITY_CHANGED,
                    IamClientStateTargets.ACCOUNTS);
        }
    }

    /** @return Mapper 查询结果对应的绑定对象；没有结果时返回 null */
    private @Nullable ExternalIdentityLink link(@Nullable ExternalIdentityRow row) {
        if (row == null) {
            return null;
        }
        ExternalIdentity identity = new ExternalIdentity(
                row.getIdentityId(),
                row.getProviderId(),
                row.getAccountId(),
                new ExternalIdentityProfile(
                        row.getExternalSubject(),
                        row.getExternalDisplayName(),
                        row.getEmail(),
                        row.getEmailVerified(),
                        row.getAvatarUri()),
                row.getLastAuthenticatedAt(),
                row.getIdentityVersion());
        IamSubjectEntity subject = new IamSubjectEntity().setId(row.getSubjectId()).setDisplayName(row.getSubjectDisplayName())
                .setStatus(row.getSubjectStatus()).setVersion(row.getSubjectVersion());
        IamAccountEntity account = new IamAccountEntity().setId(row.getAccountId()).setSubjectId(row.getSubjectId()).setScopeType(row.getAccountScopeType())
                .setScopeId(row.getAccountScopeId()).setStatus(row.getAccountStatus())
                .setSecondFactorRequired(Boolean.TRUE.equals(row.getSecondFactorRequired())).setVersion(row.getAccountVersion());
        return new ExternalIdentityLink(
                identity,
                new IdentityProviderScope(row.getProviderScopeType(), row.getProviderScopeId()),
                row.getProviderStatus(),
                subject,
                account);
    }
}
