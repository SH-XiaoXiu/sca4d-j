package com.sca4d.common.auth;

import com.sca4d.common.auth.annotation.AuthPrincipal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 将当前请求已选择的 Principal 投影为处理方法声明的参数类型。
 *
 * <p>HTTP 与 STOMP 的参数解析器在方法调用前可能尚未完成依赖 body 或 payload 的选择。它们可以先返回
 * {@code null}，由访问切面在选择完成后使用本类型生成最终调用参数，避免把可变占位对象泄漏给业务方法。
 */
public final class PrincipalInjection {

    private PrincipalInjection() {
    }

    /**
     * 根据参数声明生成可传入业务方法的 Principal 值。
     *
     * <p>没有已选择 Principal 时，只有声明 {@link AuthPrincipal#nullable()} 的参数可以得到 {@code null}；其余
     * 参数由调用方在进入业务方法前拒绝，防止非空 Principal 约束被静默降级。
     *
     * @param parameterType Controller 或 MessageMapping 参数的 Java 类型
     * @param declaration 参数上的 Principal 注入声明
     * @param principal 已完成请求级选择的 Principal；缺少选择时为 {@code null}
     * @return 与 {@code parameterType} 对应的 Principal、ID、类型或 {@code null}
     * @throws MissingAuthException 参数要求 Principal 但当前请求尚未选择时抛出
     */
    public static @Nullable Object value(
            @NotNull Class<?> parameterType,
            @NotNull AuthPrincipal declaration,
            @Nullable Principal principal) {
        if (principal == null) {
            if (declaration.nullable()) {
                return null;
            }
            throw new MissingAuthException();
        }
        if (Principal.class.isAssignableFrom(parameterType)) {
            return principal;
        }
        if (PrincipalType.class.isAssignableFrom(parameterType)) {
            return principal.requirePrincipalType();
        }
        if (Long.class.isAssignableFrom(parameterType)) {
            return principal.requirePrincipalId();
        }
        throw new IllegalArgumentException("@AuthPrincipal 不支持参数类型: " + parameterType.getName());
    }

    /**
     * 判断处理方法参数是否属于 Principal 注入支持范围。
     *
     * @param parameterType Controller 或 MessageMapping 参数的声明类型
     * @return 类型为 Principal、Long 或 PrincipalType 时返回 true
     */
    public static boolean supports(@NotNull Class<?> parameterType) {
        return Principal.class.isAssignableFrom(parameterType)
                || Long.class.isAssignableFrom(parameterType)
                || PrincipalType.class.isAssignableFrom(parameterType);
    }
}
