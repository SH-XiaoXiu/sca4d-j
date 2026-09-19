package com.sca4d.core.spring.identity;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 根据当前认证身份和类型化 HTTP 输入选择业务身份。
 *
 * <p>实现通过 {@link #parameterType()} 声明选择参数类型。Spring Web 适配层负责把 path、query、header、body
 * 和已绑定的 Controller 参数转换成该类型，选择器不依赖 Servlet API。
 *
 * @param <P> 当前选择器使用的类型化参数
 */
public interface PrincipalSelection<P> {

    /**
     * 判断当前选择器能否在 Controller 参数绑定前完成选择。
     *
     * <p>返回 {@code true} 的实现只能依赖当前认证身份以及 path、query 和 header；不能要求 request body
     * 或已经绑定的方法参数。默认等待参数绑定完成后再由选择切面调用。
     *
     * @return 不需要 request body 和已绑定方法参数时返回 {@code true}
     */
    default boolean supportsSelectionBeforeArgumentBinding() {
        return false;
    }

    /**
     * 返回当前选择器使用的参数类型。
     *
     * @return Spring Web 适配层需要转换到的参数类型
     */
    @NotNull
    Class<P> parameterType();

    /**
     * 根据当前认证身份和类型化请求参数选择业务身份。
     *
     * @param session 当前会话；系统调用或匿名请求没有会话时为空
     * @param subject 当前主体；匿名请求没有主体时为空
     * @param account 当前账号；系统调用或匿名请求没有账号时为空
     * @param parameters 当前选择器声明的类型化 HTTP 输入
     * @return 当前请求使用的业务身份；无法选择时为空
     */
    @Nullable
    Principal select(
            @Nullable Session session,
            @Nullable Subject subject,
            @Nullable Account account,
            @NotNull P parameters);

    /**
     * 返回不声明专用参数类时使用的 Map 类型。
     *
     * @return 键为 path、query、header、body 和方法参数名的 Map 类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static @NotNull Class<Map<String, @Nullable Object>> mapParameterType() {
        return (Class) Map.class;
    }
}
