package com.sca4d.auth.x509;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 隔离 X.509 认证方法对 MyBatis、状态字段和影响行数的依赖。 */
public class X509CredentialRepository {

    private final @NotNull X509CredentialMapper mapper;

    /** @param mapper X.509 SQL Mapper */
    public X509CredentialRepository(@NotNull X509CredentialMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "X.509 Credential Mapper 不能为空");
    }

    /** 保存一张外部签发的 ACTIVE X.509 公钥证书。 */
    @AffectedRows(target = "X.509 Credential", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public void add(@NotNull X509Credential credential, byte @NotNull [] certificateDer) {
        X509Credential checked = Objects.requireNonNull(credential, "X.509 Credential 不能为空");
        mapper.insert(
                checked.id(),
                checked.accountId(),
                checked.fingerprintSha256(),
                certificateDer,
                checked.serialNumber(),
                checked.subjectDn(),
                checked.notBefore(),
                checked.notAfter());
    }

    /** @return 指纹在指定时间可认证的 Credential；不存在时为空 */
    public @Nullable X509Credential findUsable(@NotNull String fingerprintSha256, @NotNull Instant now) {
        return mapper.select(null, null, fingerprintSha256, now, 1).stream().findFirst().orElse(null);
    }

    /** @return 指定 Credential；不存在时为空 */
    public @Nullable X509Credential find(long id) {
        return mapper.select(id, null, null, null, 1).stream().findFirst().orElse(null);
    }

    /** @return Account 登记过的全部 Credential */
    public @NotNull @Unmodifiable List<X509Credential> findAll(long accountId) {
        return List.copyOf(mapper.select(null, accountId, null, null, null));
    }

    /** @return Account 在指定时间至少有一张可认证 Credential 时为 `true` */
    public boolean hasUsable(long accountId, @NotNull Instant now) {
        return !mapper.select(null, accountId, null, now, 1).isEmpty();
    }

    /** 保存指定 Credential 的目标状态和撤销时间；并发变化时抛出乐观锁异常。 */
    @AffectedRows(target = "X.509 Credential", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public void updateStatus(
            @NotNull X509Credential credential,
            @NotNull X509Credential.Status status,
            @NotNull Instant changedAt) {
        X509Credential checked = Objects.requireNonNull(credential, "X.509 Credential 不能为空");
        mapper.updateStatus(
                checked.accountId(),
                checked.id(),
                checked.version(),
                checked.status(),
                Patch.set(status),
                status == X509Credential.Status.REVOKED ? Patch.set(changedAt) : Patch.clear());
    }

    /** @return 本次至少修改一张符合预期状态的 Credential 时为 `true` */
    public boolean updateAllStatuses(
            long accountId,
            @NotNull X509Credential.Status expectedStatus,
            @NotNull X509Credential.Status status,
            @NotNull Instant changedAt) {
        return AffectedRowCounts.any(
                mapper.updateStatus(
                        accountId,
                        null,
                        null,
                        expectedStatus,
                        Patch.set(status),
                        status == X509Credential.Status.REVOKED ? Patch.set(changedAt) : Patch.clear()),
                "更新 Account 的 X.509 Credential 状态");
    }

    /** 幂等删除 Account 保存的全部 X.509 公钥证书和状态。 */
    public void erase(long accountId) {
        AffectedRowCounts.requireAtLeast(mapper.deleteByAccountId(accountId), 0, "删除 X.509 Credential");
    }
}
