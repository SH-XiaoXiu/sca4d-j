package com.sca4d.identity;

import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 管理 Provider 通用作用域、名称、注册策略和启停状态，不读写具体协议字段。 */
public interface IdentityProviderService {

    /** @return 指定 GLOBAL 或 TENANT 作用域中的全部未删除 Provider */
    @NotNull
    List<IdentityProvider> providers(@NotNull IdentityProviderScope scope);

    /** @return 指定作用域中的 Provider @throws com.sca4d.common.exception.BizException 不存在时抛出 */
    @NotNull
    IdentityProvider require(@NotNull IdentityProviderScope scope, @NotNull Long providerId);

    /**
     * 创建默认停用的 Provider；协议配置必须随后写入对应子资源才能启用。
     *
     * @return 新 Provider 主键
     */
    @NotNull
    Long create(
            @NotNull IdentityProviderScope scope,
            @NotNull String code,
            @NotNull String name,
            @NotNull IdentityProviderProtocol protocol,
            @NotNull IdentityProviderRegistrationPolicy registrationPolicy,
            boolean emailAutoLink);

    /** 修改显示名称、JIT 注册策略和可信邮箱自动关联开关。 */
    void update(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull String name,
            @NotNull IdentityProviderRegistrationPolicy registrationPolicy,
            boolean emailAutoLink,
            long version);

    /** 启用前调用对应协议检查；停用后认证入口不再返回该 Provider。 */
    void changeStatus(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull IdentityProviderStatus status,
            long version);

    /** 删除停用且尚未关联 ExternalIdentity 的 Provider。 */
    void delete(@NotNull IdentityProviderScope scope, @NotNull Long providerId, long version);
}
