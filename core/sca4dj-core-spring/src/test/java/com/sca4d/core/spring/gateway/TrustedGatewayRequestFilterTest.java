package com.sca4d.core.spring.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证转发路径和客户端地址只在 Gateway Secret 匹配时生效。 */
class TrustedGatewayRequestFilterTest {

    @Test
    @DisplayName("可信 Gateway 恢复外部路径和客户端地址并隐藏控制 Header")
    void acceptsTrustedForwarding() throws Exception {
        TrustedGatewayRequestFilter filter = new TrustedGatewayRequestFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/iot/device-sessions");
        request.addHeader(GatewayHttpHeaders.SECRET, "secret");
        request.addHeader(GatewayHttpHeaders.EXTERNAL_PREFIX, "/api");
        request.addHeader(GatewayHttpHeaders.CLIENT_IP, "203.0.113.8, 10.0.0.1");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ArgumentCaptor<HttpServletRequest> forwarded = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(forwarded.capture(), ArgumentMatchers.any());
        assertThat(TrustedGatewayRequest.externalPath(forwarded.getValue()))
                .isEqualTo("/api/iot/device-sessions");
        assertThat(TrustedGatewayRequest.clientIp(forwarded.getValue())).isEqualTo("203.0.113.8");
        assertThat(forwarded.getValue().getHeader(GatewayHttpHeaders.SECRET)).isNull();
        assertThat(forwarded.getValue().getHeader(GatewayHttpHeaders.CLIENT_IP)).isNull();
    }

    @Test
    @DisplayName("错误 Secret 不采用客户端伪造的转发信息")
    void ignoresUntrustedForwarding() throws Exception {
        TrustedGatewayRequestFilter filter = new TrustedGatewayRequestFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resource");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(GatewayHttpHeaders.SECRET, "wrong");
        request.addHeader(GatewayHttpHeaders.EXTERNAL_PREFIX, "/api");
        request.addHeader(GatewayHttpHeaders.CLIENT_IP, "203.0.113.8");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ArgumentCaptor<HttpServletRequest> forwarded = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(forwarded.capture(), ArgumentMatchers.any());
        assertThat(TrustedGatewayRequest.externalPath(forwarded.getValue())).isEqualTo("/resource");
        assertThat(TrustedGatewayRequest.clientIp(forwarded.getValue())).isEqualTo("127.0.0.1");
    }
}
