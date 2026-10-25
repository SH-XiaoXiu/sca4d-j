package com.sca4d.auth.federation.identity;

import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedIdentity;
import com.sca4d.auth.federation.FederatedProfile;
import com.sca4d.auth.federation.FederatedProvider;
import com.sca4d.auth.federation.identity.mapper.FederatedIdentityMapper;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 使用 `FederatedIdentityMapper` 保存外部身份绑定和资料快照。 */
public class FederatedIdentityRepository {

    private final @NotNull FederatedIdentityMapper mapper;

    /** 创建外部身份 Repository。 */
    public FederatedIdentityRepository(@NotNull FederatedIdentityMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Identity Mapper 不能为空");
    }

    /** @return 外部身份绑定；不存在时为空 */
    public @Nullable FederatedIdentity find(@NotNull FederatedProvider provider, @NotNull FederatedProfile profile) {
        return mapper.select(provider.id(), profile.issuer(), profile.subject(), null)
                .stream()
                .findFirst()
                .orElse(null);
    }

    /** @return Account 当前的全部外部身份绑定 */
    public @NotNull List<FederatedIdentity> findByAccount(long accountId) {
        return mapper.select(null, null, null, accountId);
    }

    /** 新增外部身份绑定。 */
    @Transactional
    public @NotNull FederatedIdentity insert(
            @NotNull FederatedProvider provider,
            long accountId,
            @NotNull FederatedProfile profile) {
        try {
            if (!AffectedRowCounts.changed(
                    mapper.insert(
                            provider.id(),
                            accountId,
                            profile.issuer(),
                            profile.subject(),
                            profile.displayName(),
                            profile.email(),
                            profile.emailVerified(),
                            profile.avatarUrl()),
                    "新增联邦身份绑定")) {
                throw new BusinessException(FederatedErrorCode.IDENTITY_CONFLICT);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(FederatedErrorCode.IDENTITY_CONFLICT, exception);
        }
        FederatedIdentity identity = find(provider, profile);
        return Objects.requireNonNull(identity, "新增的外部身份绑定不存在");
    }

    /** 刷新资料快照，不改变绑定版本，避免资料刷新让有效认证证据失效。 */
    @Transactional
    public @NotNull FederatedIdentity updateProfile(
            @NotNull FederatedIdentity identity,
            @NotNull FederatedProfile profile) {
        FederatedIdentity checkedIdentity = Objects.requireNonNull(identity, "外部身份不能为空");
        AffectedRowCounts.requireSingle(
                mapper.updateProfile(
                        checkedIdentity.id(),
                        checkedIdentity.accountId(),
                        checkedIdentity.version(),
                        Patch.<String>choose()
                                .setIf(profile.displayName() != null, profile::displayName)
                                .otherwiseClear(),
                        Patch.<String>choose().setIf(profile.email() != null, profile::email).otherwiseClear(),
                        Patch.set(profile.emailVerified()),
                        Patch.<String>choose().setIf(profile.avatarUrl() != null, profile::avatarUrl).otherwiseClear()),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "联邦身份绑定");
        return new FederatedIdentity(
                checkedIdentity.id(),
                checkedIdentity.providerId(),
                checkedIdentity.accountId(),
                profile,
                checkedIdentity.version(),
                checkedIdentity.linkedAt());
    }

    /** 按调用方读取的当前版本删除身份绑定。 */
    @AffectedRows(target = "联邦身份绑定", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public void delete(long identityId, long accountId, long fallbackVersion) {
        Version expected = new Version(fallbackVersion);
        mapper.delete(identityId, accountId, expected.value());
    }

    /** 复核身份绑定仍属于账号且版本未变化。 */
    public void verifyCurrentBinding(long identityId, long accountId, long version) {
        if (!mapper.exists(identityId, accountId, version)) {
            throw new BusinessException(FederatedErrorCode.AUTHORIZATION_REJECTED);
        }
    }

    /** 幂等删除 Account 保存的全部外部身份绑定。 */
    @Transactional
    public void erase(long accountId) {
        AffectedRowCounts.requireAtLeast(mapper.deleteByAccountId(accountId), 0, "删除账号全部联邦身份绑定");
    }

}
