package com.sca4d.common.permission;

import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Controller 与消息处理方法的最终访问策略。
 *
 * <p>方法级声明只覆盖同类型类级声明；未在方法声明的类别继续继承类级策略。
 */
public class AccessPolicy {

    private final boolean anonymous;
    private final AuthPrincipal principal;
    private final RequirePermission permission;
    private final List<Access> access;

    /**
     * 固化已经完成冲突校验和类/方法覆盖计算的最终访问策略。
     *
     * @param anonymous 最终是否允许匿名执行
     * @param principal 最终 Principal 选择声明
     * @param permission 最终角色与功能权限声明
     * @param access 最终业务模块资源访问声明
     */
    private AccessPolicy(
            boolean anonymous,
            @Nullable AuthPrincipal principal,
            @Nullable RequirePermission permission,
            @NotNull List<Access> access) {
        this.anonymous = anonymous;
        this.principal = principal;
        this.permission = permission;
        this.access = List.copyOf(access);
    }

    /** @return 最终策略是否允许匿名执行 */
    public boolean isAnonymous() {
        return anonymous;
    }

    /** @return 最终生效的 Principal 选择声明 */
    public @Nullable AuthPrincipal getPrincipal() {
        return principal;
    }

    /** @return 最终生效的角色与权限声明 */
    public @Nullable RequirePermission getPermission() {
        return permission;
    }

    /** @return 最终生效且不可变的业务模块资源访问声明 */
    public @NotNull List<Access> getAccess() {
        return access;
    }

    /**
     * 解析方法和目标类型上最终生效的访问策略。
     *
     * @param method 处理方法
     * @param targetClass 目标类型
     * @return 最终访问策略
     */
    public static @NotNull AccessPolicy resolve(
            @NotNull Method method, @NotNull Class<?> targetClass) {
        boolean methodAnonymous = annotation(method, AnonymousAccess.class) != null;
        boolean classAnonymous = annotation(targetClass, AnonymousAccess.class) != null;
        AuthPrincipal methodPrincipal = annotation(method, AuthPrincipal.class);
        AuthPrincipal classPrincipal = annotation(targetClass, AuthPrincipal.class);
        RequirePermission methodPermission = annotation(method, RequirePermission.class);
        RequirePermission classPermission = annotation(targetClass, RequirePermission.class);
        List<Access> methodAccess = annotations(method, Access.class);
        List<Access> classAccess = annotations(targetClass, Access.class);

        validate(
                method,
                methodAnonymous,
                methodPrincipal,
                methodPermission,
                methodAccess);
        validate(
                targetClass,
                classAnonymous,
                classPrincipal,
                classPermission,
                classAccess);

        if (methodAnonymous) {
            return new AccessPolicy(true, null, null, List.of());
        }
        boolean methodSecured = methodPrincipal != null
                || methodPermission != null
                || !methodAccess.isEmpty();
        if (classAnonymous && !methodSecured) {
            return new AccessPolicy(true, null, null, List.of());
        }
        return new AccessPolicy(
                false,
                methodPrincipal == null ? classPrincipal : methodPrincipal,
                methodPermission == null ? classPermission : methodPermission,
                methodAccess.isEmpty() ? classAccess : methodAccess);
    }

    /**
     * 校验同一声明层级没有同时声明匿名和受保护访问策略。
     *
     * @param source 注解来源
     * @param anonymous 是否声明匿名访问
     * @param principal Principal 选择声明
     * @param permission 权限声明
     * @param access 业务模块资源访问声明
     */
    private static void validate(
            @NotNull Object source,
            boolean anonymous,
            @Nullable AuthPrincipal principal,
            @Nullable RequirePermission permission,
            @NotNull List<Access> access) {
        if (anonymous
                && (principal != null
                        || permission != null
                        || !access.isEmpty())) {
            throw new IllegalStateException("@AnonymousAccess 不能与受保护访问声明同时使用: " + source);
        }
        if (permission != null) {
            validatePermissionDeclaration(source, permission);
        }
    }

    /**
     * 校验角色与权限声明确实包含可执行的明确条件。
     *
     * <p>空声明不能伪装成权限门禁；空白值和通配符会使不同授权实现产生不一致含义，因此共用在策略解析阶段
     * 拒绝。
     *
     * @param source 注解来源
     * @param permission 待校验的权限声明
     */
    private static void validatePermissionDeclaration(
            @NotNull Object source, @NotNull RequirePermission permission) {
        String[] permissionCodes = permission.permissions();
        String[] roleCodes = permission.roles();
        if (permissionCodes.length == 0 && roleCodes.length == 0) {
            throw new IllegalStateException("@RequirePermission 至少声明一个权限或角色: " + source);
        }
        for (String permissionCode : permissionCodes) {
            if (permissionCode.isBlank() || "*".equals(permissionCode)) {
                throw new IllegalStateException("@RequirePermission 包含非法权限编码: " + source);
            }
        }
        for (String roleCode : roleCodes) {
            if (roleCode.isBlank() || "*".equals(roleCode)) {
                throw new IllegalStateException("@RequirePermission 包含非法角色编码: " + source);
            }
        }
    }

    /**
     * 查找合并后的单个注解。
     *
     * @param element 注解元素
     * @param annotationType 注解类型
     * @param <A> 注解类型
     * @return 合并注解
     */
    private static <A extends Annotation> @Nullable A annotation(
            @NotNull AnnotatedElement element, @NotNull Class<A> annotationType) {
        return AnnotatedElementUtils.findMergedAnnotation(element, annotationType);
    }

    /**
     * 查找合并后的可重复注解。
     *
     * @param element 注解元素
     * @param annotationType 注解类型
     * @param <A> 注解类型
     * @return 注解列表
     */
    private static <A extends Annotation> @NotNull List<A> annotations(
            @NotNull AnnotatedElement element, @NotNull Class<A> annotationType) {
        Set<A> annotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(element, annotationType);
        return List.copyOf(annotations);
    }

}
