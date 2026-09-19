package com.sca4d.auth.passkey.credential;

import com.sca4d.auth.passkey.credential.mapper.PasskeyAccountState;
import com.sca4d.auth.passkey.credential.mapper.PasskeyCredentialMapper;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 隔离 Passkey Credential 业务与 MyBatis，并通过账号锚点乐观版本执行并发数量限制。
 */
public class PasskeyCredentialRepository {

    private final PasskeyCredentialMapper mapper;

    /**
     * 创建 Passkey Credential Repository。
     *
     * @param mapper 执行 Passkey SQL 的 Mapper
     */
    public PasskeyCredentialRepository(@NotNull PasskeyCredentialMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Passkey Credential Mapper 不能为空");
    }

    /**
     * 在账号尚无 Passkey 锚点时保存调用方生成的 user handle，并返回数据库中的稳定值。
     *
     * @param accountId  目标账号
     * @param userHandle registration Service 生成的不透明 user handle
     * @return 并发写入完成后数据库为账号保存的 user handle
     */
    @Transactional
    public byte[] createUserHandleIfAbsent(long accountId, byte[] userHandle) {
        requireAccountId(accountId);
        AffectedRowCounts.requireSingle(
                mapper.insertAccountIfAbsent(accountId, requireBytes(userHandle, "Passkey user handle")),
                ZeroRowPolicy.ALLOW,
                "Passkey 账号锚点");
        PasskeyAccountState stored = mapper.selectAccount(accountId, null);
        if (stored == null) {
            throw new IllegalStateException("Passkey user handle 写入后不存在");
        }
        return stored.userHandle();
    }

    /**
     * 按账号读取 user handle；账号尚未开始 Passkey registration 时返回 {@code null}。
     */
    public byte @Nullable [] findUserHandle(long accountId) {
        requireAccountId(accountId);
        PasskeyAccountState account = mapper.selectAccount(accountId, null);
        return account == null ? null : account.userHandle();
    }

    /**
     * 按 user handle 定位账号；不存在时返回 {@code null}。
     */
    public @Nullable Long findAccountId(byte[] userHandle) {
        PasskeyAccountState account = mapper.selectAccount(null, requireBytes(userHandle, "Passkey user handle"));
        return account == null ? null : account.accountId();
    }

    /**
     * 返回账号当前全部有效 Passkey Credential。
     */
    public @NotNull List<PasskeyCredential> findAll(long accountId) {
        requireAccountId(accountId);
        return mapper.selectCredentials(accountId, null, null);
    }

    /**
     * 按 WebAuthn credential ID 读取有效 Credential；不存在时返回 {@code null}。
     */
    public @Nullable PasskeyCredential findByCredentialId(byte[] credentialId) {
        return mapper.selectCredentials(null, requireBytes(credentialId, "Passkey credential ID"), null)
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 同时按 WebAuthn credential ID 和 user handle 读取有效 Credential。
     */
    public @Nullable PasskeyCredential findByCredentialIdAndUserHandle(
            byte[] credentialId,
            byte[] userHandle) {
        return mapper.selectCredentials(
                null,
                requireBytes(credentialId, "Passkey credential ID"),
                requireBytes(userHandle, "Passkey user handle")).stream().findFirst().orElse(null);
    }

    /**
     * 尝试通过账号锚点乐观版本抢占一个 Credential 名额。
     *
     * @param accountId          目标账号
     * @param maximumCredentials App 为每个账号配置的上限
     * @return 本次条件更新的持久化结果；调用方根据结果决定是否重试或返回业务错误
     */
    public @NotNull CredentialSlotReservation reserveCredentialSlot(
            long accountId,
            int maximumCredentials) {
        requireAccountId(accountId);
        if (maximumCredentials < 1) {
            throw new IllegalArgumentException("Passkey Credential 上限必须为正数");
        }

        PasskeyAccountState account = mapper.selectAccount(accountId, null);
        if (account == null) {
            return CredentialSlotReservation.ACCOUNT_MISSING;
        }
        boolean updated = AffectedRowCounts.changed(
                mapper.updateAccountVersionIfBelowCredentialLimit(
                        accountId,
                        account.version().value(),
                        maximumCredentials),
                "抢占 Passkey Credential 名额");
        if (updated) {
            return CredentialSlotReservation.RESERVED;
        }
        return mapper.countByAccountId(accountId) >= maximumCredentials
                ? CredentialSlotReservation.LIMIT_REACHED
                : CredentialSlotReservation.CONCURRENT_CHANGE;
    }

    /**
     * 写入完成验证的 registration 结果；credential ID 已存在时返回 {@code null}。
     *
     * @param credential 待保存的 Credential
     * @return 数据库保存后的 Credential，唯一键冲突时返回 {@code null}
     */
    @Transactional
    public @Nullable PasskeyCredential insert(@NotNull PasskeyCredential credential) {
        PasskeyCredential checked = Objects.requireNonNull(credential, "Passkey Credential 不能为空");
        Long id = mapper.insertCredentialIfAbsent(
                checked.accountId(),
                checked.credentialId(),
                checked.publicKeyCose(),
                checked.signatureCount(),
                checked.transports(),
                checked.backupEligible(),
                checked.backedUp(),
                checked.name());
        if (id == null) {
            return null;
        }
        PasskeyCredential saved = findByCredentialId(checked.credentialId());
        if (saved == null || saved.id() != id) {
            throw new IllegalStateException("Passkey Credential 写入后不存在");
        }
        return saved;
    }

    /**
     * 按读取版本和旧计数器原子保存 assertion 结果。
     *
     * @return 更新后的 Credential；条件不满足或状态已经变化时返回 {@code null}
     */
    @Transactional
    public @Nullable PasskeyCredential advanceSignatureCount(
            @NotNull PasskeyCredential credential,
            long newSignatureCount,
            boolean backedUp,
            @NotNull Instant usedAt) {
        PasskeyCredential checked = Objects.requireNonNull(credential, "Passkey Credential 不能为空");

        // 同时比较 Credential 版本和旧计数器，阻止并发 assertion 覆盖已经推进的状态。
        boolean changed = AffectedRowCounts.changed(
                mapper.updateSignatureCountIfVersionAndCountMatch(
                        checked.id(),
                        checked.version(),
                        checked.signatureCount(),
                        newSignatureCount,
                        backedUp,
                        Objects.requireNonNull(usedAt, "Passkey 使用时间不能为空")),
                "推进 Passkey 签名计数器");
        if (!changed) {
            return null;
        }
        PasskeyCredential updated = findByCredentialId(checked.credentialId());
        if (updated == null) {
            throw new IllegalStateException("Passkey Credential 更新后不存在");
        }
        return updated;
    }

    /**
     * 抢占账号锚点版本并删除 Credential；并发变化或删除后少于最小数量时拒绝写入。
     *
     * @param accountId                   Credential 所属账号
     * @param credentialId                模块内部 Credential 标识
     * @param expectedVersion             读取 Credential 时得到的乐观版本
     * @param minimumRemainingCredentials 删除完成后至少保留的有效 Credential 数量
     * @throws OptimisticLockException 账号或 Credential 已并发变化，或删除后数量不足时抛出
     */
    @Transactional
    public void delete(
            long accountId,
            long credentialId,
            @NotNull Version expectedVersion,
            int minimumRemainingCredentials) {
        requireAccountId(accountId);
        if (credentialId <= 0) {
            throw new IllegalArgumentException("Passkey Credential 标识必须为正数");
        }
        if (minimumRemainingCredentials < 0) {
            throw new IllegalArgumentException("Passkey 最小保留数量不能为负数");
        }

        // 读取账号锚点版本，并在同一条写入语句中复核归属、数量和两个乐观版本。
        PasskeyAccountState account = mapper.selectAccount(accountId, null);
        if (account == null) {
            throw new OptimisticLockException("Passkey 账号锚点");
        }
        AffectedRowCounts.requireSingle(
                mapper.updateDeletedIfAccountAndCredentialVersionMatch(
                        accountId,
                        credentialId,
                        Objects.requireNonNull(expectedVersion, "Passkey 版本不能为空"),
                        account.version().value(),
                        minimumRemainingCredentials),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "Passkey Credential");
    }

    /**
     * 幂等删除账号的全部 Passkey 数据，供不可恢复的账号删除流程调用。
     */
    public void erase(long accountId) {
        requireAccountId(accountId);
        AffectedRowCounts.requireSingle(mapper.deleteByAccountId(accountId), ZeroRowPolicy.ALLOW, "删除 Passkey 数据");
    }

    /** 描述一次 Credential 容量条件更新的数据库结果。 */
    public enum CredentialSlotReservation {
        /** 账号锚点版本已推进，调用方可以写入一个 Credential。 */
        RESERVED,
        /** 当前有效 Credential 数量已经达到配置上限。 */
        LIMIT_REACHED,
        /** 账号锚点版本被其他事务修改，调用方可以重新读取后重试。 */
        CONCURRENT_CHANGE,
        /** 账号尚无 Passkey 锚点或锚点已经被删除。 */
        ACCOUNT_MISSING
    }

    /**
     * 复制并校验查询使用的二进制字段。
     */
    private static byte[] requireBytes(byte[] value, String name) {
        byte[] checked = Objects.requireNonNull(value, name + "不能为空").clone();
        if (checked.length == 0) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return checked;
    }

    /**
     * 校验 Account 标识可以用于 Passkey 数据查询和写入。
     */
    private static void requireAccountId(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
    }
}
