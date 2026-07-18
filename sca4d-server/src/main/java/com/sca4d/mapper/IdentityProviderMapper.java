package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.entity.IamIdentityProviderEntity;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

/** IdentityProvider 通用字段写入、乐观更新、逻辑删除和关联计数 Mapper。 */
@Mapper
public interface IdentityProviderMapper extends BaseMapper<IamIdentityProviderEntity> {

    /** @return 实际写入行数，只能为 1 */
    int insertProvider(@Param("provider") @NotNull IamIdentityProviderEntity provider);

    /** @return 主键、作用域和版本同时匹配时更新 1 行，否则为 0 */
    int updateProvider(
            @Param("providerId") @NotNull Long providerId,
            @Param("scopeType") @NotNull AccountScopeType scopeType,
            @Param("scopeId") Long scopeId,
            @Param("name") @NotNull String name,
            @Param("registrationPolicy") @NotNull IdentityProviderRegistrationPolicy registrationPolicy,
            @Param("emailAutoLink") boolean emailAutoLink,
            @Param("version") long version);

    /** @return 当前状态、作用域和版本同时匹配时更新 1 行，否则为 0 */
    int updateStatus(
            @Param("providerId") @NotNull Long providerId,
            @Param("scopeType") @NotNull AccountScopeType scopeType,
            @Param("scopeId") Long scopeId,
            @Param("expectedStatus") @NotNull IdentityProviderStatus expectedStatus,
            @Param("nextStatus") @NotNull IdentityProviderStatus nextStatus,
            @Param("version") long version);

    /** @return 停用状态、协议、作用域和版本同时匹配时推进版本 1 行，否则为 0 */
    int updateConfigurationVersion(
            @Param("providerId") @NotNull Long providerId,
            @Param("scopeType") @NotNull AccountScopeType scopeType,
            @Param("scopeId") Long scopeId,
            @Param("protocol") @NotNull IdentityProviderProtocol protocol,
            @Param("version") long version);

    /** @return Provider 未启用、没有 ExternalIdentity 且版本匹配时逻辑删除 1 行，否则为 0 */
    int markDeleted(
            @Param("providerId") @NotNull Long providerId,
            @Param("scopeType") @NotNull AccountScopeType scopeType,
            @Param("scopeId") Long scopeId,
            @Param("version") long version);

    /** @return 当前 Provider 仍关联的 ExternalIdentity 数量 */
    int countExternalIdentities(@Param("providerId") @NotNull Long providerId);
}
