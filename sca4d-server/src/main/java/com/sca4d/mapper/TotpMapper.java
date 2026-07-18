package com.sca4d.mapper;

import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamTotpEntity;
import com.sca4d.iam.totp.repository.TotpRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** TOTP Credential 公共记录、加密协议子表和原子时间步 Mapper。 */
@Mapper
public interface TotpMapper {

    /** @param credential 已确定 Account、TOTP 类型和初始状态的公共 Credential */
    void insertCredential(@Param("credential") @NotNull IamCredentialEntity credential);

    /** @param totp Credential 主键、加密密钥和激活时已接受时间步 */
    void insertTotp(@Param("totp") @NotNull IamTotpEntity totp);

    /**
     * 读取指定 Account 的 TOTP Credential、加密密钥和认证状态。
     *
     * @param accountId 已由 Authentication 固定的 Account 主键
     * @return 验证需要的当前行；没有 TOTP Credential 时为 {@code null}
     */
    @Nullable
    TotpRow selectTotp(@Param("accountId") @NotNull Long accountId);

    /**
     * 统计 Account 未删除的 ACTIVE TOTP Credential，不读取加密密钥字段。
     *
     * @param excludedCredentialId 不计入结果的 Credential；普通检查为 {@code null}
     * @return 符合条件的 Credential 数量
     */
    int countActiveTotp(
            @Param("accountId") @NotNull Long accountId,
            @Param("excludedCredentialId") @Nullable Long excludedCredentialId);

    /**
     * 只在新时间步严格大于已接受时间步时推进一次。
     *
     * @return Credential 仍可认证且当前时间步首次提交时为 1
     */
    int advanceAcceptedStep(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("acceptedStep") long acceptedStep);

    /**
     * 原子替换加密密钥、激活时间步并推进公共 Credential 版本。
     *
     * @return Credential、TOTP 子表、归属和版本全部命中时为 1
     */
    int replaceTotp(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("secretKeyId") @NotNull String secretKeyId,
            @Param("secretNonce") byte @NotNull [] secretNonce,
            @Param("secretCiphertext") byte @NotNull [] secretCiphertext,
            @Param("acceptedStep") long acceptedStep,
            @Param("version") @NotNull Long version);

    /**
     * 按 TOTP 类型、Account 归属和版本逻辑删除公共 Credential。
     *
     * @return 全部条件命中时为 1；其他 Credential 类型不会更新
     */
    int deleteTotp(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("version") @NotNull Long version);
}
