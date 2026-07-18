package com.sca4d.common.auth.web;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.permission.AccessPolicy;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.request.web.HttpRequestFacts;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * MVC 参数绑定前的 Principal 选择拦截器。
 *
 * <p>该阶段只处理 path、query、header 和无额外输入的选择。依赖 body 或方法参数的选择由
 * {@code AccessAspect} 在参数绑定后完成。
 */
public class PrincipalInterceptor implements HandlerInterceptor {

    private final PrincipalSelection<?> principalSelection;
    private final PrincipalSelectionInputResolver inputResolver;
    private final PrincipalSelectionInvoker invoker;
    private final BeanFactory beanFactory;

    public PrincipalInterceptor(
            @NotNull PrincipalSelection<?> principalSelection,
            @NotNull PrincipalSelectionInputResolver inputResolver,
            @NotNull PrincipalSelectionInvoker invoker,
            @NotNull BeanFactory beanFactory) {
        this.principalSelection = principalSelection;
        this.inputResolver = inputResolver;
        this.invoker = invoker;
        this.beanFactory = beanFactory;
    }

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest servletRequest,
            @NotNull HttpServletResponse response,
            @NotNull Object handler) {
        // ASYNC 和 ERROR 都是原始请求完成后的容器续派，不重新执行 Principal 选择。
        if (servletRequest.getDispatcherType() == DispatcherType.ASYNC
                || servletRequest.getDispatcherType() == DispatcherType.ERROR) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        CurrentRequest request = CurrentRequestHolder.current();
        // 所有 HandlerMethod 都先固定请求数据，后续资源访问或延迟 Principal 选择可以使用相同的 SpEL 输入。
        request.setPathVariables(HttpRequestFacts.pathVariables(servletRequest));
        request.setQueryParameters(HttpRequestFacts.queryParameters(servletRequest));
        request.setHeaders(HttpRequestFacts.headers(servletRequest));

        Method method = handlerMethod.getMethod();
        AccessPolicy policy = AccessPolicy.resolve(method, handlerMethod.getBeanType());
        AuthPrincipal annotation = policy.getPrincipal();
        if (policy.isAnonymous()
                || annotation == null
                || !annotation.principalRequired()
                || !inputResolver.supportsEarlyResolution(annotation, method)) {
            return true;
        }
        request.requireSubject();
        Object[] args = new Object[method.getParameterCount()];
        PrincipalSelectionInput input = inputResolver.resolve(annotation, method, args, request);
        Principal principal = invoker.select(
                selection(annotation), request, annotation.types(), input);
        request.setPrincipal(principal);
        // Principal 选定后立即安装同一会话中的权限快照；没有功能权限注解的审计操作也需要完整请求身份。
        request.setSessionPermission(
                request.requireSession().requirePermission(principal.requirePrincipalId()));
        return true;
    }

    /**
     * 获取方法或类型声明指定的 Principal 选择器。
     *
     * @param annotation 需要提前解析的 Principal 声明
     * @return 默认选择器或声明指定的 Spring Bean
     */
    private @NotNull PrincipalSelection<?> selection(@NotNull AuthPrincipal annotation) {
        if (annotation.selection() == PrincipalSelection.class) {
            return principalSelection;
        }
        return beanFactory.getBean(annotation.selection());
    }
}
