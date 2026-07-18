package com.sca4d.common.concurrent.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/** 验证限流请求信息快照不会保留可变请求对象。 */
class RateLimitContextTest {

    @Test
    void shouldUseContainerRemoteAddressInsteadOfUntrustedForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/authentications");
        request.setRemoteAddr("192.0.2.30");
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/users/{userId}");

        RateLimitContext context = RateLimitContext.http(request);

        assertThat(context.getClientIp()).isEqualTo("192.0.2.30");
        assertThat(context.getEndpoint()).isEqualTo("POST:/users/{userId}");
    }

    @Test
    void shouldRejectBlankEndpoint() {
        assertThatThrownBy(() -> new RateLimitContext(" ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("限流 endpoint 不能为空");
    }

    @Test
    void shouldRoundLargeRetryDelayWithoutOverflow() {
        RateLimitResult result = RateLimitResult.rejected(0, Long.MAX_VALUE);

        assertThat(result.getRetryAfterSeconds()).isPositive();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(Math.ceilDiv(Long.MAX_VALUE, 1_000_000_000L));
    }
}
