package com.sca4d.iam.totp.repository;

import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.web.ClientStateTarget;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamTotpEntity;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.mapper.TotpMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** TOTP Credential 公共记录、加密密钥和已消费时间步仓储。 */
@Repository
@RequiredArgsConstructor
public class TotpRepository {

    private final TotpMapper mapper;

    /**
     * 在同一事务中写入公共 Credential 和 TOTP 子表。
     *
     * @param credentialId 调用方在加密前生成并写入 GCM additional authenticated data 的主键
     * @param accountId Credential 所属 Account
     * @param encryptedSecret 不包含明文的 AES-256-GCM 加密结果
     * @param acceptedStep enrollment 激活验证码已经消费的时间步
     */
    @Transactional
    public void save(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            @NotNull EncryptedSecret encryptedSecret,
            long acceptedStep) {
        try {
            mapper.insertCredential(new IamCredentialEntity()
                    .setId(credentialId)
                    .setAccountId(accountId)
                    .setCredentialType(CredentialType.TOTP)
                    .setStatus(CredentialStatus.ACTIVE));
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_credential_account_type")) {
                throw new BizException(IamErrorCode.CREDENTIAL_ALREADY_BOUND);
            }
            throw exception;
        }
        mapper.insertTotp(new IamTotpEntity()
                .setCredentialId(credentialId)
                .setSecretKeyId(encryptedSecret.getKeyId())
                .setSecretNonce(encryptedSecret.getNonce())
                .setSecretCiphertext(encryptedSecret.getCiphertext())
                .setLastAcceptedStep(acceptedStep));
    }

    /** @return 指定 Account 当前 TOTP 行；未绑定时为 {@code null} */
    public @Nullable TotpRow find(@NotNull Long accountId) {
        return mapper.selectTotp(accountId);
    }

    /**
     * 检查 Account 是否存在未删除的 ACTIVE TOTP Credential。
     *
     * @param excludedCredentialId 不计入结果的 Credential；普通检查为 {@code null}
     * @return 查询条件命中时为 {@code true}，不读取密钥密文
     */
    public boolean hasActiveCredential(
            @NotNull Long accountId, @Nullable Long excludedCredentialId) {
        return mapper.countActiveTotp(accountId, excludedCredentialId) > 0;
    }

    /**
     * 原子消费严格晚于数据库记录的 TOTP 时间步。
     *
     * @return 跨节点并发中当前时间步首次被接受时为 {@code true}
     */
    public boolean advanceAcceptedStep(
            @NotNull Long credentialId, @NotNull Long accountId, long acceptedStep) {
        return mapper.advanceAcceptedStep(credentialId, accountId, acceptedStep) == 1;
    }

    /** 原子替换密钥、激活时间步并推进 Credential 版本。 */
    @Transactional
    public void replace(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            @NotNull EncryptedSecret encryptedSecret,
            long acceptedStep,
            @NotNull Long version) {
        if (mapper.replaceTotp(
                credentialId,
                accountId,
                encryptedSecret.getKeyId(),
                encryptedSecret.getNonce(),
                encryptedSecret.getCiphertext(),
                acceptedStep,
                version) == 0) {
            throw new ConcurrencyConflictException(
                    IamErrorCode.TOTP_CREDENTIAL_CHANGED, ClientStateTarget.resource(IamResourceTypes.CREDENTIAL, credentialId));
        }
    }

    /**
     * 只按 TOTP 类型、Account 归属和版本逻辑删除公共 Credential。
     *
     * @throws ConcurrencyConflictException 主键不是 TOTP、归属不匹配、已经删除或版本变化时抛出
     */
    public void delete(
            @NotNull Long credentialId, @NotNull Long accountId, @NotNull Long version) {
        if (mapper.deleteTotp(credentialId, accountId, version) == 0) {
            throw new ConcurrencyConflictException(
                    IamErrorCode.TOTP_CREDENTIAL_CHANGED, ClientStateTarget.resource(IamResourceTypes.CREDENTIAL, credentialId));
        }
    }
}
