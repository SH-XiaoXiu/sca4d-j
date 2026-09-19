package com.sca4d.auth.x509;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.persistence.Patch;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** 定义 X.509 模块拥有的 Account 公钥证书 SQL 形状。 */
public interface X509CredentialMapper {

    /** 写入一张 ACTIVE X.509 Credential。 */
    int insert(
            @Param("id") long id,
            @Param("accountId") long accountId,
            @Param("fingerprintSha256") @NotNull String fingerprintSha256,
            @Param("certificateDer") byte @NotNull [] certificateDer,
            @Param("serialNumber") @NotNull String serialNumber,
            @Param("subjectDn") @NotNull String subjectDn,
            @Param("notBefore") @NotNull Instant notBefore,
            @Param("notAfter") @NotNull Instant notAfter);

    /** 按标识、账号、指纹及可选有效时间查询证书元数据；调用方决定读取数量。 */
    @NotNull
    List<X509Credential> select(
            @Param("id") @Nullable Long id,
            @Param("accountId") @Nullable Long accountId,
            @Param("fingerprintSha256") @Nullable String fingerprintSha256,
            @Param("validAt") @Nullable Instant validAt,
            @Param("limit") @Nullable Integer limit);

    /** 对指定账号中符合标识、版本和状态条件的记录执行生命周期字段 Patch。 */
    int updateStatus(
            @Param("accountId") long accountId,
            @Param("id") @Nullable Long id,
            @Param("expectedVersion") @Nullable Version expectedVersion,
            @Param("expectedStatus") @NotNull X509Credential.Status expectedStatus,
            @Param("status") @NotNull Patch<X509Credential.Status> status,
            @Param("revokedAt") @NotNull Patch<Instant> revokedAt);

    /** 幂等删除 Account 的全部 X.509 Credential。 */
    int deleteByAccountId(@Param("accountId") long accountId);
}
