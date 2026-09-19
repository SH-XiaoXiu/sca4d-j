package com.sca4d.auth.passkey.credential.mapper;

import com.sca4d.auth.passkey.credential.PasskeyCredential;
import com.sca4d.core.api.concurrent.Version;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 定义 Passkey 模块私有账号锚点和 Credential 表的原子 SQL。
 */
public interface PasskeyCredentialMapper {

    /**
     * 在账号尚无 Passkey 锚点时写入随机 user handle。
     */
    int insertAccountIfAbsent(@Param("accountId") Long accountId, @Param("userHandle") byte[] userHandle);

    /**
     * 按账号读取 Passkey 锚点；账号尚未开始 registration 时返回 {@code null}。
     */
    @Nullable
    PasskeyAccountState selectAccount(
            @Param("accountId") @Nullable Long accountId,
            @Param("userHandle") byte @Nullable [] userHandle);

    /**
     * 按乐观版本原子抢占一个未超过配置上限的 Credential 名额。
     */
    int updateAccountVersionIfBelowCredentialLimit(
            @Param("accountId") Long accountId,
            @Param("expectedVersion") long expectedVersion,
            @Param("maximumCredentials") int maximumCredentials);

    /**
     * 返回账号当前有效 Credential 数量。
     */
    int countByAccountId(@Param("accountId") Long accountId);

    /** 按账号或 Credential 标识读取有效凭据，可额外匹配 user handle。 */
    List<PasskeyCredential> selectCredentials(
            @Param("accountId") @Nullable Long accountId,
            @Param("credentialId") byte @Nullable [] credentialId,
            @Param("userHandle") byte @Nullable [] userHandle);

    /**
     * 写入完成注册的 Passkey Credential，并返回数据库标识。
     */
    @Nullable
    Long insertCredentialIfAbsent(
            @Param("accountId") Long accountId,
            @Param("credentialId") byte[] credentialId,
            @Param("publicKeyCose") byte[] publicKeyCose,
            @Param("signatureCount") long signatureCount,
            @Param("transports") Set<String> transports,
            @Param("backupEligible") boolean backupEligible,
            @Param("backedUp") boolean backedUp,
            @Param("name") String name);

    /**
     * 按读到的版本和签名计数器原子保存一次成功 assertion。
     */
    int updateSignatureCountIfVersionAndCountMatch(
            @Param("id") Long id,
            @Param("expectedVersion") Version expectedVersion,
            @Param("storedSignatureCount") long storedSignatureCount,
            @Param("newSignatureCount") long newSignatureCount,
            @Param("backedUp") boolean backedUp,
            @Param("usedAt") Instant usedAt);

    /**
     * 抢占账号锚点版本，并在删除后仍满足最小保留数量时删除 Credential。
     */
    int updateDeletedIfAccountAndCredentialVersionMatch(
            @Param("accountId") Long accountId,
            @Param("id") Long id,
            @Param("expectedCredentialVersion") Version expectedCredentialVersion,
            @Param("expectedAccountVersion") long expectedAccountVersion,
            @Param("minimumRemainingCredentials") int minimumRemainingCredentials);

    /**
     * 幂等删除账号的全部 Credential 和 user handle。
     */
    int deleteByAccountId(@Param("accountId") Long accountId);
}
