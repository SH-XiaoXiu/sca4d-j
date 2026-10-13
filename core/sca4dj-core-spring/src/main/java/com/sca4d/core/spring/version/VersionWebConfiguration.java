package com.sca4d.core.spring.version;

import com.sca4d.core.api.version.ApplicationVersion;
import com.sca4d.core.api.version.ClientVersion;
import com.sca4d.core.api.version.CompatibilityDecision;
import com.sca4d.core.api.version.CompatibilityResolver;
import com.sca4d.core.api.version.CompatibilityStatus;
import com.sca4d.core.api.version.VersionCompatibilityException;
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

/** 应用显式导入的 MVC 适配；应用提供矩阵并选择默认能力和旧客户端策略。 */
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

    /** @return 使受矩阵保护的 HTTP 方法进入现有 Call 生命周期的绑定 */
    @Bean
    static VersionCallBinding versionCallBinding() {
        return new VersionCallBinding();
    }

    /** @return 业务执行前报告版本拒绝的 Call 参与者 */
    @Bean
    static VersionCallInterceptor versionCallInterceptor() {
        return new VersionCallInterceptor();
    }

    /** @return 可选 Call 功能之间的顺序约束 */
    @Bean
    static com.sca4d.core.api.call.CallLifecycleConfiguration.Customizer versionCallOrder() {
        return VersionCallInterceptor::order;
    }

    /** 注册版本判断，进入 Controller 前完成协议校验。 */
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
                response.setHeader("X-Server-Release-Version", version.releaseVersion());
                response.setHeader("X-Server-Release-Id", version.releaseId());
                response.setHeader("X-Server-Build", version.componentBuildId());
                response.setHeader("X-Server-Api-Contract", Integer.toString(version.apiContractVersion()));
                response.addHeader(
                        "Access-Control-Expose-Headers",
                        "X-Server-Release-Version, X-Server-Release-Id, X-Server-Build, X-Server-Api-Contract, X-Compatibility-Status, X-Api-Contract");
                if (!(handler instanceof HandlerMethod method))
                    return true;
                if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), VersionDiscovery.class)
                        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), VersionDiscovery.class))
                    return true;
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
                ApiCapability api = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ApiCapability.class);
                if (api == null)
                    api = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ApiCapability.class);
                CompatibilityDecision decision = client.unspecified() && allowUnspecified && api == null
                        ? new CompatibilityDecision(CompatibilityStatus.UNKNOWN, null, "unspecified-client")
                        : compatibility.resolve(
                                client,
                                api == null ? defaultCapability : api.value(),
                                api == null || api.contract() == 0 ? null : api.contract());
                if (request.getAttribute(VersionCompatibilityException.class.getName()) != null)
                    decision = new CompatibilityDecision(CompatibilityStatus.BLOCKED, null, "invalid-client");
                request.setAttribute(DECISION, decision);
                response.setHeader("X-Compatibility-Status", decision.status().value());
                response.addHeader("Vary", "X-Client-Name, X-Client-Release-Version, X-Client-Api-Contract");

                if (decision.rule() != null && decision.status() != CompatibilityStatus.BLOCKED)
                    response.setHeader("X-Api-Contract", Integer.toString(decision.contract()));
                return true;
            }
        });
    }

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
