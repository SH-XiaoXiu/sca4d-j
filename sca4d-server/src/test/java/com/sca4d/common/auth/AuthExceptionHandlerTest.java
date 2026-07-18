package com.sca4d.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

/** 验证未登录和无权限异常转换为规定的响应码与消息键。 */
class AuthExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(LocalizedMessagesFixture.create());

    @Test
    void shouldHandleUnauthenticatedExceptionAsBizException() {
        var response = handler.handleBizException(new UnauthenticatedException(), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldReturnStatusWithoutJsonBodyForUnauthenticatedEventStreamRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.TEXT_EVENT_STREAM_VALUE);

        var response = handler.handleBizException(new UnauthenticatedException(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldHandleForbiddenExceptionAsBizException() {
        var response = handler.handleBizException(new ForbiddenException(), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
        assertThat(response.getBody().getMessageKey()).isEqualTo(CommonErrorCode.FORBIDDEN.getMessageKey());
    }
}
