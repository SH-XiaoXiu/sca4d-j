package com.sca4d.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.session.SessionService;
import com.sca4d.auth.testing.SessionTestFixture;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.web.clientstate.ClientState;
import com.sca4d.core.spring.web.clientstate.ClientStateTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

class AuthRequestInterceptorTest {

    @AfterEach
    void clearCall() {
        CallHolder.clear();
        ClientState.clear();
    }

    @Test
    @DisplayName("Uses The Default Token Mode For An Unannotated Handler")
    void usesTheDefaultTokenModeForAnUnannotatedHandler() throws Exception {
        SessionService coordinator = Mockito.mock(SessionService.class);
        when(coordinator.currentSession("access-token", false))
                .thenReturn(
                        Optional.of(
                                SessionTestFixture.session(
                                        SessionTestFixture.NOW.plusSeconds(60))));
        AuthRequestInterceptor interceptor = new AuthRequestInterceptor(coordinator);
        MockHttpServletRequest request = request("Bearer access-token");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler("ordinary"));

        verify(coordinator).currentSession("access-token", false);
        assertThat(CallHolder.current().isAuthenticationAssessed()).isTrue();
    }

    @Test
    @DisplayName("Forces A Session Token For A Method Annotation Without Falling Back")
    void forcesASessionTokenForAMethodAnnotationWithoutFallingBack() throws Exception {
        SessionService coordinator = Mockito.mock(SessionService.class);
        when(coordinator.currentSession("opaque-token", true))
                .thenReturn(
                        Optional.of(
                                SessionTestFixture.session(
                                        SessionTestFixture.NOW.plusSeconds(60))));
        AuthRequestInterceptor interceptor = new AuthRequestInterceptor(coordinator);

        interceptor.preHandle(
                request("Bearer opaque-token"),
                new MockHttpServletResponse(),
                handler("sensitive"));

        verify(coordinator).currentSession("opaque-token", true);
        verify(coordinator, never()).currentSession("opaque-token", false);
    }

    @Test
    @DisplayName("Forces A Session Token For A Controller Annotation")
    void forcesASessionTokenForAControllerAnnotation() throws Exception {
        SessionService coordinator = Mockito.mock(SessionService.class);
        when(coordinator.currentSession("opaque-token", true))
                .thenReturn(
                        Optional.of(
                                SessionTestFixture.session(
                                        SessionTestFixture.NOW.plusSeconds(60))));
        AuthRequestInterceptor interceptor = new AuthRequestInterceptor(coordinator);

        interceptor.preHandle(
                request("Bearer opaque-token"),
                new MockHttpServletResponse(),
                annotatedHandler());

        verify(coordinator).currentSession("opaque-token", true);
    }

    @Test
    @DisplayName("Allows An Anonymous Handler With A Malformed Authorization Header")
    void allowsAnAnonymousHandlerWithAMalformedAuthorizationHeader() throws Exception {
        SessionService coordinator = Mockito.mock(SessionService.class);
        AuthRequestInterceptor interceptor = new AuthRequestInterceptor(coordinator);

        interceptor.preHandle(
                request("opaque-token"),
                new MockHttpServletResponse(),
                handler("anonymous"));

        verify(coordinator, never())
                .currentSession(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean());
        assertThat(ClientState.currentOrNull()).isNull();
    }

    @Test
    @DisplayName("Rejects An Unauthenticated Protected Handler Before Argument Binding")
    void rejectsAnUnauthenticatedProtectedHandlerBeforeArgumentBinding() {
        SessionService coordinator = Mockito.mock(SessionService.class);
        AuthRequestInterceptor interceptor = new AuthRequestInterceptor(coordinator);

        assertThatThrownBy(
                () -> interceptor.preHandle(
                        request(null),
                        new MockHttpServletResponse(),
                        handler("ordinary")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CoreErrorCode.AUTHENTICATION_REQUIRED));

        verify(coordinator, never())
                .currentSession(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean());
        assertThat(Objects.requireNonNull(ClientState.currentOrNull()).targets())
                .extracting(ClientStateTarget::value)
                .containsExactly("authentication.session");
    }

    private static MockHttpServletRequest request(String authorization) {
        CallHolder.set(new Call("call", SessionTestFixture.NOW));
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(name);
        return new HandlerMethod(new TestController(), method);
    }

    private static HandlerMethod annotatedHandler() throws NoSuchMethodException {
        Method method = AnnotatedTestController.class.getDeclaredMethod("ordinary");
        return new HandlerMethod(new AnnotatedTestController(), method);
    }

    @SuppressWarnings("unused") // 测试通过反射取得 Controller 方法。
    private static class TestController {
        public void ordinary() {
        }

        @AnonymousAccess
        public void anonymous() {
        }

        @RequireSessionToken
        public void sensitive() {
        }
    }

    @RequireSessionToken
    @SuppressWarnings("unused") // 测试通过反射取得 Controller 方法。
    private static class AnnotatedTestController {
        public void ordinary() {
        }
    }
}
