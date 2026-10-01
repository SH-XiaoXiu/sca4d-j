package com.sca4d.auth.hmac;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.persistence.Patch;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 定义 MAC 模块拥有的 Account Credential SQL 形状。 */
public interface HmacCredentialMapper {

    /** 写入 Account 首个加密 MAC Key。 */
    int insert(
            @Param("accountId") long accountId,
            @Param("keyId") String keyId,
            @Param("ciphertext") byte[] ciphertext);

    /** 按 Account 读取当前 Credential；尚未签发时返回空。 */
    @Nullable
    HmacCredential selectByAccountId(@Param("accountId") long accountId);

    /** 按账号及可选版本条件执行凭据字段 Patch；状态条件保护并发撤销。 */
    int update(
            @Param("accountId") long accountId,
            @Param("expectedVersion") @Nullable Version expectedVersion,
            @Param("expectedStatus") @NotNull HmacCredential.Status expectedStatus,
            @Param("keyId") @NotNull Patch<String> keyId,
            @Param("ciphertext") @NotNull Patch<byte[]> ciphertext,
            @Param("status") @NotNull Patch<HmacCredential.Status> status,
            @Param("revokedAt") @NotNull Patch<Instant> revokedAt);

    /** 幂等删除 Account 的 MAC Credential。 */
    int deleteByAccountId(@Param("accountId") long accountId);
}
