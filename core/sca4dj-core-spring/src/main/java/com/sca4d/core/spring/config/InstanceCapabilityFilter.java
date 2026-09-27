package com.sca4d.core.spring.config;

import com.sca4d.core.api.gateway.GatewayHttpPaths;
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

/** 在业务 API 入口检查本实例能力，不替代认证、权限或 Gateway 回调校验。 */
public final class InstanceCapabilityFilter extends OncePerRequestFilter {

    private final @NotNull InstanceCapabilities capabilities;

    private final @NotNull Map<String, Set<TreeCode>> requirements;

    public InstanceCapabilityFilter(@NotNull InstanceCapabilities capabilities) {
        this.capabilities = Objects.requireNonNull(capabilities);
        this.requirements = capabilities.apiRequirements();
    }

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
                || path.startsWith(GatewayHttpPaths.CALLBACK_PREFIX)) {
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
