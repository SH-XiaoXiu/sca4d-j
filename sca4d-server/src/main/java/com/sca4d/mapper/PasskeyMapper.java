package com.sca4d.mapper;

import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamPasskeyEntity;
import com.sca4d.entity.IamPasskeyUserHandleEntity;
import com.sca4d.iam.passkey.repository.PasskeyRow;
import com.sca4d.iam.passkey.repository.PasskeySecurityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Passkey 公共 Credential、WebAuthn 子表、user handle 和原子计数更新 Mapper。 */
@Mapper
public interface PasskeyMapper {

    /** @param userHandle Account 主键和随机 WebAuthn user handle */
    int insertUserHandle(@Param("userHandle") @NotNull IamPasskeyUserHandleEntity userHandle);

    /** @return Account 已保存的 user handle 行；不存在时为 {@code null} */
    @Nullable
    IamPasskeyUserHandleEntity selectUserHandle(@Param("accountId") @NotNull Long accountId);

    /** @param credential 已确定 Account、PASSKEY 类型和初始状态的公共 Credential */
    void insertCredential(@Param("credential") @NotNull IamCredentialEntity credential);

    /** @param passkey Credential 主键及 WebAuthn 注册结果 */
    void insertPasskey(@Param("passkey") @NotNull IamPasskeyEntity passkey);

    /** @return 指定 Account 的全部未删除 Passkey，按创建时间和主键排列 */
    @NotNull
    List<PasskeyRow> selectByAccount(@Param("accountId") @NotNull Long accountId);

    /** @return 多个 Account 的 Passkey 公共状态和账号安全展示字段，不包含协议认证材料 */
    @NotNull
    List<PasskeySecurityRow> selectSecurityByAccounts(
            @Param("accountIds") @NotNull List<Long> accountIds);

    /** @return 指定 user handle 的全部未删除 Passkey */
    @NotNull
    List<PasskeyRow> selectByUserHandle(@Param("userHandle") byte @NotNull [] userHandle);

    /** @return 指定 WebAuthn credential ID 的全部匹配行；全局唯一约束正常时最多一行 */
    @NotNull
    List<PasskeyRow> selectByPasskeyCredentialId(
            @Param("passkeyCredentialId") byte @NotNull [] passkeyCredentialId);

    /** @return credential ID 与 user handle 同时匹配的 Passkey；不存在时为 {@code null} */
    @Nullable
    PasskeyRow selectByPasskeyCredentialIdAndUserHandle(
            @Param("passkeyCredentialId") byte @NotNull [] passkeyCredentialId,
            @Param("userHandle") byte @NotNull [] userHandle);

    /**
     * 原子写入最新签名计数和备份状态，并推进公共 Credential 版本。
     *
     * @return Credential、状态、旧计数、备份资格和版本全部命中时为 1
     */
    int advanceAssertion(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("expectedSignatureCount") long expectedSignatureCount,
            @Param("signatureCount") long signatureCount,
            @Param("backupEligible") boolean backupEligible,
            @Param("backupState") boolean backupState,
            @Param("version") @NotNull Long version);

    /** @return 未删除 ACTIVE Passkey 数量，不读取公钥字段 */
    int countActivePasskeys(
            @Param("accountId") @NotNull Long accountId,
            @Param("excludedCredentialId") @Nullable Long excludedCredentialId);

    /** @return PASSKEY 类型、Account 和版本全部命中时为 1 */
    int deletePasskey(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("version") @NotNull Long version);
}
