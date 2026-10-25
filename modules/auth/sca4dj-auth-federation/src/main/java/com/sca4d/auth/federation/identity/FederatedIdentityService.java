package com.sca4d.auth.federation.identity;

import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedIdentity;
import com.sca4d.auth.federation.FederatedProfile;
import com.sca4d.auth.federation.FederatedProvider;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.logging.LogMark;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 编排联邦身份绑定、资料快照刷新和解绑动作。 */
public class FederatedIdentityService {

    private static final Logger LOG = LoggerFactory.getLogger(FederatedIdentityService.class);

    private final @NotNull FederatedIdentityRepository repository;

    /** 创建联邦身份服务。 */
    public FederatedIdentityService(@NotNull FederatedIdentityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "联邦身份 Repository 不能为空");
    }

    /** @return 外部身份绑定；不存在时为空 */
    @Transactional(readOnly = true)
    public FederatedIdentity find(@NotNull FederatedProvider provider, @NotNull FederatedProfile profile) {
        return repository.find(provider, profile);
    }

    /** @return 当前账号已经绑定的全部外部身份 */
    @Transactional(readOnly = true)
    public @NotNull List<FederatedIdentity> findByAccount(long accountId) {
        List<FederatedIdentity> identities = repository.findByAccount(accountId);
        LOG.debug("账号联邦身份读取完成 | accountId={} count={}", accountId, identities.size());
        return identities;
    }

    /** 新增或明确更新当前账号的外部身份绑定。 */
    @Transactional
    public @NotNull FederatedIdentity link(
            @NotNull FederatedProvider provider,
            long accountId,
            @NotNull FederatedProfile profile) {
        FederatedIdentity existing = repository.find(provider, profile);
        if (existing != null && existing.accountId() != accountId) {
            throw new BusinessException(FederatedErrorCode.IDENTITY_CONFLICT);
        }
        FederatedIdentity linked = existing == null
                ? repository.insert(provider, accountId, profile)
                : repository.updateProfile(existing, profile);
        if (existing == null) {
            LOG.atInfo()
                    .addMarker(LogMark.AFTER_COMMIT)
                    .log("联邦身份已绑定 | identityId={} accountId={}", linked.id(), accountId);
        } else {
            LOG.debug(
                    "联邦身份绑定资料已写入事务 | identityId={} accountId={}",
                    linked.id(),
                    accountId);
        }
        return linked;
    }

    /** 更新已经绑定的资料快照。 */
    @Transactional
    public @NotNull FederatedIdentity refresh(@NotNull FederatedIdentity identity, @NotNull FederatedProfile profile) {
        FederatedIdentity refreshed = repository.updateProfile(identity, profile);
        LOG.debug(
                "联邦身份刷新资料已写入事务 | identityId={} accountId={}",
                refreshed.id(),
                refreshed.accountId());
        return refreshed;
    }

    /** 按调用方读取的绑定版本删除外部身份绑定。 */
    @Transactional
    public void unlink(long identityId, long accountId, long version) {
        repository.delete(identityId, accountId, version);
        LOG.atInfo()
                .addMarker(LogMark.AFTER_COMMIT)
                .log("联邦身份已解绑 | identityId={} accountId={}", identityId, accountId);
    }

    /** 复核认证证据仍然对应当前账号的绑定。 */
    @Transactional(readOnly = true)
    public void verifyCurrentBinding(long identityId, long accountId, long version) {
        repository.verifyCurrentBinding(identityId, accountId, version);
    }

}
