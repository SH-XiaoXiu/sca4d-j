package com.sca4d.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/** 在所有 MVC 响应中声明后端实际采用的内容语言。 */
public class LocaleResponseInterceptor implements HandlerInterceptor {

    /**
     * 在 Controller 执行前写入当前请求已经解析完成的内容语言。
     *
     * <p>Spring 的 LocaleResolver 在进入拦截器前已经根据 Accept-Language 建立 LocaleContext，因此同步响应和异常响应
     * 使用同一语言数据，不需要 Controller 重复处理语言头。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring 选中的处理器
     * @return 始终继续执行请求链
     */
    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        response.setHeader("Content-Language", LocaleContextHolder.getLocale().toLanguageTag());
        return true;
    }
}
