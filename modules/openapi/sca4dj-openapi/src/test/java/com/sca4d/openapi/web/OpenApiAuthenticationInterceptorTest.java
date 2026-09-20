package com.sca4d.openapi.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.hmac.HmacProof;
import com.sca4d.auth.session.SessionService;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.openapi.client.OpenApiClient;
import com.sca4d.openapi.client.OpenApiClientService;
import com.sca4d.openapi.client.OpenApiClientStatus;
import com.sca4d.openapi.oauth.OpenApiJwtTokenService;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** 验证 OpenAPI HMAC 请求头的缺失、签名失败和 nonce 委托行为。 */
class OpenApiAuthenticationInterceptorTest {

    private final HmacAuthenticationMethod hmac = mock(HmacAuthenticationMethod.class);

    private final OpenApiClientService clients = mock(OpenApiClientService.class);

    private final OpenApiAuthenticationInterceptor interceptor = new OpenApiAuthenticationInterceptor(
            mock(OpenApiJwtTokenService.class),
            clients,
            hmac,
            mock(SessionService.class));

    @AfterEach
    void clearCall() {
        CallHolder.clear();
    }

    @Test
    @DisplayName("缺少任一 HMAC 请求头时统一返回认证失败")
    void rejectsMissingHeader() {
        MockHttpServletRequest request = request();
        request.addHeader(OpenApiAuthenticationInterceptor.CLIENT_ID_HEADER, "client-1");

        assertThatThrownBy(() -> invoke(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(AuthErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    @DisplayName("签名失败和重复 nonce 由 HMAC 认证方法统一拒绝")
    void delegatesSignatureAndNonceRejection() {
        when(clients.loadByClientId("client-1"))
                .thenReturn(
                        new OpenApiClient("client-1", 22, "test", OpenApiClientStatus.ACTIVE));
        when(clients.account(22)).thenReturn(new Account(22, 11, new RealmKey("openapi")));
        doThrow(new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED))
                .when(hmac)
                .authenticate(any(), any(HmacProof.class));

        MockHttpServletRequest first = signedRequest("a".repeat(43));
        MockHttpServletRequest second = signedRequest("a".repeat(43));

        assertThatThrownBy(() -> invoke(first)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> invoke(second)).isInstanceOf(BusinessException.class);

        verify(clients, times(2)).loadByClientId("client-1");
        verify(clients, times(2)).account(22);
        ArgumentCaptor<HmacProof> proofs = ArgumentCaptor.forClass(HmacProof.class);
        verify(hmac, times(2)).authenticate(any(Account.class), proofs.capture());
        Assertions.assertThat(proofs.getAllValues())
                .extracting(HmacProof::nonce)
                .containsExactly("0123456789abcdef", "0123456789abcdef");
    }

    private void invoke(HttpServletRequest request) throws Exception {
        Method method = RouteHandler.class.getDeclaredMethod("invoke");
        Call call = new Call("call-1", Instant.parse("2026-08-14T00:00:00Z"));
        CallHolder.set(call);
        try {
            interceptor.preHandleWithCall(
                    new OpenApiCachedBodyHttpServletRequest(request),
                    new MockHttpServletResponse(),
                    new HandlerMethod(new RouteHandler(), method),
                    call);
        } finally {
            CallHolder.clear();
        }
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/openapi/test");
    }

    private static MockHttpServletRequest signedRequest(String signature) {
        MockHttpServletRequest request = request();
        request.addHeader(OpenApiAuthenticationInterceptor.CLIENT_ID_HEADER, "client-1");
        request.addHeader(OpenApiAuthenticationInterceptor.TIMESTAMP_HEADER, "1786665600");
        request.addHeader(OpenApiAuthenticationInterceptor.NONCE_HEADER, "0123456789abcdef");
        request.addHeader(OpenApiAuthenticationInterceptor.SIGNATURE_HEADER, signature);
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        return request;
    }

    /** 测试使用的 OpenAPI 路由 Handler。 */
    static class RouteHandler {

        @OpenApiRoute(method = RequestMethod.POST, path = "/openapi/test", toolGroup = "test", toolName = "invoke", operation = "test:openapi")
        void invoke() {
        }
    }
}
