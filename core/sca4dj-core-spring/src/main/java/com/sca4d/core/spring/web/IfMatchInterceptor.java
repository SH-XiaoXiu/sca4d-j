package com.sca4d.core.spring.web;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.spring.web.annotation.IfMatch;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 为声明 `@IfMatch` 的 HTTP 方法解析强 ETag，并绑定独立的请求版本条件。 */
public class IfMatchInterceptor implements HandlerInterceptor {

    private final RequestVersionConditions versionConditions;

    /**
     * 创建方法级 `If-Match` 拦截器。
     *
     * @param versionConditions HTTP 请求版本条件入口
     */
    public IfMatchInterceptor(RequestVersionConditions versionConditions) {
        this.versionConditions = Objects.requireNonNull(versionConditions, "HTTP 请求版本条件入口不能为空");
    }

    /**
     * 解析带 {@link IfMatch} 的方法请求中的 If-Match 头，并把版本交给 RequestVersionConditions。
     * 异步分派直接放行，不重复校验。Header 缺失或为空白时抛出 HTTP 428 对应的 PRECONDITION_REQUIRED；
     * 多个 Header 值、弱 ETag、通配符 ETag 或版本号不是整数时抛出 412 对应的 PRECONDITION_FAILED。
     * 校验失败时不绑定版本，请求在进入 Controller 之前结束。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 已选择的 Handler
     * @return 非 {@link IfMatch} 方法或校验通过时为 {@code true}
     * @throws BusinessException If-Match 缺失、重复或不符合强 ETag 格式时抛出，错误码见上文
     */
    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler) {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(IfMatch.class)) {
            return true;
        }
        List<String> values = Collections.list(request.getHeaders(HttpHeaders.IF_MATCH));
        if (values.isEmpty() || values.getFirst().isBlank()) {
            throw new BusinessException(HttpErrorCode.PRECONDITION_REQUIRED);
        }
        if (values.size() != 1) {
            throw new BusinessException(HttpErrorCode.PRECONDITION_FAILED);
        }
        try {
            List<ETag> tags = ETag.parse(values.getFirst());
            if (tags.size() != 1 || tags.getFirst().weak() || tags.getFirst().isWildcard()) {
                throw new BusinessException(HttpErrorCode.PRECONDITION_FAILED);
            }
            versionConditions.bind(new Version(Long.parseLong(tags.getFirst().tag())));
            return true;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpErrorCode.PRECONDITION_FAILED, exception);
        }
    }
}
