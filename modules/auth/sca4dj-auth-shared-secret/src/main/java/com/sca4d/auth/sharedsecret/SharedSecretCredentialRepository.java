package com.sca4d.auth.sharedsecret;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** 隔离 Shared Secret 认证方法对 MyBatis、状态字段和影响行数的依赖。 */
public class SharedSecretCredentialRepository {

    private final @NotNull SharedSecretCredentialMapper mapper;

    /** @param mapper Shared Secret SQL Mapper */
    public SharedSecretCredentialRepository(@NotNull SharedSecretCredentialMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Shared Secret Mapper 不能为空");
    }

    /** @return Account 当前凭据；从未签发时为空 */
    public @Nullable SharedSecretCredential find(long accountId) {
        return mapper.selectByAccountId(accountId);
    }

    /** 创建 Account 首个凭据。 */
    @Transactional
    @AffectedRows(target = "Shared Secret 凭据", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public @NotNull SharedSecretCredential create(long accountId, @NotNull String secretHash) {
        mapper.insert(accountId, Objects.requireNonNull(secretHash, "Shared Secret 摘要不能为空"));
        return loadAfterWrite(accountId);
    }

    /** 按状态和版本轮换或重新签发凭据。 */
    @Transactional
    @AffectedRows(target = "Shared Secret 凭据", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public @NotNull SharedSecretCredential replace(
            @NotNull SharedSecretCredential current,
            @NotNull String secretHash,
            @NotNull SharedSecretCredential.Status status,
            @NotNull Instant changedAt) {
        mapper.update(
                current.accountId(),
                current.version(),
                current.status(),
                Patch.set(secretHash),
                Patch.set(status),
                status == SharedSecretCredential.Status.REVOKED ? Patch.set(changedAt) : Patch.clear());
        return loadAfterWrite(current.accountId());
    }

    /** 按预期状态保存目标状态及对应撤销时间；条件不匹配返回 false。 */
    public boolean updateStatus(
            long accountId,
            @NotNull SharedSecretCredential.Status expectedStatus,
            @NotNull SharedSecretCredential.Status status,
            @NotNull Instant changedAt) {
        return AffectedRowCounts.changed(
                mapper.update(
                        accountId,
                        null,
                        expectedStatus,
                        Patch.ignore(),
                        Patch.set(status),
                        status == SharedSecretCredential.Status.REVOKED ? Patch.set(changedAt) : Patch.clear()),
                "修改 Shared Secret 凭据状态");
    }

    /** 幂等删除 Account 保存的 Shared Secret 摘要和状态。 */
    @AffectedRows(target = "删除 Shared Secret 凭据", whenZero = ZeroRowPolicy.ALLOW)
    public void erase(long accountId) {
        mapper.deleteByAccountId(accountId);
    }

    /** 读取刚完成写入的凭据。 */
    private @NotNull SharedSecretCredential loadAfterWrite(long accountId) {
        return Objects.requireNonNull(find(accountId), "写入后的 Shared Secret 凭据不存在");
    }
}
