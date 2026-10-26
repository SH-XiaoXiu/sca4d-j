package com.sca4d.auth.federation.provider.mapper;

import com.sca4d.auth.federation.FederatedClientAuthentication;
import com.sca4d.auth.federation.FederatedProviderProtocol;
import com.sca4d.auth.federation.FederatedProviderStatus;
import com.sca4d.auth.federation.FederatedUnlinkedIdentityAction;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.persistence.Patch;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读写联邦 Provider、协议配置和版本条件。 */
public interface ProviderMapper {

    /** 按可选编码、状态和展示标志读取未删除 Provider，按展示顺序排序。 */
    @NotNull
    List<@NotNull ProviderState> select(
            @Param("code") @Nullable String code,
            @Param("status") @Nullable FederatedProviderStatus status,
            @Param("loginPageVisible") @Nullable Boolean loginPageVisible);

    /** @param providerId Provider 标识 @return 最新配置；未配置时为空 */
    @Nullable
    ProviderSettingsState selectSettings(@Param("providerId") long providerId);

    /** @return 新增行数 */
    int insert(
            @Param("code") @NotNull String code,
            @Param("protocol") @NotNull FederatedProviderProtocol protocol,
            @Param("status") @NotNull FederatedProviderStatus status,
            @Param("loginPageVisible") boolean loginPageVisible,
            @Param("displayOrder") int displayOrder,
            @Param("unlinkedIdentityAction") @NotNull FederatedUnlinkedIdentityAction unlinkedIdentityAction,
            @Param("autoLinkVerifiedEmail") boolean autoLinkVerifiedEmail,
            @Param("i18n") @NotNull String i18n,
            @Param("iconReference") @Nullable String iconReference);

    /** @return 修改行数；Provider 不存在或版本不匹配时为零 */
    @CheckReturnValue
    int update(
            @Param("code") @NotNull String code,
            @Param("loginPageVisible") @NotNull Patch<Boolean> loginPageVisible,
            @Param("displayOrder") @NotNull Patch<Integer> displayOrder,
            @Param("unlinkedIdentityAction") @NotNull Patch<FederatedUnlinkedIdentityAction> unlinkedIdentityAction,
            @Param("autoLinkVerifiedEmail") @NotNull Patch<Boolean> autoLinkVerifiedEmail,
            @Param("i18n") @NotNull Patch<String> i18n,
            @Param("iconReference") @NotNull Patch<String> iconReference,
            @Param("status") @NotNull Patch<FederatedProviderStatus> status,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /** @return 删除行数；状态、身份绑定或版本不符合条件时为零 */
    @CheckReturnValue
    int delete(
            @Param("code") @NotNull String code,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /** 标记当前配置删除。 */
    int deleteSettings(@Param("providerId") long providerId);

    /** 写入新的协议配置。 */
    int insertSettings(
            @Param("providerId") long providerId,
            @Param("issuer") @Nullable String issuer,
            @Param("clientId") @Nullable String clientId,
            @Param("secretKeyId") @Nullable String secretKeyId,
            byte @Nullable [] secretCiphertext,
            @Param("authorizationEndpoint") @Nullable String authorizationEndpoint,
            @Param("tokenEndpoint") @Nullable String tokenEndpoint,
            @Param("userInfoEndpoint") @Nullable String userInfoEndpoint,
            @Param("jwksEndpoint") @Nullable String jwksEndpoint,
            @Param("clientAuthentication") @NotNull FederatedClientAuthentication clientAuthentication,
            @Param("scopes") @NotNull String scopes,
            @Param("profileMapping") @NotNull String profileMapping,
            @Param("authorizationParameters") @NotNull String authorizationParameters);

    /** @return 修改行数；按期望版本原子递增 Provider 版本，版本不匹配或 Provider 已删除时为零 */
    int updateConfigurationVersion(
            @Param("providerId") long providerId,
            @Param("expectedVersion") @NotNull Version expectedVersion);
}
