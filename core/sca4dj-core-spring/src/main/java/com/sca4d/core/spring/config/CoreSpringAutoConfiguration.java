package com.sca4d.core.spring.config;

import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.annotation.Locked;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.annotation.RequirePermissions;
import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallLifecycleConfiguration;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyCallInterceptor;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.LockCallInterceptor;
import com.sca4d.core.api.concurrent.RateLimitCallInterceptor;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.MessageCatalog;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.permission.SessionOperationPermissionPolicy;
import com.sca4d.core.api.region.PhoneNumberCondition;
import com.sca4d.core.api.region.RegionCatalog;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.call.CallPreCommitTransactionInstaller;
import com.sca4d.core.spring.call.CallPreCommitTransactionListener;
import com.sca4d.core.spring.call.SpelCallValueResolver;
import com.sca4d.core.spring.call.SpringCallValueResolverLookup;
import com.sca4d.core.spring.i18n.CatalogMessageSource;
import com.sca4d.core.spring.i18n.ClasspathMessageCatalog;
import com.sca4d.core.spring.i18n.I18nProperties;
import com.sca4d.core.spring.i18n.RequestLocaleResolver;
import com.sca4d.core.spring.id.SnowflakeIdGenerator;
import com.sca4d.core.spring.identity.PrincipalSelectionCallInterceptor;
import com.sca4d.core.spring.identity.PrincipalSelectionInputResolver;
import com.sca4d.core.spring.identity.PrincipalSelectionInterceptor;
import com.sca4d.core.spring.identity.PrincipalSelectionInvoker;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.permission.CurrentResourceScope;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.RedisIdempotencyProperties;
import com.sca4d.core.spring.redis.RedisPipelineProperties;
import com.sca4d.core.spring.validation.PhoneNumberConditionRegistry;
import com.sca4d.core.spring.web.ApiResponseAdvice;
import com.sca4d.core.spring.web.CurrentIdentityArgumentResolver;
import com.sca4d.core.spring.web.GlobalExceptionHandler;
import com.sca4d.core.spring.web.HttpCallFilter;
import com.sca4d.core.spring.web.HttpInputValidationAspect;
import com.sca4d.core.spring.web.IdentityMdcInterceptor;
import com.sca4d.core.spring.web.IfMatchInterceptor;
import com.sca4d.core.spring.web.ListParametersArgumentResolver;
import com.sca4d.core.spring.web.MethodArgumentsIdempotencyKeyResolver;
import com.sca4d.core.spring.web.PageRequestArgumentResolver;
import com.sca4d.core.spring.web.RequestVersionConditionFilter;
import com.sca4d.core.spring.web.RequestVersionConditions;
import com.sca4d.core.spring.web.ResponseETagContractValidator;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.core.spring.web.clientstate.ClientStateFilter;
import com.sca4d.core.spring.web.clientstate.ClientStateResponseAdvice;
import com.sca4d.core.spring.web.view.BaseViewJacksonInstaller;
import jakarta.validation.Validator;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** 注册 Core API 在 Spring MVC 中使用的 Filter、参数解析器、异常处理器和 AOP Bean。 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@EnableAspectJAutoProxy
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@EnableConfigurationProperties({
        CoreSpringProperties.class,
        HttpInputValidationProperties.class,
        IdProperties.class,
        I18nProperties.class,
        RedisIdempotencyProperties.class,
        RedisPipelineProperties.class,
        SqlBatchProperties.class
})
public class CoreSpringAutoConfiguration {

    public CoreSpringAutoConfiguration() {
    }

    /** @return 使用 UTC 时间线的系统时钟 */
    @Bean
    @ConditionalOnMissingBean
    Clock coreClock() {
        return Clock.systemUTC();
    }

    /**
     * @param properties 雪花 ID 节点配置
     * @return 默认雪花 ID 生成器
     */
    @Bean
    @ConditionalOnMissingBean
    IdGenerator idGenerator(IdProperties properties) {
        return new SnowflakeIdGenerator(properties.getNodeId());
    }

    /** @return 使用 libphonenumber 单例元数据的地区目录 */
    @Bean
    @ConditionalOnMissingBean
    RegionCatalog regionCatalog() {
        return RegionCatalog.standard();
    }

    /** @param properties SQL 批次限制 @return 公共 SQL 分批执行器 */
    @Bean
    @ConditionalOnMissingBean
    SqlBatches sqlBatches(SqlBatchProperties properties) {
        return new SqlBatches(properties);
    }

    /** @param properties Redis pipeline 限制 @return 公共 Redis 分批执行器 */
    @Bean
    @ConditionalOnMissingBean
    RedisBatches redisBatches(RedisPipelineProperties properties) {
        return new RedisBatches(properties);
    }

    /** @param conditions 应用显式声明的电话号码条件 Bean @return 已检查重复类型的条件目录 */
    @Bean
    PhoneNumberConditionRegistry phoneNumberConditionRegistry(List<PhoneNumberCondition> conditions) {
        return new PhoneNumberConditionRegistry(conditions);
    }

    /** @param properties I18n 应用配置 @return 已校验的支持和默认 Locale 目录 */
    @Bean
    @ConditionalOnMissingBean
    LocaleCatalog localeCatalog(I18nProperties properties) {
        List<Locale> supported = properties.getSupportedLocales()
                .stream()
                .map(Locale::forLanguageTag)
                .toList();
        return new LocaleCatalog(supported, Locale.forLanguageTag(properties.getDefaultLocale()));
    }

    /** @param resources 类路径资源解析器 @param locales Locale 目录 @return 模块静态消息目录 */
    @Bean
    @ConditionalOnMissingBean
    MessageCatalog messageCatalog(ResourcePatternResolver resources, LocaleCatalog locales) {
        return new ClasspathMessageCatalog(resources, locales);
    }

    /** @param locales Locale 目录 @param messages 静态消息目录 @return 统一文本本地化入口 */
    @Bean
    @ConditionalOnMissingBean
    TextLocalizer textLocalizer(LocaleCatalog locales, MessageCatalog messages) {
        return new TextLocalizer(locales, messages);
    }

    /** @param messages 静态消息目录 @param locales 静态消息支持和默认 Locale @return Spring 和 Bean Validation 使用的消息源 */
    @Bean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
    @ConditionalOnMissingBean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
    MessageSource messageSource(MessageCatalog messages, LocaleCatalog locales) {
        return new CatalogMessageSource(messages, locales);
    }

    /** @param locales Locale 目录 @return 保留动态数据请求语言的 HTTP Locale 解析器 */
    @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    LocaleResolver localeResolver(LocaleCatalog locales) {
        return new RequestLocaleResolver(locales);
    }

    /** @param messageSource 共享静态消息源 @return 使用共享消息目录插值的 Bean Validation 入口 */
    @Bean
    @ConditionalOnClass(name = "org.hibernate.validator.HibernateValidator")
    @ConditionalOnMissingBean(Validator.class)
    LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }

    /**
     * @param validator Bean Validation 入口
     * @param localizer 文本本地化入口
     * @param properties HTTP 输入默认校验配置
     * @return Controller 执行前的 HTTP 输入默认校验切面
     */
    @Bean
    HttpInputValidationAspect httpInputValidationAspect(
            Validator validator,
            TextLocalizer localizer,
            HttpInputValidationProperties properties) {
        return new HttpInputValidationAspect(validator, localizer, properties);
    }

    /** @param clock 调用开始时间使用的时钟 @return 最外层调用过滤器 */
    @Bean
    FilterRegistrationBean<HttpCallFilter> httpCallFilter(Clock clock, CallInvoker invoker) {
        FilterRegistrationBean<HttpCallFilter> registration = new FilterRegistrationBean<>(
                new HttpCallFilter(clock, invoker));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /** @param versionConditions HTTP 请求版本条件入口 @return 请求版本条件清理过滤器 */
    @Bean
    FilterRegistrationBean<RequestVersionConditionFilter> requestVersionConditionFilter(
            RequestVersionConditions versionConditions) {
        FilterRegistrationBean<RequestVersionConditionFilter> registration = new FilterRegistrationBean<>(
                new RequestVersionConditionFilter(versionConditions));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        return registration;
    }

    /** @return 在响应提交前写入 ClientState 的过滤器 */
    @Bean
    FilterRegistrationBean<ClientStateFilter> clientStateFilter() {
        FilterRegistrationBean<ClientStateFilter> registration = new FilterRegistrationBean<>(new ClientStateFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    /** @return ClientState 响应体写出适配 */
    @Bean
    ClientStateResponseAdvice clientStateResponseAdvice() {
        return new ClientStateResponseAdvice();
    }

    /** @return `ApiResponse` 状态码和响应头写出适配 */
    @Bean
    ApiResponseAdvice apiResponseAdvice() {
        return new ApiResponseAdvice();
    }

    /**
     * @param mappings 应用实际注册的 Spring MVC 路由
     * @return 启动期响应 ETag 契约校验器
     */
    @Bean
    @ConditionalOnBean(RequestMappingHandlerMapping.class)
    ResponseETagContractValidator responseETagContractValidator(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        return new ResponseETagContractValidator(mappings);
    }

    /**
     * 创建将业务异常和 Spring MVC 异常写入 HTTP 响应的处理器。
     *
     * @param localizer 统一文本本地化入口
     * @return HTTP 异常处理器
     */
    @Bean
    GlobalExceptionHandler globalExceptionHandler(TextLocalizer localizer) {
        return new GlobalExceptionHandler(localizer);
    }

    /** @param applicationContext Spring 容器 @return 按 Bean 类型查找的值解析器目录 */
    @Bean
    CallValueResolverLookup callValueResolverLookup(ApplicationContext applicationContext) {
        return new SpringCallValueResolverLookup(applicationContext);
    }

    /** @param applicationContext Spring 容器 @return 默认注解表达式解析器 */
    @Bean
    @ConditionalOnMissingBean(SPICallValueResolver.class)
    SPICallValueResolver spiCallValueResolver(ApplicationContext applicationContext) {
        return new SpelCallValueResolver(applicationContext);
    }

    /** @return 当前调用资源范围入口 */
    @Bean
    CurrentResourceScope currentResourceScope() {
        return new CurrentResourceScope();
    }

    /** @return HTTP 请求和内部调用共用的根资源版本条件入口 */
    @Bean
    RequestVersionConditions requestVersionConditions() {
        return new RequestVersionConditions();
    }

    /** @param objectMapper 脚手架内部 JSON 映射器 @return 幂等参数规范化和摘要入口 */
    @Bean
    @ConditionalOnMissingBean
    IdempotencyFingerprints idempotencyFingerprints(@Sca4djJson Sca4djMapper objectMapper) {
        return new IdempotencyFingerprints(objectMapper);
    }

    /** @param fingerprints 幂等参数规范化入口 @return 使用方法参数摘要的幂等键解析器 */
    @Bean
    @ConditionalOnMissingBean
    MethodArgumentsIdempotencyKeyResolver methodArgumentsIdempotencyKeyResolver(
            IdempotencyFingerprints fingerprints) {
        return new MethodArgumentsIdempotencyKeyResolver(fingerprints);
    }

    /** @return 固定操作使用 Session 静态资源判断的内置策略 */
    @Bean
    SessionOperationPermissionPolicy sessionOperationPermissionPolicy() {
        return new SessionOperationPermissionPolicy();
    }

    /** @param policies Spring 容器中的业务权限策略 @return 唯一策略认领的权限评估器 */
    @Bean
    PermissionAssessment permissionAssessment(List<PermissionPolicy> policies) {
        return new PermissionAssessment(policies);
    }

    /**
     * @param modules 各业务模块声明的 Operation 目录
     * @return 完成结构校验和排序的 Operation 目录
     */
    @Bean
    OperationCatalog operationCatalog(@NotNull List<@NotNull OperationModule> modules) {
        return new OperationCatalog(modules);
    }

    /**
     * @param handlerAdapter MVC 实际请求处理适配器
     * @param localizer 统一文本本地化入口
     * @return BaseView Jackson 模块安装器
     */
    @Bean
    @ConditionalOnBean(RequestMappingHandlerAdapter.class)
    BaseViewJacksonInstaller baseViewJacksonInstaller(
            RequestMappingHandlerAdapter handlerAdapter,
            TextLocalizer localizer) {
        return new BaseViewJacksonInstaller(handlerAdapter, localizer);
    }

    /** @param permissionAssessment 权限策略入口 @param resolverLookup 调用值解析器目录 @return 权限调用前评估器 */
    @Bean
    PermissionCallInterceptor permissionCallInterceptor(
            PermissionAssessment permissionAssessment,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver) {
        return new PermissionCallInterceptor(permissionAssessment, resolverLookup, expressionResolver);
    }

    /** @param rateLimiter 共享限流器 @param resolverLookup 调用值解析器目录 @return 限流调用前评估器 */
    @Bean
    RateLimitCallInterceptor rateLimitCallInterceptor(
            RateLimiter rateLimiter,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver) {
        return new RateLimitCallInterceptor(rateLimiter, resolverLookup, expressionResolver);
    }

    /**
     * @param idempotencyStore 幂等状态和结果存储
     * @param resolverLookup 调用值解析器目录
     * @param idempotencyFingerprints 幂等参数摘要入口
     * @param idempotencyProperties 幂等处理时间配置
     * @return 幂等执行控制器
     */
    @Bean
    IdempotencyCallInterceptor idempotencyCallInterceptor(
            IdempotencyStore idempotencyStore,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver,
            IdempotencyFingerprints idempotencyFingerprints,
            RedisIdempotencyProperties idempotencyProperties,
            @Qualifier("callIdempotencyScheduler") ScheduledExecutorService renewalScheduler) {
        return new IdempotencyCallInterceptor(
                idempotencyStore,
                resolverLookup,
                expressionResolver,
                idempotencyFingerprints,
                idempotencyProperties.getProcessingTtl(),
                idempotencyProperties.getRetryAfter(),
                idempotencyProperties.getResultRetention(),
                renewalScheduler);
    }

    /** @return 在异步调用结束前按 token 续期幂等处理权的单线程调度器 */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "callIdempotencyScheduler")
    ScheduledExecutorService callIdempotencyScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "sca4dj-call-idempotency-renewal");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /** @param distributedLock 分布式锁 @param resolverLookup 调用值解析器目录 @return 锁执行控制器 */
    @Bean
    LockCallInterceptor lockCallInterceptor(
            DistributedLock distributedLock,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver) {
        return new LockCallInterceptor(distributedLock, resolverLookup, expressionResolver);
    }

    /** @return 调用认证及主体前提检查 */
    @Bean
    IdentityCallInterceptor identityCallInterceptor() {
        return new IdentityCallInterceptor();
    }

    /** @return 主体选择完成后检查权限和审计声明的业务主体前提 */
    @Bean
    IdentityCallInterceptor.PrincipalRequirement principalRequirement() {
        return new IdentityCallInterceptor.PrincipalRequirement();
    }

    /** @param participants 功能和应用注册的阶段参与者 @param customizers 模块和应用补充的依赖 @return 待校验的生命周期配置 */
    @Bean
    @ConditionalOnMissingBean
    CallLifecycleConfiguration callLifecycleConfiguration(
            List<CallLifecycle.Participant> participants,
            List<CallLifecycleConfiguration.Customizer> customizers) {
        CallLifecycleConfiguration configuration = new CallLifecycleConfiguration(participants);
        customizers.forEach(customizer -> customizer.configure(configuration));
        return configuration;
    }

    /** @param configuration 已登记阶段和依赖的配置 @return 已完成依赖校验的调用执行器 */
    @Bean
    CallInvoker callInvoker(CallLifecycleConfiguration configuration) {
        return new CallInvoker(configuration);
    }

    /** @param invoker 调用执行器 @param declarations 功能所属注解注册 @param clock 入口时钟 @return Spring AOP 桥接 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor callLifecycleAdvisor(
            ObjectProvider<CallInvoker> invoker,
            ObjectProvider<CallLifecycleAdvisor.AnnotationBinding<?>> declarations,
            ObjectProvider<CallLifecycleAdvisor.MethodBinding> methodBindings,
            ObjectProvider<Clock> clock) {
        return new CallLifecycleAdvisor(invoker, declarations, methodBindings, clock);
    }

    /** 注册 Core 自己定义的匿名访问注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<AnonymousAccess> anonymousCallBinding() {
        return () -> AnonymousAccess.class;
    }

    /** 注册 Core 自己定义的权限注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<RequirePermission> permissionCallBinding() {
        return () -> RequirePermission.class;
    }

    /** 注册 repeatable 权限注解的容器。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<RequirePermissions> permissionsCallBinding() {
        return () -> RequirePermissions.class;
    }

    /** 注册 Core 自己定义的限流注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<RateLimited> rateLimitCallBinding() {
        return () -> RateLimited.class;
    }

    /** 注册 Core 自己定义的幂等注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<Idempotent> idempotentCallBinding() {
        return () -> Idempotent.class;
    }

    /** 注册 Core 自己定义的锁注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<Locked> lockedCallBinding() {
        return () -> Locked.class;
    }

    /** 注册无内置注解但显式要求进入调用生命周期的方法。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<CallLifecycle.Intercepted> interceptedCallBinding() {
        return () -> CallLifecycle.Intercepted.class;
    }

    /** 系统注解只负责自主入口身份，不向已有用户调用赋予额外权限。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<SystemExecution> systemCallBinding() {
        return new CallLifecycleAdvisor.AnnotationBinding<>() {
            @Override
            public @NonNull Class<SystemExecution> annotationType() {
                return SystemExecution.class;
            }

            @Override
            public @NonNull Optional<Call> createCall(String callId, Instant startedAt, SystemExecution annotation) {
                return Optional
                        .of(Call.system(callId, startedAt, SystemPrincipals.require(TreeCode.of(annotation.value()))));
            }
        };
    }

    /** @return 事务提交前预提交条件监听器 */
    @Bean
    CallPreCommitTransactionListener callPreCommitTransactionListener() {
        return new CallPreCommitTransactionListener();
    }

    /**
     * @param listener 事务提交前预提交条件监听器
     * @return 把监听器安装到事务管理器的 Bean 后处理器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static CallPreCommitTransactionInstaller callPreCommitTransactionInstaller(
            ObjectProvider<CallPreCommitTransactionListener> listener) {
        return new CallPreCommitTransactionInstaller(listener);
    }

    /** @return HTTP 业务身份选择输入解析器 */
    @Bean
    PrincipalSelectionInputResolver principalSelectionInputResolver() {
        return new PrincipalSelectionInputResolver();
    }

    /** @return 使用 Spring JavaBean 绑定的类型化业务身份选择调用器 */
    @Bean
    PrincipalSelectionInvoker principalSelectionInvoker() {
        return new PrincipalSelectionInvoker();
    }

    /**
     * @param applicationContext Spring 容器
     * @param inputResolver HTTP 选择输入解析器
     * @param invoker 类型化选择器调用器
     * @return Controller 业务身份选择切面
     */
    @Bean
    PrincipalSelectionCallInterceptor principalSelectionCallInterceptor(
            ApplicationContext applicationContext,
            PrincipalSelectionInputResolver inputResolver,
            PrincipalSelectionInvoker invoker) {
        return new PrincipalSelectionCallInterceptor(applicationContext, inputResolver, invoker);
    }

    /** 注册业务身份选择声明；运行时选择由主体选择参与者负责。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<CurrentPrincipal> currentPrincipalCallBinding() {
        return () -> CurrentPrincipal.class;
    }

    /** 注册版本条件声明，附着动作延迟到真正执行方法时获取请求状态。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<IfMatch> ifMatchCallBinding(
            ObjectProvider<RequestVersionConditions> conditions) {
        return new CallLifecycleAdvisor.AnnotationBinding<>() {
            @Override
            public @NotNull Class<IfMatch> annotationType() {
                return IfMatch.class;
            }

            @Override
            public void attach(@NotNull CallExecution execution, @NotNull IfMatch annotation) {
                conditions.getObject().attach(execution);
            }
        };
    }

    /**
     * @param applicationContext Spring 容器
     * @param inputResolver HTTP 选择输入解析器
     * @param invoker 类型化选择器调用器
     * @return MVC 参数绑定前的业务身份选择拦截器
     */
    @Bean
    PrincipalSelectionInterceptor principalSelectionInterceptor(
            ApplicationContext applicationContext,
            PrincipalSelectionInputResolver inputResolver,
            PrincipalSelectionInvoker invoker) {
        return new PrincipalSelectionInterceptor(applicationContext, inputResolver, invoker);
    }

    /** @param properties 分页配置 @return MVC 参数解析器注册 */
    @Bean
    WebMvcConfigurer coreWebMvcConfigurer(
            CoreSpringProperties properties,
            PrincipalSelectionInterceptor principalSelectionInterceptor,
            IfMatchInterceptor ifMatchInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addFormatters(@NotNull FormatterRegistry registry) {
                DateTimeFormatterRegistrar dateTimeFormatterRegistrar = new DateTimeFormatterRegistrar();
                dateTimeFormatterRegistrar.setUseIsoFormat(true);
                dateTimeFormatterRegistrar.registerFormatters(registry);
            }

            @Override
            public void addArgumentResolvers(@NotNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentIdentityArgumentResolver());
                resolvers.add(new ListParametersArgumentResolver(properties));
                resolvers.add(new PageRequestArgumentResolver(properties));
            }

            @Override
            public void addInterceptors(@NotNull InterceptorRegistry registry) {
                registry.addInterceptor(ifMatchInterceptor)
                        .order(Ordered.HIGHEST_PRECEDENCE + 100);
                registry.addInterceptor(principalSelectionInterceptor)
                        .order(Ordered.HIGHEST_PRECEDENCE + 200);
                registry.addInterceptor(new IdentityMdcInterceptor());
            }
        };
    }

    /** @return 方法级 `If-Match` 请求头拦截器 */
    @Bean
    IfMatchInterceptor ifMatchInterceptor(RequestVersionConditions versionConditions) {
        return new IfMatchInterceptor(versionConditions);
    }

}
