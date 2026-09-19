package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.spring.web.clientstate.ClientState;
import com.sca4d.core.spring.web.clientstate.ClientStateFilter;
import com.sca4d.core.spring.web.clientstate.ClientStateResponseAdvice;
import com.sca4d.core.spring.web.clientstate.ClientStateTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证 ClientState 响应组件只写入当前请求登记的状态刷新头。 */
class ClientStateResponseAdviceTest {

    private final ClientStateResponseAdvice advice = new ClientStateResponseAdvice();

    @AfterEach
    void clearClientState() {
        ClientState.clear();
    }

    @Test
    @DisplayName("Writes Ordered Client State Headers Without Replacing Body")
    void writesOrderedClientStateHeadersWithoutReplacingBody() throws Exception {
        ClientState.refresh(ClientStateTarget.of("account"));
        ClientState.refresh(ClientStateTarget.of("permissions"));
        ClientState.refresh(ClientStateTarget.of("account"));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/demo");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        MethodParameter returnType = new MethodParameter(
                getClass().getDeclaredMethod("writesOrderedClientStateHeadersWithoutReplacingBody"),
                -1);
        Object responseBody = new Object();

        Object body = advice.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                response);
        response.flush();

        assertThat(body).isSameAs(responseBody);
        assertThat(servletResponse.getHeader(ClientState.REFRESH_HEADER))
                .isEqualTo("account,permissions");
    }

    @Test
    @DisplayName("Filter Writes Headers And Clears Request State")
    void filterWritesHeadersAndClearsRequestState() throws Exception {
        ClientState.refresh(ClientStateTarget.of("account"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ClientStateFilter().doFilter(
                new MockHttpServletRequest(),
                response,
                new MockFilterChain());

        assertThat(response.getHeader(ClientState.REFRESH_HEADER)).isEqualTo("account");
        assertThat(ClientState.currentOrNull()).isNull();
    }
}
