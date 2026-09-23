package com.sca4d.notification.sms.mapper;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.Version;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 读写 SMS Provider、线路绑定及其版本条件。
 */
public interface SmsProviderMapper {
    /**
     * 按可选标识、启用状态和路由读取未删除 Provider。
     */
    @NotNull
    List<@NotNull SmsProviderState> selectProviders(
            @Param("providerId") @Nullable Long providerId,
            @Param("enabled") @Nullable Boolean enabled,
            @Param("route") @Nullable String route);

    /**
     * @return 支持指定线路并按优先级排序的启用 Provider
     */
    default @NotNull List<@NotNull SmsProviderState> selectEnabledProviders(@NotNull String route) {
        return selectProviders(null, true, route);
    }

    /**
     * 按可选条件读取未删除传输绑定。
     */
    @NotNull
    List<@NotNull SmsBindingState> selectBindings(
            @Param("bindingId") @Nullable Long bindingId,
            @Param("providerId") @Nullable Long providerId,
            @Param("notificationCode") @Nullable TreeCode notificationCode,
            @Param("route") @Nullable String route,
            @Param("languageTag") @Nullable String languageTag,
            @Param("enabled") @Nullable Boolean enabled);

    /**
     * @return 新增默认停用 Provider 的行数
     */
    int insertProvider(
            @Param("providerId") long providerId,
            @Param("code") @NotNull String code,
            @Param("providerType") @NotNull String providerType,
            @Param("credentialRef") @NotNull String credentialRef,
            @Param("priority") int priority);

    /**
     * @return 按预期版本完整替换 Provider 的行数
     */
    int replaceProvider(
            @Param("providerId") long providerId,
            @Param("code") @NotNull String code,
            @Param("providerType") @NotNull String providerType,
            @Param("credentialRef") @NotNull String credentialRef,
            @Param("priority") int priority,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * @return 按预期版本修改 Provider 状态的行数
     */
    int setProviderEnabled(
            @Param("providerId") long providerId,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * @return 按预期版本逻辑删除停用且没有绑定的 Provider 的行数
     */
    int deleteProvider(
            @Param("providerId") long providerId,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * @return 新增默认停用传输绑定的行数
     */
    int insertBinding(
            @Param("bindingId") long bindingId,
            @Param("providerId") long providerId,
            @Param("notificationCode") @NotNull TreeCode notificationCode,
            @Param("route") @NotNull String route,
            @Param("languageTag") @NotNull String languageTag,
            @Param("signName") @NotNull String signName,
            @Param("providerTemplateCode") @NotNull String providerTemplateCode,
            @Param("parameterMappingJson") @NotNull String parameterMappingJson);

    /**
     * @return 按预期版本完整替换停用绑定的行数
     */
    int replaceBinding(
            @Param("bindingId") long bindingId,
            @Param("notificationCode") @NotNull TreeCode notificationCode,
            @Param("route") @NotNull String route,
            @Param("languageTag") @NotNull String languageTag,
            @Param("signName") @NotNull String signName,
            @Param("providerTemplateCode") @NotNull String providerTemplateCode,
            @Param("parameterMappingJson") @NotNull String parameterMappingJson,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * @return 按预期版本修改绑定状态的行数
     */
    int setBindingEnabled(
            @Param("bindingId") long bindingId,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * @return 按预期版本逻辑删除停用绑定的行数
     */
    int deleteBinding(
            @Param("bindingId") long bindingId,
            @Param("expectedVersion") @NotNull Version expectedVersion);
}
