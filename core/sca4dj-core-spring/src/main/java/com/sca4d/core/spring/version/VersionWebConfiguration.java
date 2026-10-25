package com.sca4d.core.spring.version;

import com.sca4d.core.api.call.CallLifecycleConfiguration;
import com.sca4d.core.api.version.ApplicationVersion;
import com.sca4d.core.api.version.ClientVersion;
import com.sca4d.core.api.version.CompatibilityDecision;
import com.sca4d.core.api.version.CompatibilityResolver;
import com.sca4d.core.api.version.CompatibilityStatus;
import com.sca4d.core.api.version.exception.VersionCompatibilityException;
import com.sca4d.core.spring.web.HttpConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Objects;

/**
 * 应用显式导入的 MVC 配置类，注册版本响应头、客户端版本解析、版本拦截器和参数解析器。
 * 应用提供 CompatibilityResolver 兼容矩阵，并通过 sca4dj.versioning.default-capability 与 sca4dj.versioning.allow-unspecified 配置未声明接口的默认归属和未指定版本客户端的放行策略。
 */
@Configuration(proxyBeanMethods = false)
public class VersionWebConfiguration implements WebMvcConfigurer {
    static final String CLIENT = ClientVersion.class.getName();

    static final String DECISION = CompatibilityDecision.class.getName();

    private final ApplicationVersion version;

    private final CompatibilityResolver compatibility;

    private final String defaultCapability;

    private final boolean allowUnspecified;

    /** 创建请求适配，业务规则仅来自应用提供的矩阵。 */
    public VersionWebConfiguration(
            ApplicationVersion version,
            CompatibilityResolver compatibility,
            @Value("${sca4dj.versioning.default-capability:application}") String defaultCapability,
            @Value("${sca4dj.versioning.allow-unspecified:false}") boolean allowUnspecified) {
        this.version = version;
        this.compatibility = compatibility;
        this.defaultCapability = defaultCapability;
        this.allowUnspecified = allowUnspecified;
    }

    /** @return 把被兼容矩阵保护的 HTTP 方法绑定到 Call 执行的 VersionCallBinding */
    @Bean
    static VersionCallBinding versionCallBinding() {
        return new VersionCallBinding();
    }

    /** @return 在业务执行前检查版本拒绝结果的 CallLifecycle 参与者 */
    @Bean
    static VersionCallInterceptor versionCallInterceptor() {
        return new VersionCallInterceptor();
    }

    /** @return 调整版本检查与权限、幂等参与者执行顺序的 CallLifecycle 配置器 */
    @Bean
    static CallLifecycleConfiguration.Customizer versionCallOrder() {
        return VersionCallInterceptor::order;
    }

    /**
     * 注册 MVC 拦截器。每个请求都写入服务端版本响应头；处理器带 {@link VersionDiscovery} 时不解析客户端版本。
     * 其余请求解析客户端版本并计算兼容结果，结果写入请求属性，兼容状态通过 COMPATIBILITY_STATUS_HEADER 返回，状态不为 BLOCKED 且规则存在时还通过 API_CONTRACT_HEADER 返回接口契约版本，同时把客户端信息写入 MDC 供日志使用。
     * 拦截器在 afterCompletion 和异步处理开始时清理 MDC。
     */
    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(new AsyncHandlerInterceptor() {
            @Override
            public void afterCompletion(
                    @NotNull HttpServletRequest request,
                    @NotNull HttpServletResponse response,
                    @NotNull Object handler,
                    Exception exception) {
                clearVersionMdc();
            }

            @Override
            public void afterConcurrentHandlingStarted(
                    @NotNull HttpServletRequest request,
                    @NotNull HttpServletResponse response,
                    @NotNull Object handler) {
                clearVersionMdc();
            }

            @Override
            public boolean preHandle(
                    @NotNull HttpServletRequest request,
                    @NotNull HttpServletResponse response,
                    @NotNull Object handler) {
                // 每个经过该拦截器的请求都返回服务端版本，并通过 Access-Control-Expose-Headers 允许浏览器客户端读取这些响应头。
                response.setHeader(HttpConstants.SERVER_RELEASE_VERSION_HEADER, version.releaseVersion());
                response.setHeader(HttpConstants.SERVER_RELEASE_ID_HEADER, version.releaseId());
                response.setHeader(HttpConstants.SERVER_BUILD_HEADER, version.componentBuildId());
                response.setHeader(
                        HttpConstants.SERVER_API_CONTRACT_HEADER,
                        Integer.toString(version.apiContractVersion()));
                response.addHeader(
                        "Access-Control-Expose-Headers",
                        String.join(
                                ", ",
                                HttpConstants.SERVER_RELEASE_VERSION_HEADER,
                                HttpConstants.SERVER_RELEASE_ID_HEADER,
                                HttpConstants.SERVER_BUILD_HEADER,
                                HttpConstants.SERVER_API_CONTRACT_HEADER,
                                HttpConstants.COMPATIBILITY_STATUS_HEADER,
                                HttpConstants.API_CONTRACT_HEADER));
                // 非 Controller 处理器，以及标注 VersionDiscovery 的版本发现接口，不解析客户端版本，也不计算兼容结果。
                if (!(handler instanceof HandlerMethod method))
                    return true;
                if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), VersionDiscovery.class)
                        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), VersionDiscovery.class))
                    return true;
                // 客户端版本头格式非法时不中断请求：异常保存到请求属性中，客户端信息置为空，后续兼容结果固定为 BLOCKED。
                ClientVersion client;
                try {
                    client = new ClientVersionResolver().resolve(request);
                } catch (VersionCompatibilityException exception) {
                    request.setAttribute(VersionCompatibilityException.class.getName(), exception);
                    client = new ClientVersion(null, null, null, null, null);
                }
                request.setAttribute(CLIENT, client);
                MDC.put("serverBuild", version.componentBuildId());
                if (client.name() != null)
                    MDC.put("clientName", client.name());
                if (client.releaseVersion() != null)
                    MDC.put("clientVersion", client.releaseVersion());
                if (client.build() != null)
                    MDC.put("clientBuild", client.build());
                // 接口优先使用方法上的 ApiCapability，其次使用类上的声明，都没有时归入默认归属。
                // 未指定版本的客户端在 allow-unspecified 开启且接口未声明时直接返回 UNKNOWN，不查询兼容矩阵。
                ApiCapability api = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ApiCapability.class);
                if (api == null)
                    api = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ApiCapability.class);
                CompatibilityDecision decision = client.unspecified() && allowUnspecified && api == null
                        ? new CompatibilityDecision(CompatibilityStatus.UNKNOWN, null, "unspecified-client")
                        : compatibility.resolve(
                                client,
                                api == null ? defaultCapability : api.value(),
                                api == null || api.contract() == 0 ? null : api.contract());
                // 版本头非法时覆盖矩阵结果，无论接口归属如何都拒绝请求，并把结果交给 Controller 参数解析器。
                if (request.getAttribute(VersionCompatibilityException.class.getName()) != null)
                    decision = new CompatibilityDecision(CompatibilityStatus.BLOCKED, null, "invalid-client");
                request.setAttribute(DECISION, decision);
                // 兼容状态每次都写入响应头；只有未被拒绝且命中规则时才返回接口契约版本，避免客户端按被拒绝的契约继续调用。
                response.setHeader(HttpConstants.COMPATIBILITY_STATUS_HEADER, decision.status().value());
                response.addHeader(
                        "Vary",
                        String.join(
                                ", ",
                                HttpConstants.CLIENT_NAME_HEADER,
                                HttpConstants.CLIENT_RELEASE_VERSION_HEADER,
                                HttpConstants.CLIENT_API_CONTRACT_HEADER));

                if (decision.rule() != null && decision.status() != CompatibilityStatus.BLOCKED)
                    response.setHeader(HttpConstants.API_CONTRACT_HEADER, Integer.toString(decision.contract()));
                return true;
            }
        });
    }

    /** 删除版本拦截器写入的 MDC 字段，避免同一线程处理后续请求时沿用上一个客户端版本。 */
    private static void clearVersionMdc() {
        MDC.remove("serverBuild");
        MDC.remove("clientName");
        MDC.remove("clientVersion");
        MDC.remove("clientBuild");
    }

    /** Controller 可直接接收类型化客户端版本和兼容结果。 */
    @Override
    public void addArgumentResolvers(@NotNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(@NotNull MethodParameter parameter) {
                return parameter.getParameterType() == ClientVersion.class
                        || parameter.getParameterType() == CompatibilityDecision.class;
            }

            /**
             * 读取拦截器在 preHandle 中写入的客户端版本或兼容结果。
             * 带 {@link VersionDiscovery} 的处理器不会写入这两个属性，声明这些参数时会因属性为空而抛出 NullPointerException。
             */
            @Override
            public Object resolveArgument(
                    @NotNull MethodParameter parameter,
                    ModelAndViewContainer container,
                    @NotNull NativeWebRequest request,
                    WebDataBinderFactory binder) {
                var servlet = Objects.requireNonNull(request.getNativeRequest(HttpServletRequest.class));
                return Objects.requireNonNull(
                        servlet.getAttribute(parameter.getParameterType() == ClientVersion.class ? CLIENT : DECISION));
            }
        });
    }
}
