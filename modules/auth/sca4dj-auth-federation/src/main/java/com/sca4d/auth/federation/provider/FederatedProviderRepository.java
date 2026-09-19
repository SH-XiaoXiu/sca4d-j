package com.sca4d.auth.federation.provider;

import com.sca4d.auth.federation.FederatedProvider;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.auth.federation.FederatedUnlinkedIdentityAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 提供联邦 Provider 及其协议配置的稳定持久化语义。 */
public interface FederatedProviderRepository {

    /** @return 所有未删除 Provider */
    @NotNull
    List<FederatedProvider> findAll();

    /** @param code Provider 编码 @return Provider；不存在时为空 */
    @NotNull
    Optional<FederatedProvider> findByCode(@NotNull String code);

    /** @return 登录页可用 Provider */
    @NotNull
    List<FederatedProvider> findLoginProviders();

    /** 新增 Provider。 */
    @NotNull
    FederatedProvider insert(@NotNull FederatedProvider provider);

    /** 按根资源版本条件替换 Provider 的展示和未绑定身份策略。 */
    @NotNull
    FederatedProvider replace(
            @NotNull String code,
            boolean loginPageVisible,
            int displayOrder,
            @NotNull FederatedUnlinkedIdentityAction action,
            boolean autoLinkVerifiedEmail,
            @NotNull Map<String, Map<String, String>> i18n,
            @Nullable String iconReference);

    /** 按根资源版本条件修改状态。 */
    @NotNull
    FederatedProvider changeStatus(
            @NotNull String code,
            @NotNull com.sca4d.auth.federation.FederatedProviderStatus status);

    /** 删除停用且没有身份绑定的 Provider。 */
    void delete(@NotNull String code);

    /** @return 不包含 Secret 的协议配置；未配置时为空 */
    @Nullable
    FederatedProviderConfiguration configuration(@NotNull FederatedProvider provider);

    /** @return 授权交换需要的协议配置；仅允许模块内部短暂读取 Secret */
    @Nullable
    FederatedProviderConfiguration authorizationConfiguration(@NotNull FederatedProvider provider);

    /** 保存协议配置；空 Secret 保留已有 Secret，并返回版本已增加的 Provider。 */
    @NotNull
    FederatedProvider saveConfiguration(
            @NotNull String code,
            @NotNull FederatedProviderConfiguration configuration);
}
