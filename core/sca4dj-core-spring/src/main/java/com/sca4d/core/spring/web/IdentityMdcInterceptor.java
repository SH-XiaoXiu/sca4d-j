package com.sca4d.core.spring.web;

import com.sca4d.core.api.call.Call;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

/** 在 Controller 执行期间把当前会话、主体和账号标识写入日志上下文。 */
public class IdentityMdcInterceptor extends HttpCallHandlerInterceptor {

    /** 创建当前身份日志上下文拦截器。 */
    public IdentityMdcInterceptor() {
    }

    /**
     * 写入认证阶段已经确认的身份标识。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 当前 Controller 方法
     * @param call 当前请求唯一的调用
     * @return 始终允许请求继续执行
     */
    @Override
    protected boolean preHandleWithCall(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            @NotNull Call call) {
        call.session().ifPresent(session -> MDC.put("sessionId", session.id().toString()));
        call.subject().ifPresent(subject -> MDC.put("subjectId", Long.toString(subject.subjectId())));
        call.account().ifPresent(account -> MDC.put("accountId", Long.toString(account.accountId())));
        call.principal().ifPresent(principal -> MDC.put("principalId", Long.toString(principal.principalId())));
        return true;
    }

    /**
     * 清理当前请求写入的身份标识。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 当前 Controller 方法
     * @param exception Controller 执行异常；正常完成时为空
     */
    @Override
    public void afterCompletion(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            @Nullable Exception exception) {
        MDC.remove("principalId");
        MDC.remove("accountId");
        MDC.remove("subjectId");
        MDC.remove("sessionId");
    }
}
