package com.sca4d.auth.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.federation.identity.FederatedIdentityRepository;
import com.sca4d.auth.federation.identity.FederatedIdentityService;
import com.sca4d.auth.federation.identity.mapper.FederatedIdentityMapper;
import com.sca4d.auth.federation.provider.DefaultFederatedProviderRepository;
import com.sca4d.auth.federation.provider.FederatedProviderRepository;
import com.sca4d.auth.federation.provider.mapper.ProviderMapper;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.redis.Sca4djRedis;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** 自动装配联邦 Provider 持久化、授权状态和认证方法；不注册应用 HTTP 路由。 */
@AutoConfiguration
@MapperScan(basePackageClasses = {ProviderMapper.class, FederatedIdentityMapper.class})
public class FederatedAutoConfiguration {

    /** 创建 Provider Secret 加密器。 */
    @Bean
    FederatedSecretCryptography federatedSecretCryptography(@NotNull SecretKeyring keyring) {
        return new FederatedSecretCryptography(keyring);
    }

    /** 创建 Provider Repository。 */
    @Bean
    FederatedProviderRepository federatedProviderRepository(
            @NotNull ProviderMapper providerMapper,
            @Sca4djJson @NotNull ObjectMapper mapper,
            @NotNull FederatedSecretCryptography secrets,
            @NotNull VersionConditions versionConditions) {
        return new DefaultFederatedProviderRepository(providerMapper, mapper, secrets, versionConditions);
    }

    /** 创建外部身份绑定 Repository。 */
    @Bean
    FederatedIdentityRepository federatedIdentityRepository(@NotNull FederatedIdentityMapper identityMapper) {
        return new FederatedIdentityRepository(identityMapper);
    }

    /** 创建外部身份绑定服务。 */
    @Bean
    FederatedIdentityService federatedIdentityService(@NotNull FederatedIdentityRepository repository) {
        return new FederatedIdentityService(repository);
    }

    /** 创建 Provider 服务。 */
    @Bean
    FederatedProviderService federatedProviderService(
            @NotNull FederatedProviderRepository repository,
            @NotNull com.sca4d.core.api.i18n.LocaleCatalog locales) {
        return new FederatedProviderService(repository, locales);
    }

    /** 创建 Redis 授权状态存储。 */
    @Bean
    FederatedAuthorizationStore federatedAuthorizationStore(
            @Sca4djRedis @NotNull StringRedisTemplate redis,
            @NotNull RuntimeNamespace namespace) {
        return new RedisFederatedAuthorizationStore(redis, namespace);
    }

    /** 创建外部 HTTP 客户端。 */
    @Bean
    @ConditionalOnMissingBean
    RestClient federatedRestClient() {
        return RestClient.builder().build();
    }

    /** 创建 OIDC/OAuth2 授权服务。 */
    @Bean
    FederatedAuthorizationService federatedAuthorizationService(
            @NotNull FederatedProviderService providers,
            @NotNull FederatedAuthorizationStore store,
            @NotNull ObjectMapper mapper,
            @NotNull RestClient restClient,
            @NotNull Clock clock) {
        return new FederatedAuthorizationService(providers, store, mapper, restClient, clock);
    }

    /** 只有具体应用提供账号解析器后才注册认证方法。 */
    @Bean
    @ConditionalOnBean(FederatedAccountResolver.class)
    FederatedAuthenticationMethod federatedAuthenticationMethod(
            @NotNull FederatedAuthorizationStore store,
            @NotNull FederatedProviderService providers,
            @NotNull FederatedAccountResolver accounts,
            @NotNull FederatedIdentityService identityBindings,
            @NotNull FederatedIdentityRepository identityRepository,
            @NotNull Clock clock) {
        return new FederatedAuthenticationMethod(
                store,
                providers,
                accounts,
                identityBindings,
                identityRepository,
                clock);
    }
}
