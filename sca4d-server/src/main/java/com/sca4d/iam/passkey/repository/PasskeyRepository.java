package com.sca4d.iam.passkey.repository;

import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.web.ClientStateTarget;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamPasskeyEntity;
import com.sca4d.entity.IamPasskeyUserHandleEntity;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.passkey.PasskeySecurityDetails;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.mapper.PasskeyMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Passkey user handle、公共 Credential、公钥和认证器计数的持久化操作。 */
@Repository
@RequiredArgsConstructor
public class PasskeyRepository {

    private final PasskeyMapper mapper;

    /** @return Account 已保存的 WebAuthn user handle；尚未注册 Passkey 时为 {@code null} */
    public byte @Nullable [] findUserHandle(@NotNull Long accountId) {
        IamPasskeyUserHandleEntity stored = mapper.selectUserHandle(accountId);
        return stored == null ? null : stored.getUserHandle();
    }

    /**
     * 在同一事务中写入 Account user handle、公共 Credential 和 Passkey 协议子表。
     *
     * @throws BizException WebAuthn credential ID 已被其他 Account 注册时抛出
     */
    @Transactional
    public void save(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            byte @NotNull [] userHandle,
            byte @NotNull [] passkeyCredentialId,
            byte @NotNull [] publicKeyCose,
            long signatureCount,
            byte @NotNull [] aaguid,
            boolean backupEligible,
            boolean backupState,
            @NotNull String displayName) {
        try {
            mapper.insertUserHandle(new IamPasskeyUserHandleEntity()
                    .setAccountId(accountId)
                    .setUserHandle(userHandle));
        } catch (DataIntegrityViolationException exception) {
            if (!PostgresConstraintViolation.matches(
                    exception, "23505", "uk_iam_passkey_user_handle")) {
                throw exception;
            }
            throw new BizException(IamErrorCode.PASSKEY_USER_HANDLE_CONFLICT);
        }
        IamPasskeyUserHandleEntity stored = mapper.selectUserHandle(accountId);
        byte[] storedUserHandle = stored == null ? null : stored.getUserHandle();
        if (storedUserHandle == null || !Arrays.equals(storedUserHandle, userHandle)) {
            // 并发首次注册只能沿用数据库已经选定的 handle，不能把另一 ceremony 的公钥绑定到该 Account。
            throw new BizException(IamErrorCode.PASSKEY_USER_HANDLE_CONFLICT);
        }
        mapper.insertCredential(new IamCredentialEntity()
                .setId(credentialId)
                .setAccountId(accountId)
                .setCredentialType(CredentialType.PASSKEY)
                .setStatus(CredentialStatus.ACTIVE));
        try {
            mapper.insertPasskey(new IamPasskeyEntity()
                    .setCredentialId(credentialId)
                    .setPasskeyCredentialId(passkeyCredentialId)
                    .setPublicKeyCose(publicKeyCose)
                    .setSignatureCount(signatureCount)
                    .setAaguid(aaguid)
                    .setBackupEligible(backupEligible)
                    .setBackupState(backupState)
                    .setDisplayName(displayName));
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(
                    exception, "23505", "uk_iam_passkey_credential_id")) {
                throw new BizException(IamErrorCode.PASSKEY_CREDENTIAL_ALREADY_REGISTERED);
            }
            throw exception;
        }
    }

    /** @return Account 的全部未删除 Passkey；一次查询同时返回 WebAuthn 和认证状态 */
    public @NotNull List<PasskeyRow> findByAccount(@NotNull Long accountId) {
        return mapper.selectByAccount(accountId);
    }

    /**
     * 批量读取账号安全页需要的 Passkey 展示字段，不读取公钥、credential ID 或 user handle。
     *
     * @return 以 Account 主键分组的 Passkey；输入为空时不执行 SQL
     */
    public @NotNull Map<Long, List<PasskeySecurityDetails>> findSecurityByAccounts(
            @NotNull List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectSecurityByAccounts(accountIds).stream()
                .map(row -> new PasskeySecurityDetails(
                        row.getCredentialId(),
                        row.getAccountId(),
                        row.getCredentialStatus(),
                        row.getCredentialCreatedAt(),
                        row.getCredentialUpdatedAt(),
                        row.getCredentialVersion(),
                        row.getPasskeyDisplayName(),
                        Boolean.TRUE.equals(row.getBackupEligible()),
                        Boolean.TRUE.equals(row.getBackupState()),
                        row.getLastUsedAt()))
                .collect(Collectors.groupingBy(
                        PasskeySecurityDetails::getAccountId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    /** @return user handle 对应的全部未删除 Passkey */
    public @NotNull List<PasskeyRow> findByUserHandle(byte @NotNull [] userHandle) {
        return mapper.selectByUserHandle(userHandle);
    }

    /** @return WebAuthn credential ID 对应的全部匹配行；正常数据库状态下最多一行 */
    public @NotNull List<PasskeyRow> findByPasskeyCredentialId(
            byte @NotNull [] passkeyCredentialId) {
        return mapper.selectByPasskeyCredentialId(passkeyCredentialId);
    }

    /** @return credential ID 与 user handle 同时匹配的 Passkey；不存在时为 {@code null} */
    public @Nullable PasskeyRow find(
            byte @NotNull [] passkeyCredentialId, byte @NotNull [] userHandle) {
        return mapper.selectByPasskeyCredentialIdAndUserHandle(
                passkeyCredentialId, userHandle);
    }

    /**
     * 使用注册时读取的 Credential 版本和 signCount 原子保存 assertion 结果。
     *
     * @return 更新后的公共 Credential 版本；并发 assertion 已先更新时为 {@code null}
     */
    public @Nullable Long advanceAssertion(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            long expectedSignatureCount,
            long signatureCount,
            boolean backupEligible,
            boolean backupState,
            @NotNull Long version) {
        int affected = mapper.advanceAssertion(
                credentialId,
                accountId,
                expectedSignatureCount,
                signatureCount,
                backupEligible,
                backupState,
                version);
        return affected == 1 ? version + 1 : null;
    }

    /** @return Account 是否存在未删除的 ACTIVE Passkey，不读取公钥和 credential ID */
    public boolean hasActiveCredential(
            @NotNull Long accountId, @Nullable Long excludedCredentialId) {
        return mapper.countActivePasskeys(accountId, excludedCredentialId) > 0;
    }

    /**
     * 按 PASSKEY 类型、Account 归属和版本逻辑删除公共 Credential。
     *
     * @throws ConcurrencyConflictException 类型、归属、删除状态或版本变化时抛出
     */
    public void delete(
            @NotNull Long credentialId, @NotNull Long accountId, @NotNull Long version) {
        if (mapper.deletePasskey(credentialId, accountId, version) == 0) {
            throw new ConcurrencyConflictException(
                    IamErrorCode.PASSKEY_CREDENTIAL_CHANGED, ClientStateTarget.resource(IamResourceTypes.CREDENTIAL, credentialId));
        }
    }
}
