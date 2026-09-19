package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.spring.web.annotation.IfMatch;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class IfMatchInterceptorTest {

    private final RequestVersionConditions versionConditions = new RequestVersionConditions();

    private final IfMatchInterceptor interceptor = new IfMatchInterceptor(versionConditions);

    @AfterEach
    void clearVersionConditions() {
        versionConditions.clear();
    }

    @Test
    @DisplayName("Binds Single Strong ETag As Request Version Condition")
    void bindsSingleStrongEtagAsRequestVersionCondition() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.IF_MATCH, "\"12\"");

        assertThat(
                interceptor.preHandle(
                        request,
                        new MockHttpServletResponse(),
                        new HandlerMethod(this, getClass().getDeclaredMethod("versionAware"))))
                .isTrue();

        assertThat(versionConditions.resolve(new Version(99)).expected()).isEqualTo(new Version(12));
        assertThatThrownBy(() -> versionConditions.resolve(new Version(12)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能由一个根资源写入消费");
    }

    @Test
    @DisplayName("Rejects Missing ETag As Required Precondition")
    void rejectsMissingEtagAsRequiredPrecondition() {
        assertThatThrownBy(
                () -> interceptor.preHandle(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        new HandlerMethod(this, getClass().getDeclaredMethod("versionAware"))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(HttpErrorCode.PRECONDITION_REQUIRED));
    }

    @Test
    @DisplayName("Rejects Weak Wildcard Multiple Or Nonnumeric ETags")
    void rejectsInvalidEtags() {
        for (String value : new String[] {"W/\"12\"", "*", "\"12\", \"13\"", "\"current\""}) {
            versionConditions.clear();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HttpHeaders.IF_MATCH, value);

            assertThatThrownBy(
                    () -> interceptor.preHandle(
                            request,
                            new MockHttpServletResponse(),
                            new HandlerMethod(this, getClass().getDeclaredMethod("versionAware"))))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(HttpErrorCode.PRECONDITION_FAILED));
        }
    }

    @Test
    @DisplayName("Ignores Method Without If Match Contract")
    void ignoresMethodWithoutIfMatchContract() throws Exception {
        assertThat(
                interceptor.preHandle(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        new HandlerMethod(this, getClass().getDeclaredMethod("ordinary"))))
                .isTrue();
        assertThat(versionConditions.resolve(new Version(7)).expected()).isEqualTo(new Version(7));
    }

    @Test
    @DisplayName("Skips If Match Binding During Async Redispatch")
    void skipsIfMatchBindingDuringAsyncRedispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);

        assertThat(
                interceptor.preHandle(
                        request,
                        new MockHttpServletResponse(),
                        new HandlerMethod(this, getClass().getDeclaredMethod("versionAware"))))
                .isTrue();
        assertThat(versionConditions.resolve(new Version(7)).expected()).isEqualTo(new Version(7));
    }

    @IfMatch
    private void versionAware() {
    }

    private void ordinary() {
    }
}
