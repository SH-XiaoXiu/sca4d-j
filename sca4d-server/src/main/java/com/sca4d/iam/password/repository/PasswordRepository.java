package com.sca4d.iam.password.repository;

import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.web.ClientStateTarget;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamPasswordEntity;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.mapper.PasswordMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PASSWORD Credential 公共记录与 Argon2id 摘要仓储。 */
@Repository
@RequiredArgsConstructor
public class PasswordRepository {

    private final PasswordMapper mapper;
    private final IdGenerator idGenerator;

    /**
     * 在同一事务中写入公共 Credential 和 Password 子表。
     *
     * @param accountId Credential 所属 Account
     * @param passwordHash 包含参数和盐的 Argon2id 编码摘要
     * @return 新建 Credential 主键
     */
    @Transactional
    public @NotNull Long save(@NotNull Long accountId, @NotNull String passwordHash) {
        Long credentialId = idGenerator.nextId();
        try {
            mapper.insertCredential(new IamCredentialEntity()
                    .setId(credentialId)
                    .setAccountId(accountId)
                    .setCredentialType(CredentialType.PASSWORD)
                    .setStatus(CredentialStatus.ACTIVE));
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_credential_account_type")) {
                throw new BizException(IamErrorCode.CREDENTIAL_ALREADY_BOUND);
            }
            throw exception;
        }
        mapper.insertPassword(new IamPasswordEntity()
                .setCredentialId(credentialId)
                .setPasswordHash(passwordHash));
        return credentialId;
    }

    /**
     * 读取指定 Account 的 PASSWORD Credential 和认证状态。
     *
     * @param accountId 已由认证入口解析的 Account 主键
     * @return 验证需要的当前行；没有 PASSWORD Credential 时为 {@code null}
     */
    public @Nullable PasswordRow find(@NotNull Long accountId) {
        return mapper.selectPassword(accountId);
    }

    /**
     * 原子替换摘要并推进 Credential 版本。
     *
     * @param credentialId PASSWORD Credential 主键
     * @param accountId 所属 Account 主键
     * @param passwordHash 新 Argon2id 编码摘要
     * @param version 调用方读取的 Credential 版本
     * @throws ConcurrencyConflictException Credential、Password 子表、归属或版本任一不匹配时抛出
     */
    @Transactional
    public void replace(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            @NotNull String passwordHash,
            @NotNull Long version) {
        if (mapper.replacePassword(credentialId, accountId, passwordHash, version) == 0) {
            throw new ConcurrencyConflictException(
                    IamErrorCode.CREDENTIAL_CHANGED, ClientStateTarget.resource(IamResourceTypes.CREDENTIAL, credentialId));
        }
    }
}
