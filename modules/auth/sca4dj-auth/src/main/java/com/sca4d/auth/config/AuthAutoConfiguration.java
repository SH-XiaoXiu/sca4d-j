package com.sca4d.auth.config;

import static com.sca4d.core.api.name.NameFormat.COLON;

import cn.dev33.satoken.stp.StpLogic;
import com.sca4d.auth.engine.AuthenticationMethods;
import com.sca4d.auth.engine.RedisAuthenticationAttempts;
import com.sca4d.auth.engine.attempt.RedisAuthenticationAttemptStore;
import com.sca4d.auth.jwt.JwtSessionProjection;
import com.sca4d.auth.session.RedisSessionGeneration;
import com.sca4d.auth.session.RedisSessionTokenIndex;
import com.sca4d.auth.session.SaTokenRedisSerialization;
import com.sca4d.auth.session.SaTokenSessionControl;
import com.sca4d.auth.session.SaTokenSessionManager;
import com.sca4d.auth.session.SessionIdentityLoadService;
import com.sca4d.auth.session.SessionRefreshService;
import com.sca4d.auth.session.SessionService;
import com.sca4d.auth.session.codec.OperationResourceCodec;
import com.sca4d.auth.session.codec.PrincipalIdLoaders;
import com.sca4d.auth.session.codec.PrincipalLoaders;
import com.sca4d.auth.session.codec.ResourceIdLoaders;
import com.sca4d.auth.session.codec.ResourceLoaders;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.auth.web.AuthRequestInterceptor;
import com.sca4d.core.api.authentication.AuthenticationAttempts;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.authentication.SessionIdentityLoader;
import com.sca4d.core.api.authentication.SessionInvalidationListener;
import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdLoader;
import com.sca4d.core.api.authentication.principalsession.PrincipalLoader;
import com.sca4d.core.api.authentication.principalsession.ResourceCodec;
import com.sca4d.core.api.authentication.principalsession.ResourceIdLoader;
import com.sca4d.core.api.authentication.principalsession.ResourceLoader;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.definition.SystemSetting;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.redis.Sca4djRedis;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 自动装配动作认证引擎、Sa-Token Session 和请求认证拦截器。 */
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    /** 创建本应用上下文的模块设置，来源由 Spring 显式注入。 */
    @Bean
    AuthSettings authSettings(SettingReader<String> reader, SettingWriter<String> writer) {
        return new AuthSettings(reader, writer);
    }

    /** 只在官方 Sa-Token Redis DAO 初始化完成后安装内部资源 Key 的序列化器。 */
    @Bean
    static SaTokenRedisSerialization saTokenRedisSerialization() {
        return new SaTokenRedisSerialization();
    }

    /** @return Session 有效期设置 */
    @Bean
    SystemSetting<Duration> authSessionTtlSetting(AuthSettings settings) {
        return settings.SESSION_TTL;
    }

    /** @return 认证尝试有效期设置 */
    @Bean
    SystemSetting<Duration> authenticationAttemptTtlSetting(AuthSettings settings) {
        return settings.ATTEMPT_TTL;
    }

    /** @return JWT access 有效期设置 */
    @Bean
    SystemSetting<Duration> jwtAccessTtlSetting(AuthSettings settings) {
        return settings.JWT_ACCESS_TTL;
    }

    @Bean
    SessionCodec sessionCodec(
            @Sca4djJson Sca4djMapper objectMapper,
            List<PrincipalCodec<?>> principalCodecs,
            List<ResourceCodec<?>> resourceCodecs) {
        return new SessionCodec(objectMapper, principalCodecs, resourceCodecs);
    }

    @Bean
    OperationResourceCodec operationSessionCodec(@Sca4djJson Sca4djMapper objectMapper) {
        return new OperationResourceCodec(objectMapper);
    }

    @Bean
    RedisSessionGeneration redisSessionGeneration(
            @Sca4djRedis RedisTemplate<Key, String> redis,
            ObjectProvider<SessionInvalidationListener> listeners) {
        return new RedisSessionGeneration(
                redis,
                () -> listeners.orderedStream().toList());
    }

    /**
     * 创建 SessionId 到不透明 Token 的索引。
     *
     * @param redis Redis 字符串操作
     * @param resourceNames 当前 App 隔离名称下的资源名称
     * @return SessionId 到不透明 Token 的索引
     */
    @Bean
    RedisSessionTokenIndex redisSessionTokenIndex(
            @Sca4djRedis RedisTemplate<Key, String> redis) {
        return new RedisSessionTokenIndex(redis);
    }

    /**
     * @return 保留 HTTP Token 名称并按应用隔离 Redis 数据的 Sa-Token 逻辑
     */
    @Bean
    StpLogic sca4djStpLogic() {
        return new StpLogic(Key.of(TreeCode.ofSegment("AUTH")).format(COLON));
    }

    /**
     * @param generations Account Session 失效代次存储
     * @param stpLogic Sa-Token 登录状态操作
     * @return 不装载身份或 Resource 的 Account Session 控制器
     */
    @Bean
    SaTokenSessionControl saTokenSessionControl(
            RedisSessionGeneration generations,
            StpLogic stpLogic,
            RedisSessionTokenIndex tokenIndex) {
        return new SaTokenSessionControl(generations, stpLogic, tokenIndex);
    }

    @Bean
    SessionIdentityLoadService sessionIdentityLoadService(List<SessionIdentityLoader> loaders) {
        return new SessionIdentityLoadService(loaders);
    }

    /**
     * @param loaders 应用安装的 Session Principal Loader
     * @return 选择唯一 Loader 并校验 Principal 结果的服务
     */
    @Bean
    PrincipalLoaders principalSessionPrincipalLoadService(
            List<PrincipalLoader> loaders) {
        return new PrincipalLoaders(loaders);
    }

    /**
     * @param loaders 应用安装的紧凑 Principal 标识 Loader
     * @return 校验每种 Principal 类型只有一个标识来源的服务
     */
    @Bean
    PrincipalIdLoaders principalSessionPrincipalIdLoadService(
            List<PrincipalIdLoader<?>> loaders) {
        return new PrincipalIdLoaders(loaders);
    }

    @Bean(destroyMethod = "close", defaultCandidate = false)
    ExecutorService sessionResourceExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ResourceLoaders sessionResourceLoadService(
            List<ResourceLoader> loaders,
            @Qualifier("sessionResourceExecutor") ExecutorService executor) {
        return new ResourceLoaders(loaders, executor);
    }

    /**
     * @param loaders 应用安装的数字 Resource 标识 Loader
     * @param executor Session Resource 并发装载执行器
     * @return 合并完整 Principal 数字 Resource 标识的服务
     */
    @Bean
    ResourceIdLoaders sessionResourceIdLoadService(
            List<ResourceIdLoader> loaders,
            @Qualifier("sessionResourceExecutor") ExecutorService executor) {
        return new ResourceIdLoaders(loaders, executor);
    }

    @Bean
    SessionRefreshService sessionRefreshService(
            SessionIdentityLoadService identities,
            PrincipalLoaders principals,
            PrincipalIdLoaders principalIds,
            ResourceLoaders resources,
            ResourceIdLoaders resourceIds) {
        return new SessionRefreshService(identities, principals, principalIds, resources, resourceIds);
    }

    @Bean
    SaTokenSessionManager saTokenSessionManager(
            SessionCodec codec,
            SessionRefreshService refresh,
            RedisSessionGeneration generations,
            OperationCatalog operations,
            RedisSessionTokenIndex tokenIndex,
            StpLogic stpLogic,
            Clock clock) {
        return new SaTokenSessionManager(
                codec,
                refresh,
                generations,
                operations,
                tokenIndex,
                stpLogic,
                clock);
    }

    /** @return 使用独立 HS256 密钥签发和验证短效 Session JWT 的服务 */
    @Bean
    JwtSessionProjection jwtSessionProjection(
            SessionCodec codec,
            AuthProperties properties,
            AuthSettings settings,
            Clock clock) {
        return new JwtSessionProjection(codec, properties, settings, clock);
    }

    @Bean
    AuthenticationMethods authenticationMethods(List<AuthenticationMethod<?>> methods) {
        return new AuthenticationMethods(methods);
    }

    @Bean
    RedisAuthenticationAttemptStore redisAuthenticationAttemptStore(
            @Sca4djRedis RedisTemplate<Key, String> redis,
            @Sca4djJson Sca4djMapper objectMapper,
            Clock clock,
            DistributedLock distributedLock) {
        return new RedisAuthenticationAttemptStore(redis, objectMapper, clock, distributedLock);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthenticationAttempts authenticationAttempts(
            AuthenticationMethods methods,
            RedisAuthenticationAttemptStore attempts,
            AuthenticationPolicy policy,
            AuthSettings settings,
            Clock clock) {
        return new RedisAuthenticationAttempts(
                methods,
                attempts,
                policy,
                settings,
                clock);
    }

    @Bean
    SessionService principalSessionService(
            SessionIdentityLoadService identities,
            PrincipalLoaders principals,
            PrincipalIdLoaders principalIds,
            ResourceLoaders resources,
            ResourceIdLoaders resourceIds,
            SaTokenSessionManager sessions,
            JwtSessionProjection tokenProjection,
            AuthSettings settings,
            Clock clock) {
        return new SessionService(
                identities,
                principals,
                principalIds,
                resources,
                resourceIds,
                sessions,
                tokenProjection,
                settings,
                clock);
    }

    /** @return 在 Controller 执行前按方法注解选择 Token 类型并排除应用指定路径的 MVC 配置 */
    @Bean
    WebMvcConfigurer authWebMvcConfigurer(
            SessionService sessions,
            AuthProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NotNull InterceptorRegistry registry) {
                registry.addInterceptor(new AuthRequestInterceptor(sessions))
                        .excludePathPatterns(properties.getExcludedPaths())
                        .order(Ordered.HIGHEST_PRECEDENCE + 100);
            }
        };
    }

}
