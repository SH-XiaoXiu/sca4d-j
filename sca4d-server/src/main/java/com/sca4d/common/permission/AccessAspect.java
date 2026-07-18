package com.sca4d.common.permission;

import com.sca4d.common.access.AccessCriteria;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.access.ResourceAccessPolicy;
import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalInjection;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.web.PrincipalSelectionInput;
import com.sca4d.common.auth.web.PrincipalSelectionInputResolver;
import com.sca4d.common.auth.web.PrincipalSelectionInvoker;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Controller 与消息处理方法的访问策略切面。
 */
@Aspect
@Order(250)
public class AccessAspect {

    private final PrincipalSelection<?> principalSelection;
    private final PrincipalSelectionInputResolver inputResolver;
    private final PrincipalSelectionInvoker invoker;
    private final BeanFactory beanFactory;
    private final PermissionChecker permissionChecker;

    /**
     * 创建共用访问控制切面。
     *
     * @param principalSelection 默认 Principal 选择器
     * @param inputResolver Principal 选择及表达式输入解析器
     * @param invoker 类型化 Principal 选择调用器
     * @param beanFactory 声明指定的选择器和业务模块资源访问策略来源
     * @param permissionChecker 角色与功能权限检查器
     */
    public AccessAspect(
            @NotNull PrincipalSelection<?> principalSelection,
            @NotNull PrincipalSelectionInputResolver inputResolver,
            @NotNull PrincipalSelectionInvoker invoker,
            @NotNull BeanFactory beanFactory,
            @NotNull PermissionChecker permissionChecker) {
        this.principalSelection = principalSelection;
        this.inputResolver = inputResolver;
        this.invoker = invoker;
        this.beanFactory = beanFactory;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 按 Anonymous、Principal、Permission、Access 顺序执行最终访问策略。
     *
     * @param joinPoint Controller 或 MessageMapping 方法调用
     * @return 业务方法返回值；void 或业务返回空值时允许为空
     * @throws Throwable 访问拒绝异常或业务方法异常
     */
    @Around("within(com.sca4d..*) && ("
            + "@within(org.springframework.web.bind.annotation.RestController)"
            + " || @annotation(org.springframework.web.bind.annotation.ResponseBody)"
            + " || @within(org.springframework.messaging.handler.annotation.MessageMapping)"
            + " || @annotation(org.springframework.messaging.handler.annotation.MessageMapping))")
    public @Nullable Object authorize(@NotNull ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        AccessPolicy policy = AccessPolicy.resolve(method, joinPoint.getTarget().getClass());
        if (policy.isAnonymous()) {
            return joinPoint.proceed();
        }

        CurrentRequest request = CurrentRequestHolder.current();
        if (request.system()) {
            resolveAccess(policy, method, joinPoint.getArgs(), request);
            return joinPoint.proceed(principalArguments(method, joinPoint.getArgs(), request.getPrincipal()));
        }
        request.requireSubject();

        AuthPrincipal principalAnnotation = policy.getPrincipal();
        RequirePermission permission = policy.getPermission();
        boolean principalRequired = (principalAnnotation != null && principalAnnotation.principalRequired())
                || permission != null
                || !policy.getAccess().isEmpty()
                || requiresPrincipalInjection(method);
        if (principalRequired && shouldSelectPrincipal(principalAnnotation, method, request)) {
            PrincipalSelectionInput input = inputResolver.resolve(
                    principalAnnotation, method, joinPoint.getArgs(), request);
            PrincipalType[] types = principalAnnotation == null
                    ? new PrincipalType[0]
                    : principalAnnotation.types();
            Principal principal = invoker.select(
                    selection(principalAnnotation), request, types, input);
            request.setPrincipal(principal);
            // body 或方法参数决定 Principal 时，同步替换权限快照，避免沿用早期选择结果或留下空授权上下文。
            request.setSessionPermission(
                    request.requireSession().requirePermission(principal.requirePrincipalId()));
        }
        if (principalRequired) {
            request.requirePrincipal();
        }

        if (permission != null) {
            for (String permissionCode : permission.permissions()) {
                permissionChecker.requirePermission(request, permissionCode);
            }
            for (String roleCode : permission.roles()) {
                permissionChecker.requireRole(request, roleCode);
            }
        }
        resolveAccess(policy, method, joinPoint.getArgs(), request);
        return joinPoint.proceed(principalArguments(method, joinPoint.getArgs(), request.getPrincipal()));
    }

    /**
     * 判断当前处理方法是否存在要求最终 Principal 的参数注入。
     *
     * <p>参数注入不会改变类或方法的访问声明；它只要求在进入业务方法前得到可用 Principal，以避免把
     * {@code null} 传入声明为非空的业务参数。
     *
     * @param method Controller 或 MessageMapping 方法
     * @return 存在非 nullable 的 {@link AuthPrincipal} 参数时返回 true
     */
    private boolean requiresPrincipalInjection(@NotNull Method method) {
        for (Parameter parameter : method.getParameters()) {
            AuthPrincipal declaration = parameter.getAnnotation(AuthPrincipal.class);
            if (declaration != null && !declaration.nullable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用完成选择后的 Principal 替换处理方法的注入参数。
     *
     * <p>Spring 在 AOP 切面之前已经完成参数解析。body 或 payload 参与选择时，解析器只能暂时返回
     * {@code null}；此处在调用业务方法前一次性写入 Principal、ID 和类型，保证三种注入形式观察到
     * 同一个最终选择结果。
     *
     * @param method 即将执行的处理方法
     * @param arguments Spring 已绑定的原始参数
     * @param principal 当前请求已选择的 Principal；允许为空以支持 nullable 参数
     * @return 可以直接传给 {@link ProceedingJoinPoint#proceed(Object[])} 的参数副本
     */
    private @NotNull Object[] principalArguments(
            @NotNull Method method, @NotNull Object[] arguments, @Nullable Principal principal) {
        Object[] invocationArguments = arguments.clone();
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            AuthPrincipal declaration = parameters[index].getAnnotation(AuthPrincipal.class);
            if (declaration != null) {
                invocationArguments[index] = PrincipalInjection.value(
                        parameters[index].getType(), declaration, principal);
            }
        }
        return invocationArguments;
    }

    /**
     * 判断既有 Principal 是否可用于当前访问声明。
     *
     * <p>依赖 body、payload 或其他方法参数的声明必须在本切面重新选择；只依赖请求数据的声明可复用
     * MVC 拦截器或 STOMP 实现已经建立的 Principal。
     *
     * @param annotation 类或方法上的 Principal 选择声明；缺少声明时使用默认 PrincipalSelection
     * @param method 当前处理方法
     * @param request 当前请求
     * @return 需要执行选择时返回 true
     */
    private boolean shouldSelectPrincipal(
            @Nullable AuthPrincipal annotation,
            @NotNull Method method,
            @NotNull CurrentRequest request) {
        return request.getPrincipal() == null
                || request.getPrincipal().getPrincipalId() == null
                || (annotation != null
                        && !inputResolver.supportsEarlyResolution(annotation, method));
    }

    /**
     * 获取当前访问声明指定的 PrincipalSelection 实现。
     *
     * @param annotation 类或方法上的选择声明；缺少时使用通用默认实现
     * @return 本次请求使用的 PrincipalSelection 实现
     */
    private @NotNull PrincipalSelection<?> selection(
            @Nullable AuthPrincipal annotation) {
        if (annotation == null || annotation.selection() == PrincipalSelection.class) {
            return principalSelection;
        }
        return beanFactory.getBean(annotation.selection());
    }

    /**
     * 解析访问策略声明的全部业务模块资源访问范围。
     *
     * @param policy 当前方法的最终访问策略
     * @param method 当前处理方法
     * @param arguments 已完成绑定的方法参数
     * @param request 当前请求；解析结果会写入其中，供 Repository 获取
     */
    private void resolveAccess(
            @NotNull AccessPolicy policy,
            @NotNull Method method,
            @NotNull Object[] arguments,
            @NotNull CurrentRequest request) {
        for (Access declaration : policy.getAccess()) {
            Object resourceId = null;
            if (!declaration.resourceId().isBlank()) {
                resourceId = inputResolver.evaluate(
                        declaration.resourceId(), method, arguments, request);
                if (resourceId == null || resourceId.toString().isBlank()) {
                    throw new ForbiddenException(CommonErrorCode.ACCESS_RESOURCE_ID_REQUIRED);
                }
            }
            resolveAccess(declaration, resourceId, request);
        }
    }

    /**
     * 读取注解指定的业务资源，完成 Cedar 判断并保存其 Criteria。
     *
     * <p>资源、Policy 与 Criteria 类型由同一个 Bean 声明；重复写入同类型条件时失败关闭。Cedar 返回
     * Deny 时不会生成 Criteria 或进入业务方法。
     *
     * @param declaration 当前资源访问声明
     * @param resourceId 可选资源标识
     * @param request 当前请求
     */
    @SuppressWarnings("unchecked")
    private void resolveAccess(
            @NotNull Access declaration,
            @Nullable Object resourceId,
            @NotNull CurrentRequest request) {
        ResourceAccessPolicy<Object, AccessCriteria> resourcePolicy = (ResourceAccessPolicy<Object, AccessCriteria>) beanFactory.getBean(declaration.policy());
        Object resource = resourcePolicy.resource(request, declaration.action(), resourceId);
        if (!resourcePolicy.policy().allows(request, declaration.action(), resource)) {
            throw new ResourceAccessDeniedException();
        }
        AccessCriteria criteria = resourcePolicy.criteria(request, declaration.action(), resource);
        request.putAccess(resourcePolicy.criteriaType(), criteria);
    }
}
