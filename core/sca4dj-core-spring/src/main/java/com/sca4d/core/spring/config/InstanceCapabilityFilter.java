package com.sca4d.core.spring.config;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 在业务 API 入口检查当前实例是否启用了请求路径所需的功能，不替代认证、权限或 Gateway 回调校验。 */
public class InstanceCapabilityFilter extends OncePerRequestFilter {

    private final @NotNull InstanceCapabilities capabilities;

    private final @NotNull Map<String, Set<TreeCode>> requirements;

    /** @param capabilities 当前实例启用的功能，以及各 API 前缀所需的功能编码 */
    public InstanceCapabilityFilter(@NotNull InstanceCapabilities capabilities) {
        this.capabilities = Objects.requireNonNull(capabilities);
        this.requirements = capabilities.apiRequirements();
    }

    /**
     * 按最长匹配的 API 前缀检查所需功能；任一功能未启用时直接返回 503，请求不再进入后续过滤器和 Controller。
     * 以 `/actuator` 和回调路径开头的请求不做检查。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param chain 后续 Servlet 过滤器链
     * @throws ServletException 后续过滤器或请求处理失败时抛出
     * @throws IOException 读取或写入请求响应失败时抛出
     */
    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        if (path.equals("/actuator")
                || path.startsWith("/actuator/")
                || path.startsWith(GatewayConstants.CALLBACK_PATH + "/")) {
            chain.doFilter(request, response);
            return;
        }
        int longest = -1;
        Set<TreeCode> required = Set.of();
        for (Map.Entry<String, Set<TreeCode>> entry : requirements.entrySet()) {
            String prefix = entry.getKey();
            if ((prefix.equals("/") || path.equals(prefix) || path.startsWith(prefix + '/'))
                    && prefix.length() > longest) {
                longest = prefix.length();
                required = entry.getValue();
            }
        }
        if (!capabilities.enabledCodes().containsAll(required)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }
}
