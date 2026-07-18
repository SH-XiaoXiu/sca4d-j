package com.sca4d.identity.oauth2;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.entity.IamOauth2ProviderEntity;
import com.sca4d.identity.IdentityClientStateTargets;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.mapper.Oauth2ProviderMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/** 读写 OAuth2 协议子表，并阻止 Client Secret 密文进入管理端对象。 */
@Repository
@RequiredArgsConstructor
public class OAuth2ProviderRepository {

    private final Oauth2ProviderMapper mapper;

    /** @return OAuth2 配置及父 Provider 版本；尚未配置时返回 null */
    public @Nullable OAuth2ProviderConfiguration findConfiguration(
            @NotNull Long providerId, long version) {
        IamOauth2ProviderEntity entity = mapper.selectOneById(providerId);
        if (entity == null) {
            return null;
        }
        return new OAuth2ProviderConfiguration(
                entity.getProviderId(),
                entity.getAuthorizationUri(),
                entity.getTokenUri(),
                entity.getProfileUri(),
                entity.getAuxiliaryProfileUri(),
                entity.getClientId(),
                secretConfigured(entity),
                List.copyOf(Arrays.asList(entity.getScopes().split(" "))),
                new OAuth2ProfileMapping(
                        entity.getSubjectPath(),
                        entity.getDisplayNamePath(),
                        entity.getEmailPath(),
                        entity.getEmailVerifiedPath(),
                        entity.getAvatarPath()),
                version);
    }

    /** @return OAuth2 认证使用的端点、映射和加密 Client Secret；尚未配置时返回 null */
    public @Nullable OAuth2Client findClient(
            @NotNull Long providerId, @NotNull Long providerVersion) {
        IamOauth2ProviderEntity entity = mapper.selectOneById(providerId);
        if (entity == null || !secretConfigured(entity)) {
            return null;
        }
        return new OAuth2Client(
                entity.getProviderId(),
                entity.getAuthorizationUri(),
                entity.getTokenUri(),
                entity.getProfileUri(),
                entity.getAuxiliaryProfileUri(),
                entity.getClientId(),
                new EncryptedSecret(
                        entity.getClientSecretKeyId(),
                        entity.getClientSecretNonce(),
                        entity.getClientSecretCiphertext()),
                List.copyOf(Arrays.asList(entity.getScopes().split(" "))),
                new OAuth2ProfileMapping(
                        entity.getSubjectPath(),
                        entity.getDisplayNamePath(),
                        entity.getEmailPath(),
                        entity.getEmailVerifiedPath(),
                        entity.getAvatarPath()),
                providerVersion);
    }

    /**
     * 新增或替换 OAuth2 配置；更新时 encrypted 为 null 表示保留原 Client Secret。
     *
     * @param exists 父事务开始时是否已经存在协议记录
     */
    public void saveConfiguration(
            @NotNull OAuth2ProviderConfiguration configuration,
            @Nullable EncryptedSecret encrypted,
            boolean exists) {
        IamOauth2ProviderEntity entity = new IamOauth2ProviderEntity()
                .setProviderId(configuration.getProviderId())
                .setAuthorizationUri(configuration.getAuthorizationUri())
                .setTokenUri(configuration.getTokenUri())
                .setProfileUri(configuration.getProfileUri())
                .setAuxiliaryProfileUri(configuration.getAuxiliaryProfileUri())
                .setClientId(configuration.getClientId())
                .setScopes(String.join(" ", configuration.getScopes()))
                .setSubjectPath(configuration.getProfileMapping().getSubjectPath())
                .setDisplayNamePath(configuration.getProfileMapping().getDisplayNamePath())
                .setEmailPath(configuration.getProfileMapping().getEmailPath())
                .setEmailVerifiedPath(configuration.getProfileMapping().getEmailVerifiedPath())
                .setAvatarPath(configuration.getProfileMapping().getAvatarPath());
        if (encrypted != null) {
            entity.setClientSecretKeyId(encrypted.getKeyId())
                    .setClientSecretNonce(encrypted.getNonce())
                    .setClientSecretCiphertext(encrypted.getCiphertext());
        }
        if (!exists) {
            if (encrypted == null) {
                throw new IllegalArgumentException("首次保存 OAuth2 配置必须提供 Client Secret 密文");
            }
            mapper.insertConfiguration(entity);
            return;
        }
        if (mapper.updateConfiguration(entity, encrypted != null) != 1) {
            throw new ConcurrencyConflictException(
                    IdentityProviderErrorCode.PROVIDER_CHANGED,
                    IdentityClientStateTargets.PROVIDERS);
        }
    }

    /** @return 认证端点、subject JSONPath 和 Client Secret 密文均存在时返回 true */
    public boolean isConfigured(@NotNull Long providerId) {
        IamOauth2ProviderEntity entity = mapper.selectOneById(providerId);
        return entity != null
                && StringUtils.hasText(entity.getAuthorizationUri())
                && StringUtils.hasText(entity.getTokenUri())
                && StringUtils.hasText(entity.getProfileUri())
                && StringUtils.hasText(entity.getClientId())
                && StringUtils.hasText(entity.getScopes())
                && StringUtils.hasText(entity.getSubjectPath())
                && secretConfigured(entity);
    }

    /** @return 三个 AES-GCM 存储列均存在且不为空时返回 true */
    private boolean secretConfigured(@NotNull IamOauth2ProviderEntity entity) {
        return entity.getClientSecretKeyId() != null
                && entity.getClientSecretNonce() != null
                && entity.getClientSecretNonce().length > 0
                && entity.getClientSecretCiphertext() != null
                && entity.getClientSecretCiphertext().length > 0;
    }
}
