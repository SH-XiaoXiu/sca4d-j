package com.sca4d.core.spring.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.web.CurrentIdentityArgumentResolver;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.CurrentSubject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class PrincipalSelectionInterceptorTest {

    @AfterEach
    void clearCall() {
        CallHolder.clear();
    }

    @Test
    @DisplayName("Selects early principal before resolving controller arguments")
    void selectsEarlyPrincipalBeforeResolvingControllerArguments() throws Exception {
        TestPrincipal principal = authenticatedCall();
        EarlySelection selection = new EarlySelection(principal);
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("earlySelection", selection);
        PrincipalSelectionInterceptor interceptor = new PrincipalSelectionInterceptor(
                applicationContext,
                new PrincipalSelectionInputResolver(),
                new PrincipalSelectionInvoker());
        HandlerMethod handler = new HandlerMethod(
                new TestController(),
                TestController.class.getDeclaredMethod("upload", TestPrincipal.class, UploadBody.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/files/uploads");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("scope", "platform"));
        request.addHeader("X-Principal-Id", "31");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        Object resolved = new CurrentIdentityArgumentResolver().resolveArgument(
                new MethodParameter(handler.getMethod(), 0),
                null,
                mock(NativeWebRequest.class),
                null);

        assertThat(resolved).isSameAs(principal);
        assertThat(selection.parameters).containsEntry("body", null);
        assertThat(selection.parameters.get("path")).isEqualTo(Map.of("scope", "platform"));
    }

    @Test
    @DisplayName("Rejects a concrete principal parameter that does not match the selection")
    void rejectsConcretePrincipalParameterThatDoesNotMatchTheSelection() throws Exception {
        TestPrincipal principal = authenticatedCall();
        CallHolder.current().selectPrincipal(principal);
        HandlerMethod handler = new HandlerMethod(
                new TestController(),
                TestController.class.getDeclaredMethod("mismatched", OtherPrincipal.class));

        assertThatThrownBy(
                () -> new CurrentIdentityArgumentResolver().resolveArgument(
                        new MethodParameter(handler.getMethod(), 0),
                        null,
                        mock(NativeWebRequest.class),
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OtherPrincipal.class.getName())
                .hasMessageContaining(TestPrincipal.class.getName());
    }

    @Test
    @DisplayName("Resolves required identity IDs as primitive or boxed long values")
    void resolvesNumericIdentityIds() throws Exception {
        TestPrincipal principal = authenticatedCall();
        CallHolder.current().selectPrincipal(principal);
        HandlerMethod handler = new HandlerMethod(
                new TestController(),
                TestController.class.getDeclaredMethod(
                        "numeric",
                        long.class,
                        Long.class,
                        long.class));
        CurrentIdentityArgumentResolver resolver = new CurrentIdentityArgumentResolver();

        assertThat(
                resolver.resolveArgument(
                        new MethodParameter(handler.getMethod(), 0),
                        null,
                        mock(NativeWebRequest.class),
                        null))
                .isEqualTo(11L);
        assertThat(
                resolver.resolveArgument(
                        new MethodParameter(handler.getMethod(), 1),
                        null,
                        mock(NativeWebRequest.class),
                        null))
                .isEqualTo(21L);
        assertThat(
                resolver.resolveArgument(
                        new MethodParameter(handler.getMethod(), 2),
                        null,
                        mock(NativeWebRequest.class),
                        null))
                .isEqualTo(31L);
    }

    @Test
    @DisplayName("Leaves bound argument selection to the controller aspect")
    void leavesBoundArgumentSelectionToTheControllerAspect() throws Exception {
        authenticatedCall();
        LateSelection selection = new LateSelection();
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("lateSelection", selection);
        PrincipalSelectionInterceptor interceptor = new PrincipalSelectionInterceptor(
                applicationContext,
                new PrincipalSelectionInputResolver(),
                new PrincipalSelectionInvoker());
        HandlerMethod handler = new HandlerMethod(
                new LateController(),
                LateController.class.getDeclaredMethod("update", UploadBody.class));

        assertThat(
                interceptor.preHandle(
                        new MockHttpServletRequest("PUT", "/resource"),
                        new MockHttpServletResponse(),
                        handler))
                .isTrue();
        assertThat(selection.called).isFalse();
        assertThat(CallHolder.current().isPrincipalAssessed()).isFalse();
    }

    @Test
    @DisplayName("Rejects anonymous requests before selecting a required principal")
    void rejectsAnonymousRequestsBeforeSelectingARequiredPrincipal() throws Exception {
        CallHolder.set(
                new Call(
                        "call-1",
                        Instant.parse("2026-07-27T00:00:00Z")));
        EarlySelection selection = new EarlySelection(mock(Principal.class));
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("earlySelection", selection);
        PrincipalSelectionInterceptor interceptor = new PrincipalSelectionInterceptor(
                applicationContext,
                new PrincipalSelectionInputResolver(),
                new PrincipalSelectionInvoker());
        HandlerMethod handler = new HandlerMethod(
                new TestController(),
                TestController.class.getDeclaredMethod("upload", TestPrincipal.class, UploadBody.class));

        assertThatThrownBy(
                () -> interceptor.preHandle(
                        new MockHttpServletRequest("POST", "/files/uploads"),
                        new MockHttpServletResponse(),
                        handler))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode())
                                .isEqualTo(CoreErrorCode.AUTHENTICATION_REQUIRED));
        assertThat(selection.parameters).isNull();
    }

    /** 建立已认证但尚未选择业务身份的测试调用。 */
    private static TestPrincipal authenticatedCall() {
        long principalId = 31L;
        TestPrincipal principal = new TestPrincipal(principalId);
        Subject subject = mock(Subject.class);
        when(subject.subjectId()).thenReturn(11L);
        Account account = mock(Account.class);
        when(account.accountId()).thenReturn(21L);
        Session session = mock(Session.class);
        when(session.subject()).thenReturn(subject);
        when(session.account()).thenReturn(account);
        when(session.principalById(principalId)).thenReturn(principal);
        when(session.principal(principal)).thenReturn(principal);
        when(session.resources(principalId)).thenReturn(List.of());
        Call call = new Call("call-1", Instant.parse("2026-07-27T00:00:00Z"));
        call.authenticate(session);
        CallHolder.set(call);
        return principal;
    }

    /** 提供可在 MVC 参数绑定前执行的测试选择器。 */
    private static final class EarlySelection implements PrincipalSelection<Map<String, @Nullable Object>> {

        private final Principal principal;

        private Map<String, @Nullable Object> parameters;

        private EarlySelection(Principal principal) {
            this.principal = principal;
        }

        @Override
        public boolean supportsSelectionBeforeArgumentBinding() {
            return true;
        }

        @Override
        public @NotNull Class<Map<String, @Nullable Object>> parameterType() {
            return PrincipalSelection.mapParameterType();
        }

        @Override
        public Principal select(
                @Nullable Session session,
                @Nullable Subject subject,
                @Nullable Account account,
                @NotNull Map<String, @Nullable Object> parameters) {
            this.parameters = parameters;
            return principal;
        }
    }

    /** 提供必须等待 request body 绑定完成的测试选择器。 */
    private static final class LateSelection implements PrincipalSelection<Map<String, @Nullable Object>> {

        private boolean called;

        @Override
        public @NotNull Class<Map<String, @Nullable Object>> parameterType() {
            return PrincipalSelection.mapParameterType();
        }

        @Override
        public @Nullable Principal select(
                @Nullable Session session,
                @Nullable Subject subject,
                @Nullable Account account,
                @NotNull Map<String, @Nullable Object> parameters) {
            called = true;
            return null;
        }
    }

    /** 提供包含 Principal 参数和 request body 的测试 Controller。 */
    @CurrentPrincipal(selection = EarlySelection.class)
    @SuppressWarnings("unused")
    private static final class TestController {

        public void upload(@CurrentPrincipal TestPrincipal principal, @RequestBody UploadBody body) {
        }

        public void mismatched(@CurrentPrincipal OtherPrincipal principal) {
        }

        public void numeric(
                @CurrentSubject long subjectId,
                @CurrentAccount Long accountId,
                @CurrentPrincipal long principalId) {
        }
    }

    /** 提供只能在参数绑定后选择业务身份的测试 Controller。 */
    @CurrentPrincipal(selection = LateSelection.class)
    @SuppressWarnings("unused")
    private static final class LateController {

        public void update(@RequestBody UploadBody body) {
        }
    }

    /** 表示测试 Controller 接收的上传请求。 */
    private static final class UploadBody {
    }

    /** 表示选择器实际返回的具体业务身份。 */
    @SuppressWarnings("ClassCanBeRecord")
    private static final class TestPrincipal implements Principal {

        private final long principalId;

        private TestPrincipal(long principalId) {
            this.principalId = principalId;
        }

        @Override
        public long principalId() {
            return principalId;
        }
    }

    /** 表示 Controller 错误声明的另一种业务身份。 */
    private static final class OtherPrincipal implements Principal {

        @Override
        public long principalId() {
            return 32L;
        }
    }
}
