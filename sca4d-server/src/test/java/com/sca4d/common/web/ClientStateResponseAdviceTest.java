package com.sca4d.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.authentication.AuthenticationClientStateTargets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;

/** 验证响应写出前追加客户端刷新标记。 */
class ClientStateResponseAdviceTest {

    private final ClientStateResponseAdvice advice = new ClientStateResponseAdvice();

    @AfterEach
    void clearClientState() {
        ClientState.clear();
    }

    @Test
    void shouldWriteStateHeadersBeforeResponseBodyIsCommitted() throws Exception {
        ClientState.stale();
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        ClientState.refresh(ClientStateTarget.of("operation-logs"));
        ClientState.version("v2");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/demo");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        MethodParameter returnType = new MethodParameter(getClass()
                .getDeclaredMethod("shouldWriteStateHeadersBeforeResponseBodyIsCommitted"), -1);
        ApiResponse<String> responseBody = ApiResponse.created("body", URI.create("/resources/1"), 0L);

        Object body = advice.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                response);
        response.flush();

        assertThat(body).isSameAs(responseBody);
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(servletResponse.getHeader("Location")).isEqualTo("/resources/1");
        assertThat(servletResponse.getHeader("ETag")).isEqualTo("\"0\"");
        assertThat(servletResponse.getHeader(ClientState.STALE_HEADER)).isEqualTo("true");
        assertThat(servletResponse.getHeader(ClientState.REFRESH_HEADER)).isEqualTo("me,operation-logs");
        assertThat(servletResponse.getHeader(ClientState.VERSION_HEADER)).isEqualTo("v2");
    }
}
