package com.sca4d.auth.hmac;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.security.EncryptedSecret;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** 隔离 HMAC 认证方法对 MyBatis、状态字段和影响行数的依赖。 */
public class HmacCredentialRepository {

    private final @NotNull HmacCredentialMapper mapper;

    /** @param mapper HMAC Credential Mapper */
    public HmacCredentialRepository(@NotNull HmacCredentialMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "HMAC Credential Mapper 不能为空");
    }

    /** @return Account 当前 Credential；从未签发时为空 */
    public @Nullable HmacCredential find(long accountId) {
        return mapper.selectByAccountId(accountId);
    }

    /** 创建 Account 首个 HMAC Credential。 */
    @Transactional
    @AffectedRows(target = "HMAC Credential", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public @NotNull HmacCredential create(long accountId, @NotNull EncryptedSecret encrypted) {
        EncryptedSecret checked = Objects.requireNonNull(encrypted, "加密 HMAC Key 不能为空");
        mapper.insert(accountId, checked.keyId(), checked.ciphertext());
        return loadAfterWrite(accountId);
    }

    /** 按版本轮换或重新签发 HMAC Credential。 */
    @Transactional
    @AffectedRows(target = "HMAC Credential", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public @NotNull HmacCredential replace(
            @NotNull HmacCredential current,
            @NotNull EncryptedSecret encrypted,
            @NotNull HmacCredential.Status status,
            @NotNull Instant changedAt) {
        EncryptedSecret checked = Objects.requireNonNull(encrypted, "加密 HMAC Key 不能为空");
        mapper.update(
                current.accountId(),
                current.version(),
                current.status(),
                Patch.set(checked.keyId()),
                Patch.set(checked.ciphertext()),
                Patch.set(status),
                status == HmacCredential.Status.REVOKED ? Patch.set(changedAt) : Patch.clear());
        return loadAfterWrite(current.accountId());
    }

    /** 按预期状态保存目标状态及对应撤销时间；条件不匹配返回 false。 */
    public boolean updateStatus(
            long accountId,
            @NotNull HmacCredential.Status expectedStatus,
            @NotNull HmacCredential.Status status,
            @NotNull Instant changedAt) {
        return AffectedRowCounts.changed(
                mapper.update(
                        accountId,
                        null,
                        expectedStatus,
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.set(status),
                        status == HmacCredential.Status.REVOKED ? Patch.set(changedAt) : Patch.clear()),
                "修改 HMAC Credential 状态");
    }

    /** 幂等删除 Account 保存的加密 HMAC Key 和状态。 */
    @AffectedRows(target = "删除 HMAC Credential", whenZero = ZeroRowPolicy.ALLOW)
    public void erase(long accountId) {
        mapper.deleteByAccountId(accountId);
    }

    /** 读取刚完成写入的 Credential。 */
    private @NotNull HmacCredential loadAfterWrite(long accountId) {
        return Objects.requireNonNull(find(accountId), "写入后的 HMAC Credential 不存在");
    }
}
