package com.sca4d.auth.sharedsecret;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.persistence.Patch;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 定义 Shared Secret 模块拥有的 Account 凭据 SQL 形状。 */
public interface SharedSecretCredentialMapper {

    /** 写入 Account 首个 Shared Secret 摘要。 */
    int insert(@Param("accountId") long accountId, @Param("secretHash") String secretHash);

    /** 按 Account 读取当前凭据；尚未签发时返回空。 */
    @Nullable
    SharedSecretCredential selectByAccountId(@Param("accountId") long accountId);

    /** 按账号及可选版本条件保存凭据字段 Patch，不按签发、重新启用、撤销拆分 SQL。 */
    int update(
            @Param("accountId") long accountId,
            @Param("expectedVersion") @Nullable Version expectedVersion,
            @Param("expectedStatus") @NotNull SharedSecretCredential.Status expectedStatus,
            @Param("secretHash") @NotNull Patch<String> secretHash,
            @Param("status") @NotNull Patch<SharedSecretCredential.Status> status,
            @Param("revokedAt") @NotNull Patch<Instant> revokedAt);

    /** 幂等删除 Account 的 Shared Secret Credential。 */
    int deleteByAccountId(@Param("accountId") long accountId);
}
