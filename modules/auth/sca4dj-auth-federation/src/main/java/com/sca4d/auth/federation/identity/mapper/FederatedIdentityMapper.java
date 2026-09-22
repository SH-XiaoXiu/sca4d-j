package com.sca4d.auth.federation.identity.mapper;

import com.sca4d.auth.federation.FederatedIdentity;
import com.sca4d.core.api.persistence.Patch;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读写 Account 与外部身份之间的绑定及其资料快照。 */
public interface FederatedIdentityMapper {

    /** 按外部身份或账号读取完整绑定；必须提供 Provider 或 Account 范围。 */
    @NotNull
    List<FederatedIdentity> select(
            @Param("providerId") @Nullable Long providerId,
            @Param("issuer") @Nullable String issuer,
            @Param("subject") @Nullable String subject,
            @Param("accountId") @Nullable Long accountId);

    /** 新增外部身份绑定。 */
    int insert(
            @Param("providerId") long providerId,
            @Param("accountId") long accountId,
            @Param("issuer") @Nullable String issuer,
            @Param("subject") @NotNull String subject,
            @Param("displayName") @Nullable String displayName,
            @Param("email") @Nullable String email,
            @Param("emailVerified") boolean emailVerified,
            @Param("avatarUrl") @Nullable String avatarUrl);

    /** 按绑定版本刷新资料快照，不修改绑定版本。 */
    int updateProfile(
            @Param("id") long id,
            @Param("accountId") long accountId,
            @Param("version") long version,
            @Param("displayName") @NotNull Patch<String> displayName,
            @Param("email") @NotNull Patch<String> email,
            @Param("emailVerified") @NotNull Patch<Boolean> emailVerified,
            @Param("avatarUrl") @NotNull Patch<String> avatarUrl);

    /** 原子删除当前账号的指定绑定。 */
    int delete(@Param("id") long id, @Param("accountId") long accountId, @Param("version") long version);

    /** 检查绑定仍属于账号且版本未改变。 */
    boolean exists(@Param("id") long id, @Param("accountId") long accountId, @Param("version") long version);

    /** 幂等删除 Account 的全部外部身份绑定。 */
    int deleteByAccountId(@Param("accountId") long accountId);

}
