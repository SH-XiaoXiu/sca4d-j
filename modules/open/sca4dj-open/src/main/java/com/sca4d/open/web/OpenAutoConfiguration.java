package com.sca4d.open.web;

import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.session.SessionService;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.auth.sharedsecret.SharedSecretAuthenticationMethod;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.open.auth.OpenAuthenticationService;
import com.sca4d.open.client.OpenClientOperationChecker;
import com.sca4d.open.client.OpenClientOperationCheckerImpl;
import com.sca4d.open.client.OpenClientRepository;
import com.sca4d.open.client.OpenClientService;
import com.sca4d.open.client.mapper.OpenClientMapper;
import com.sca4d.open.config.OpenProperties;
import com.sca4d.open.oauth.OpenAuthenticationEndpoint;
import com.sca4d.open.oauth.OpenJwtTokenService;
import com.sca4d.open.oauth.OpenTokenEndpointHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Clock;

/** 自动装配平台开放 Client、Token 和 Operation 授权能力。 */
@AutoConfiguration
@MapperScan(basePackageClasses = OpenClientMapper.class)
@ConditionalOnClass(RequestMappingHandlerMapping.class)
@EnableConfigurationProperties(OpenProperties.class)
public class OpenAutoConfiguration {

    /** 创建开放平台 JWT 服务。 */
    @Bean
    OpenJwtTokenService openJwtTokenService(SessionCodec codec, OpenProperties properties, Clock clock) {
        return new OpenJwtTokenService(codec, properties, clock);
    }

    /** 创建平台开放 Token 恢复服务。 */
    @Bean
    OpenAuthenticationService openAuthenticationService(
            OpenJwtTokenService jwtTokens,
            SessionService sessions) {
        return new OpenAuthenticationService(jwtTokens, sessions);
    }

    /** 创建 Client 持久化边界。 */
    @Bean
    OpenClientRepository openClientRepository(OpenClientMapper mapper, SqlBatches sqlBatches) {
        return new OpenClientRepository(mapper, sqlBatches);
    }

    /** 创建平台开放 Client 应用服务。 */
    @Bean
    OpenClientService openClientService(
            IdentityService identities,
            OperationCatalog operations,
            OpenClientRepository clients,
            SharedSecretAuthenticationMethod sharedSecrets,
            HmacAuthenticationMethod hmac) {
        return new OpenClientService(identities, operations, clients, sharedSecrets, hmac);
    }

    /** 创建默认的 Client Operation 检查器。 */
    @Bean
    @ConditionalOnMissingBean(OpenClientOperationChecker.class)
    OpenClientOperationChecker openClientOperationChecker(OpenClientService clients) {
        return new OpenClientOperationCheckerImpl(clients);
    }

    /** 创建应用选择的通用 Client Credentials Token Handler。 */
    @Bean
    @ConditionalOnBean(OpenAuthenticationEndpoint.class)
    OpenTokenEndpointHandler openTokenEndpointHandler(
            OpenClientService clients,
            IdentityService identities,
            SharedSecretAuthenticationMethod sharedSecrets,
            SessionService sessions,
            OpenJwtTokenService jwtTokens) {
        return new OpenTokenEndpointHandler(clients, identities, sharedSecrets, sessions, jwtTokens);
    }

    /** 注册应用选择的通用认证端点。 */
    @Bean
    @ConditionalOnBean(OpenAuthenticationEndpoint.class)
    OpenAuthenticationEndpointRegistrar openAuthenticationEndpointRegistrar(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
            OpenAuthenticationEndpoint endpoint,
            OpenTokenEndpointHandler handler) {
        return new OpenAuthenticationEndpointRegistrar(mappings, endpoint, handler);
    }
}
