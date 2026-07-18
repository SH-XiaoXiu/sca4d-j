package com.sca4d.identity.oidc;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.entity.IamOidcProviderEntity;
import com.sca4d.identity.IdentityClientStateTargets;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.mapper.OidcProviderMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

/** 读写 OIDC 协议子表，并阻止秘密密文进入管理端对象。 */
@Repository
@RequiredArgsConstructor
public class OidcProviderRepository {

    private final OidcProviderMapper mapper;

    /** @return OIDC 配置及父 Provider 版本；尚未配置时返回 null */
    public @Nullable OidcProviderConfiguration findConfiguration(
            @NotNull Long providerId, long version) {
        IamOidcProviderEntity entity = mapper.selectOneById(providerId);
        if (entity == null) {
            return null;
        }
        return new OidcProviderConfiguration(
                entity.getProviderId(),
                entity.getIssuer(),
                entity.getClientId(),
                secretConfigured(entity),
                List.copyOf(Arrays.asList(entity.getScopes().split(" "))),
                version);
    }

    /** @return OIDC 认证请求使用的协议配置和加密 Client Secret；尚未配置时返回 null */
    public @Nullable OidcClient findClient(@NotNull Long providerId, @NotNull Long providerVersion) {
        IamOidcProviderEntity entity = mapper.selectOneById(providerId);
        if (entity == null || !secretConfigured(entity)) {
            return null;
        }
        return new OidcClient(
                entity.getProviderId(),
                entity.getIssuer(),
                entity.getClientId(),
                new EncryptedSecret(
                        entity.getClientSecretKeyId(),
                        entity.getClientSecretNonce(),
                        entity.getClientSecretCiphertext()),
                List.copyOf(Arrays.asList(entity.getScopes().split(" "))),
                providerVersion);
    }

    /**
     * 新增或替换 OIDC 配置；更新时 encrypted 为 null 表示保留原 Client Secret。
     *
     * @param exists 父事务开始时是否已经存在协议记录
     */
    public void saveConfiguration(
            @NotNull Long providerId,
            @NotNull String issuer,
            @NotNull String clientId,
            @Nullable EncryptedSecret encrypted,
            @NotNull String scopes,
            boolean exists) {
        if (!exists) {
            if (encrypted == null) {
                throw new IllegalArgumentException("首次保存 OIDC 配置必须提供 Client Secret 密文");
            }
            mapper.insertConfiguration(new IamOidcProviderEntity()
                    .setProviderId(providerId)
                    .setIssuer(issuer)
                    .setClientId(clientId)
                    .setClientSecretKeyId(encrypted.getKeyId())
                    .setClientSecretNonce(encrypted.getNonce())
                    .setClientSecretCiphertext(encrypted.getCiphertext())
                    .setScopes(scopes));
            return;
        }
        if (mapper.updateConfiguration(
                providerId,
                issuer,
                clientId,
                encrypted == null ? null : encrypted.getKeyId(),
                encrypted == null ? null : encrypted.getNonce(),
                encrypted == null ? null : encrypted.getCiphertext(),
                encrypted != null,
                scopes) != 1) {
            throw new ConcurrencyConflictException(
                    IdentityProviderErrorCode.PROVIDER_CHANGED,
                    IdentityClientStateTargets.PROVIDERS);
        }
    }

    /** @return 协议记录、openid scope 和三个 Client Secret 密文列均存在时返回 true */
    public boolean isConfigured(@NotNull Long providerId) {
        IamOidcProviderEntity entity = mapper.selectOneById(providerId);
        return entity != null
                && entity.getIssuer() != null
                && !entity.getIssuer().isBlank()
                && entity.getClientId() != null
                && !entity.getClientId().isBlank()
                && entity.getScopes() != null
                && Arrays.asList(entity.getScopes().split(" ")).contains("openid")
                && secretConfigured(entity);
    }

    /** @return 三个 AES-GCM 存储列均存在且不为空时返回 true */
    private boolean secretConfigured(@NotNull IamOidcProviderEntity entity) {
        return entity.getClientSecretKeyId() != null
                && entity.getClientSecretNonce() != null
                && entity.getClientSecretNonce().length > 0
                && entity.getClientSecretCiphertext() != null
                && entity.getClientSecretCiphertext().length > 0;
    }
}
