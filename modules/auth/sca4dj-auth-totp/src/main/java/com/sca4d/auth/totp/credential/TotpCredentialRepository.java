package com.sca4d.auth.totp.credential;

import com.sca4d.auth.totp.TotpErrorCode;
import com.sca4d.auth.totp.credential.mapper.TotpCredentialMapper;
import com.sca4d.auth.totp.credential.mapper.TotpCredentialState;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.security.EncryptedSecret;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 隔离 TOTP 服务与 MyBatis Mapper，并使用数据库原子拒绝过时验证码。 */
public class TotpCredentialRepository {

    private final TotpCredentialMapper mapper;

    /**
     * 创建 TOTP 凭据 Repository。
     *
     * @param mapper TOTP 凭据 Mapper
     */
    public TotpCredentialRepository(@NotNull TotpCredentialMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "TOTP 凭据 Mapper 不能为空");
    }

    /**
     * 查询账号当前 TOTP 凭据。
     *
     * @param accountId 目标账号
     * @return 账号当前 TOTP 凭据；未激活时为 {@code null}
     */
    public @Nullable TotpCredential findByAccountId(@NotNull Long accountId) {
        TotpCredentialState row = mapper.selectByAccountId(
                Objects.requireNonNull(accountId, "Account 标识不能为空"));
        return row == null
                ? null
                : new TotpCredential(
                        row.accountId(),
                        new EncryptedSecret(row.secretKeyId(), row.secretCiphertext()),
                        row.lastAcceptedStep(),
                        row.version());
    }

    /**
     * 写入首次激活的凭据，并保存激活验证码的时间步。
     *
     * @param accountId 凭据所属账号
     * @param secret 加密后的 TOTP 共享密钥
     * @param acceptedStep 激活时接受的验证码时间步
     * @return 新建的 TOTP 凭据
     * @throws BusinessException 账号已经存在 TOTP 凭据时抛出 CREDENTIAL_EXISTS
     */
    @Transactional
    public @NotNull TotpCredential create(
            @NotNull Long accountId,
            @NotNull EncryptedSecret secret,
            long acceptedStep) {
        Long checkedId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        EncryptedSecret checkedSecret = Objects.requireNonNull(secret, "TOTP 加密密钥不能为空");
        if (!AffectedRowCounts.changed(
                mapper.insert(
                        checkedId,
                        checkedSecret.keyId(),
                        checkedSecret.ciphertext(),
                        acceptedStep),
                "新增 TOTP 凭据")) {
            throw new BusinessException(TotpErrorCode.CREDENTIAL_EXISTS);
        }
        return Objects.requireNonNull(findByAccountId(checkedId), "新建的 TOTP 凭据不存在");
    }

    /**
     * 按凭据版本原子检查并更新验证码时间步下限。
     *
     * @param accountId 凭据所属账号
     * @param expectedVersion 认证流程读取到的凭据版本
     * @param acceptedStep 本次命中的验证码时间步
     * @return 凭据版本未变且时间步不早于已接受下限时为 {@code true}
     */
    public boolean advanceAcceptedStep(
            @NotNull Long accountId,
            @NotNull Version expectedVersion,
            long acceptedStep) {
        return AffectedRowCounts.changed(
                mapper.updateAcceptedStepIfVersion(
                        Objects.requireNonNull(accountId, "Account 标识不能为空"),
                        Objects.requireNonNull(expectedVersion, "TOTP 凭据版本不能为空"),
                        acceptedStep),
                "推进 TOTP 验证码时间步");
    }

    /**
     * 按乐观版本替换密钥，并保存激活新密钥时使用的时间步。
     *
     * @param accountId 凭据所属账号
     * @param secret 加密后的新 TOTP 共享密钥
     * @param acceptedStep 激活新密钥时接受的验证码时间步
     * @param expectedVersion Service 刚读取到的凭据版本
     * @return 替换后的 TOTP 凭据
     */
    @AffectedRows(target = "TOTP 凭据", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    @Transactional
    public @NotNull TotpCredential replace(
            @NotNull Long accountId,
            @NotNull EncryptedSecret secret,
            long acceptedStep,
            @NotNull Version expectedVersion) {
        Long checkedId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        EncryptedSecret checkedSecret = Objects.requireNonNull(secret, "TOTP 加密密钥不能为空");
        mapper.updateCredentialIfVersion(
                checkedId,
                checkedSecret.keyId(),
                checkedSecret.ciphertext(),
                acceptedStep,
                Objects.requireNonNull(expectedVersion, "TOTP 凭据版本不能为空"));
        return Objects.requireNonNull(findByAccountId(checkedId), "替换后的 TOTP 凭据不存在");
    }

    /**
     * 按乐观版本删除凭据。
     *
     * @param accountId 凭据所属账号
     * @param expectedVersion Service 刚读取到的凭据版本
     */
    @AffectedRows(target = "TOTP 凭据", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public void delete(@NotNull Long accountId, @NotNull Version expectedVersion) {
        mapper.deleteByAccountId(
                Objects.requireNonNull(accountId, "Account 标识不能为空"),
                Objects.requireNonNull(expectedVersion, "TOTP 凭据版本不能为空"));
    }

    /**
     * 幂等删除账号保存的 TOTP 凭据。
     *
     * @param accountId 待清理凭据的 Account
     */
    @AffectedRows(target = "删除 TOTP 凭据", whenZero = ZeroRowPolicy.ALLOW)
    public void erase(@NotNull Long accountId) {
        mapper.deleteByAccountId(Objects.requireNonNull(accountId, "Account 标识不能为空"), null);
    }
}
