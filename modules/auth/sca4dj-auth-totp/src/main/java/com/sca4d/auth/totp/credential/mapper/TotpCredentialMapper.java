package com.sca4d.auth.totp.credential.mapper;

import com.sca4d.core.api.concurrent.Version;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.Nullable;

/** 定义 TOTP 模块私有凭据表的原子读写。 */
public interface TotpCredentialMapper {

    /**
     * 查询账号当前 TOTP 凭据。
     *
     * @param accountId 目标账号
     * @return 账号当前 TOTP 凭据；未激活时为 {@code null}
     */
    @Nullable
    TotpCredentialState selectByAccountId(@Param("accountId") Long accountId);

    /**
     * 写入首次激活的 TOTP 凭据。
     *
     * @param accountId 凭据所属账号
     * @param secretKeyId 加密密钥标识
     * @param secretCiphertext TOTP 共享密钥密文
     * @param lastAcceptedStep 激活时接受的验证码时间步
     * @return 新写入行数；账号已有凭据时为 {@code 0}
     */
    int insert(
            @Param("accountId") Long accountId,
            @Param("secretKeyId") String secretKeyId,
            @Param("secretCiphertext") byte[] secretCiphertext,
            @Param("lastAcceptedStep") long lastAcceptedStep);

    /**
     * 只有凭据版本未变化且新时间步不早于游标时更新游标。
     *
     * @param accountId 凭据所属账号
     * @param expectedVersion 认证流程读取到的凭据版本
     * @param acceptedStep 本次命中的验证码时间步
     * @return 当前或更新时间步通过原子检查的行数
     */
    int updateAcceptedStepIfVersion(
            @Param("accountId") Long accountId,
            @Param("expectedVersion") Version expectedVersion,
            @Param("acceptedStep") long acceptedStep);

    /**
     * 按乐观版本替换 TOTP 凭据。
     *
     * @param accountId 凭据所属账号
     * @param secretKeyId 新的加密密钥标识
     * @param secretCiphertext 新的 TOTP 共享密钥密文
     * @param lastAcceptedStep 激活新密钥时接受的验证码时间步
     * @param expectedVersion Service 刚读取到的凭据版本
     * @return 版本匹配并完成替换的行数
     */
    int updateCredentialIfVersion(
            @Param("accountId") Long accountId,
            @Param("secretKeyId") String secretKeyId,
            @Param("secretCiphertext") byte[] secretCiphertext,
            @Param("lastAcceptedStep") long lastAcceptedStep,
            @Param("expectedVersion") Version expectedVersion);

    /**
     * 删除账号保存的 TOTP 凭据和密钥，可选版本条件用于前台修改。
     *
     * @param accountId 凭据所属账号
     * @param expectedVersion 修改所依据的版本；账号生命周期清理不限定版本
     * @return 删除行数；账号没有 TOTP 凭据时为 {@code 0}
     */
    int deleteByAccountId(
            @Param("accountId") Long accountId,
            @Param("expectedVersion") @Nullable Version expectedVersion);
}
