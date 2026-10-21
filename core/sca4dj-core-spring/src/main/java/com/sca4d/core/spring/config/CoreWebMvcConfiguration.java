package com.sca4d.core.spring.config;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.identity.PrincipalSelectionInterceptor;
import com.sca4d.core.spring.web.ApiResponseAdvice;
import com.sca4d.core.spring.web.CurrentIdentityArgumentResolver;
import com.sca4d.core.spring.web.GlobalExceptionHandler;
import com.sca4d.core.spring.web.HttpCallFilter;
import com.sca4d.core.spring.web.HttpInputValidationAspect;
import com.sca4d.core.spring.web.IdentityMdcInterceptor;
import com.sca4d.core.spring.web.IfMatchInterceptor;
import com.sca4d.core.spring.web.ListParametersArgumentResolver;
import com.sca4d.core.spring.web.PageRequestArgumentResolver;
import com.sca4d.core.spring.web.RequestVersionConditionFilter;
import com.sca4d.core.spring.web.RequestVersionConditions;
import com.sca4d.core.spring.web.ResponseETagContractValidator;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.core.spring.web.clientstate.ClientStateFilter;
import com.sca4d.core.spring.web.clientstate.ClientStateResponseAdvice;
import com.sca4d.core.spring.web.view.BaseViewJacksonInstaller;
import jakarta.validation.Validator;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Clock;
import java.util.List;

/** 注册请求过滤器、响应处理和 Spring MVC 参数与拦截器适配。 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CoreWebMvcConfiguration {

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

    /**
     * @param clock 调用开始时间使用的时钟
     * @param invoker 调用生命周期执行器
     * @param properties HTTP 请求配置
     * @return 最外层调用过滤器
     */
    @Bean
    FilterRegistrationBean<HttpCallFilter> httpCallFilter(
            Clock clock,
            CallInvoker invoker,
            WebRequestProperties properties) {
        FilterRegistrationBean<HttpCallFilter> registration = new FilterRegistrationBean<>(
                new HttpCallFilter(clock, invoker, properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setAsyncSupported(true);
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

    /** @return HTTP 请求和内部调用共用的根资源版本条件入口 */
    @Bean
    RequestVersionConditions requestVersionConditions() {
        return new RequestVersionConditions();
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
