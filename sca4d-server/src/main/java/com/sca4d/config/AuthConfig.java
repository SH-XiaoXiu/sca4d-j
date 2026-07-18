package com.sca4d.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.AuthenticationUnavailable;
import com.sca4d.common.auth.DefaultPrincipalSelection;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.web.AccountArgumentResolver;
import com.sca4d.common.auth.web.AuthInterceptor;
import com.sca4d.common.auth.web.PrincipalArgumentResolver;
import com.sca4d.common.auth.web.PrincipalInterceptor;
import com.sca4d.common.auth.web.PrincipalSelectionInputResolver;
import com.sca4d.common.auth.web.PrincipalSelectionInvoker;
import com.sca4d.common.auth.web.SubjectArgumentResolver;
import com.sca4d.common.permission.AccessAspect;
import com.sca4d.common.permission.PermissionChecker;
import com.sca4d.common.permission.PermissionCheckerUnavailable;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 认证、Principal、权限与资源访问配置。
 */
@Configuration
@EnableConfigurationProperties(Sca4djAuthenticationProperties.class)
public class AuthConfig {

    /** 启动时拒绝不完整的邮件验证码、TOTP 和 Passkey 配置。 */
    @Bean
    public InitializingBean authenticationPropertiesValidator(
            @NotNull Sca4djAuthenticationProperties properties) {
        return () -> {
            if (properties.getTtl() == null || properties.getTtl().isZero() || properties.getTtl().isNegative()) {
                throw new IllegalArgumentException("认证 TTL 必须大于 0");
            }
            if (properties.getStepUpTtl() == null
                    || properties.getStepUpTtl().isZero()
                    || properties.getStepUpTtl().isNegative()) {
                throw new IllegalArgumentException("STEP_UP TTL 必须大于 0");
            }
            properties.getEmailCode().validate();
            properties.getTotp().validate();
            properties.getPasskey().validate();
        };
    }

    static final List<String> OPEN_API_PUBLIC_PATHS = List.of(
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**");

    /**
     * 注册没有具体认证实现时拒绝所有登录请求的默认实现。
     *
     * @return 始终拒绝创建和解析会话的认证实现
     */
    @Bean
    @ConditionalOnMissingBean(Authentication.class)
    public @NotNull Authentication authentication() {
        return new AuthenticationUnavailable();
    }

    /**
     * 注册未声明选择条件时使用的 DEFAULT Principal 选择器。
     *
     * <p>该 Bean 使用固定名称与 Primary 含义，业务可通过同名 Bean 替换默认行为，同时保留其他类型化选择器。
     *
     * @return 从当前 Session 读取 DEFAULT Principal 的实现
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "defaultPrincipalSelection")
    public @NotNull PrincipalSelection<?> defaultPrincipalSelection() {
        return new DefaultPrincipalSelection();
    }

    /**
     * 注册没有业务权限实现时的失败关闭检查器。
     *
     * @return 对受保护权限声明拒绝访问的检查器
     */
    @Bean
    @ConditionalOnMissingBean(PermissionChecker.class)
    public @NotNull PermissionChecker permissionChecker() {
        return new PermissionCheckerUnavailable();
    }

    /**
     * 注册 Repository 读取当前资源访问范围的入口。
     *
     * @return 从 CurrentRequest 读取业务模块 Criteria 的访问器
     */
    @Bean
    @ConditionalOnMissingBean(ResourceAccess.class)
    public @NotNull ResourceAccess resourceAccess() {
        return new ResourceAccess();
    }

    /**
     * 注册 Controller Subject 参数解析器。
     *
     * @return 支持 Subject 对象和 subjectId 注入的解析器
     */
    @Bean
    public @NotNull SubjectArgumentResolver subjectArgumentResolver() {
        return new SubjectArgumentResolver();
    }

    /**
     * 注册 Controller Account 参数解析器。
     *
     * @return 支持 Account 对象和 accountId 注入的解析器
     */
    @Bean
    public @NotNull AccountArgumentResolver accountArgumentResolver() {
        return new AccountArgumentResolver();
    }

    /**
     * 注册 Controller Principal 参数解析器。
     *
     * @return 注入最终 Principal 选择结果的解析器
     */
    @Bean
    public @NotNull PrincipalArgumentResolver principalArgumentResolver() {
        return new PrincipalArgumentResolver();
    }

    /**
     * 注册 Principal 选择输入提取器。
     *
     * @return 从 path、query、header、body 和方法参数读取选择输入的解析器
     */
    @Bean
    public @NotNull PrincipalSelectionInputResolver principalSelectionInputResolver() {
        return new PrincipalSelectionInputResolver();
    }

    /**
     * 注册类型化 Principal 选择调用器。
     *
     * @param objectMapper 用于把原始选择输入转换为选择器参数的映射器
     * @return 支持泛型选择器参数绑定的调用器
     */
    @Bean
    public @NotNull PrincipalSelectionInvoker principalSelectionInvoker(
            @NotNull ObjectMapper objectMapper) {
        return new PrincipalSelectionInvoker(objectMapper);
    }

    /**
     * 注册要求非匿名接口具有有效 Subject 的认证拦截器。
     *
     * @return 在 HTTP 请求开始时建立认证信息的拦截器
     */
    @Bean
    public @NotNull AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    /**
     * 注册能够提前完成 path、query、header Principal 选择的拦截器。
     *
     * @param principalSelection 默认 Principal 选择器
     * @param inputResolver 选择输入提取器
     * @param invoker 类型化选择器调用器
     * @param beanFactory 用于查找声明指定的选择器 Bean
     * @return Principal 预选择拦截器
     */
    @Bean
    public @NotNull PrincipalInterceptor principalInterceptor(
            @NotNull PrincipalSelection<?> principalSelection,
            @NotNull PrincipalSelectionInputResolver inputResolver,
            @NotNull PrincipalSelectionInvoker invoker,
            @NotNull BeanFactory beanFactory) {
        return new PrincipalInterceptor(
                principalSelection, inputResolver, invoker, beanFactory);
    }

    /**
     * 将认证参数解析器和有序安全拦截器注册到 Spring MVC。
     *
     * @param subjectArgumentResolver Subject 参数解析器
     * @param accountArgumentResolver Account 参数解析器
     * @param principalArgumentResolver Principal 参数解析器
     * @param authInterceptor Subject 认证拦截器
     * @param principalInterceptor Principal 预选择拦截器
     * @return 认证运行时 MVC 扩展
     */
    @Bean
    public @NotNull WebMvcConfigurer authWebMvcConfigurer(
            @NotNull SubjectArgumentResolver subjectArgumentResolver,
            @NotNull AccountArgumentResolver accountArgumentResolver,
            @NotNull PrincipalArgumentResolver principalArgumentResolver,
            @NotNull AuthInterceptor authInterceptor,
            @NotNull PrincipalInterceptor principalInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(
                    @NotNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(subjectArgumentResolver);
                resolvers.add(accountArgumentResolver);
                resolvers.add(principalArgumentResolver);
            }

            @Override
            public void addInterceptors(@NotNull InterceptorRegistry registry) {
                registry.addInterceptor(authInterceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(OPEN_API_PUBLIC_PATHS)
                        .order(0);
                registry.addInterceptor(principalInterceptor)
                        .addPathPatterns("/**")
                        .order(1);
            }
        };
    }

    /**
     * 注册方法级 Principal、Permission 与业务模块资源访问共用执行切面。
     *
     * @param principalSelection 默认 Principal 选择器
     * @param inputResolver 选择输入提取器
     * @param invoker 类型化选择器调用器
     * @param beanFactory 用于解析声明指定的扩展 Bean
     * @param permissionChecker 当前会话权限检查器
     * @return 失败关闭的共用访问控制切面
     */
    @Bean
    public @NotNull AccessAspect accessAspect(
            @NotNull PrincipalSelection<?> principalSelection,
            @NotNull PrincipalSelectionInputResolver inputResolver,
            @NotNull PrincipalSelectionInvoker invoker,
            @NotNull BeanFactory beanFactory,
            @NotNull PermissionChecker permissionChecker) {
        return new AccessAspect(
                principalSelection,
                inputResolver,
                invoker,
                beanFactory,
                permissionChecker);
    }
}
