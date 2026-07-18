package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sca4d.common.auth.PrincipalSelection;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** 验证异步请求重新进入线程时恢复并清理 CurrentRequest。 */
class AsyncDispatchInterceptorTest {

    @Test
    void shouldReuseInitialAuthorizationDuringAsyncResponseDispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/realtime/sse");
        request.setDispatcherType(DispatcherType.ASYNC);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod("stream"));
        PrincipalSelection<?> selection = mock(PrincipalSelection.class);
        PrincipalSelectionInputResolver inputResolver = mock(PrincipalSelectionInputResolver.class);
        PrincipalSelectionInvoker invoker = mock(PrincipalSelectionInvoker.class);
        BeanFactory beanFactory = mock(BeanFactory.class);

        assertThat(new AuthInterceptor().preHandle(request, response, handler)).isTrue();
        assertThat(new PrincipalInterceptor(selection, inputResolver, invoker, beanFactory)
                .preHandle(request, response, handler))
                .isTrue();

        verifyNoInteractions(selection, inputResolver, invoker, beanFactory);
    }

    @Test
    void shouldNotReadClearedRequestContextDuringErrorDispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod("stream"));
        PrincipalSelection<?> selection = mock(PrincipalSelection.class);
        PrincipalSelectionInputResolver inputResolver = mock(PrincipalSelectionInputResolver.class);
        PrincipalSelectionInvoker invoker = mock(PrincipalSelectionInvoker.class);
        BeanFactory beanFactory = mock(BeanFactory.class);

        assertThat(new AuthInterceptor().preHandle(request, response, handler)).isTrue();
        assertThat(new PrincipalInterceptor(selection, inputResolver, invoker, beanFactory)
                .preHandle(request, response, handler))
                .isTrue();

        verifyNoInteractions(selection, inputResolver, invoker, beanFactory);
    }

    /** 提供异步返回值供 HandlerMethod 构造使用的测试 Controller。 */
    static class TestController {

        /** 提供没有参数和返回值的方法供 HandlerMethod 引用。 */
        public void stream() {
        }
    }
}
