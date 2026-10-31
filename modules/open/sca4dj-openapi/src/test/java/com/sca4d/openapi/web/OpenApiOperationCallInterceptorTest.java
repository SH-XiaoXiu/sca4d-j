package com.sca4d.openapi.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.open.client.OpenClientOperationChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

/** 验证开放 API Operation 检查在 Call 生命周期中执行，不依赖 OpenAPI 注解。 */
class OpenApiOperationCallInterceptorTest {

    private final OpenApiRouteCatalog routes = mock(OpenApiRouteCatalog.class);

    private final OpenClientOperationChecker checker = mock(OpenClientOperationChecker.class);

    private final OpenApiOperationCallInterceptor interceptor = new OpenApiOperationCallInterceptor(routes, checker);

    private final Method method = method();

    private final OpenApiRouteDefinition route = mock(OpenApiRouteDefinition.class);

    @BeforeEach
    void registerRoute() {
        when(route.operation()).thenReturn(TreeCode.ofColon("test:openapi"));
        when(routes.find(RouteHandler.class, method)).thenReturn(Optional.of(route));
    }

    @Test
    @DisplayName("已认证的 APPLICATION Call 执行开放 Operation 检查")
    void verifiesAuthorizedApplicationCall() {
        Call call = authenticatedCall(SubjectType.APPLICATION);
        CallExecution execution = execution(call);

        interceptor.before(execution);

        verify(checker).verifyAllowed(call, TreeCode.ofColon("test:openapi"));
    }

    @Test
    @DisplayName("未认证的开放 Call 在 Operation 检查前失败")
    void rejectsUnauthenticatedCall() {
        Call call = new Call("call-1", Instant.parse("2026-08-14T00:00:00Z"));

        assertThatThrownBy(() -> interceptor.before(execution(call)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CoreErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    @DisplayName("已认证但不是 APPLICATION 的 Call 不能执行开放 Operation")
    void rejectsNonApplicationCall() {
        Call call = authenticatedCall(SubjectType.HUMAN);

        assertThatThrownBy(() -> interceptor.before(execution(call)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CoreErrorCode.AUTHENTICATION_REQUIRED);
    }

    private CallExecution execution(Call call) {
        return new CallExecution(call, RouteHandler.class, method, new Object[0], Void.TYPE);
    }

    private static Call authenticatedCall(SubjectType type) {
        Call call = new Call("call-1", Instant.parse("2026-08-14T00:00:00Z"));
        Session session = mock(Session.class);
        when(session.subject()).thenReturn(new Subject(1, type));
        when(session.account()).thenReturn(mock(Account.class));
        call.authenticate(session);
        return call;
    }

    private static Method method() {
        try {
            return RouteHandler.class.getDeclaredMethod("invoke");
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    /** 不声明 OpenAPI 注解的测试 Controller 方法。 */
    static class RouteHandler {

        void invoke() {
        }
    }
}
