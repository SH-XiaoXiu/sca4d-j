package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

/** 验证 MVC 参数绑定前选择 Principal 时同步安装对应会话权限。 */
class PrincipalInterceptorTest {

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldInstallSessionPermissionWithoutPermissionAnnotation() throws Exception {
        Principal principal = Principal.of(10L, PrincipalType.DEFAULT, 1L, null);
        CurrentRequest request = CurrentRequest.authenticated(TestSession.withAuthorization(
                new Subject(1L, "Subject 1"),
                List.of(principal),
                Map.of(10L, new SessionPermission(10L, null, null))));
        CurrentRequestHolder.set(request);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("DELETE", "/me/accounts/1/sessions/s-1");
        servletRequest.setAttribute(
                "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern",
                "/me/accounts/{accountId}/sessions/{sessionId}");
        servletRequest.setAttribute(
                "org.springframework.web.servlet.HandlerMapping.uriTemplateVariables",
                Map.of("accountId", "1", "sessionId", "s-1"));
        HandlerMethod handler = new HandlerMethod(
                new TestController(), TestController.class.getDeclaredMethod("endSession"));
        PrincipalInterceptor interceptor = new PrincipalInterceptor(
                new FixedPrincipalSelection(principal),
                new PrincipalSelectionInputResolver(),
                new PrincipalSelectionInvoker(new ObjectMapper()),
                new StaticListableBeanFactory());

        assertThat(interceptor.preHandle(
                servletRequest, new MockHttpServletResponse(), handler))
                .isTrue();
        assertThat(request.requirePrincipal()).isSameAs(principal);
        assertThat(request.requireSessionPermission().getPrincipalId()).isEqualTo(10L);
    }

    /** 提供只声明 Principal 选择、不声明功能权限的处理方法。 */
    private static final class TestController {

        @AuthPrincipal(PrincipalType.DEFAULT)
        public void endSession() {
        }
    }

    /** 为拦截器测试返回当前 Session 已包含的固定 Principal。 */
    private static final class FixedPrincipalSelection implements PrincipalSelection<Map<String, Object>> {

        private final Principal principal;

        /** @param principal 当前 Session 允许选择的 Principal */
        private FixedPrincipalSelection(@NotNull Principal principal) {
            this.principal = principal;
        }

        @Override
        public @NotNull Class<Map<String, Object>> parameterType() {
            return PrincipalSelection.mapParameterType();
        }

        @Override
        public @NotNull Principal select(
                @NotNull CurrentRequest request,
                @NotNull PrincipalType[] allowedTypes,
                @Nullable Long principalId,
                @Nullable Long tenantId,
                @Nullable String selectionKey,
                @NotNull Map<String, Object> parameters) {
            return principal;
        }
    }
}
