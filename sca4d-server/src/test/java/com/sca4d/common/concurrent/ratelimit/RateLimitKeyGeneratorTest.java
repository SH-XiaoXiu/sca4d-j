package com.sca4d.common.concurrent.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 验证限流 key 按 IP、Subject、Principal 和 SpEL 生成。 */
class RateLimitKeyGeneratorTest {

    @AfterEach
    void clearAuth() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldUsePrincipalBeforeSubjectForUserKey() {
        Sca4djConcurrentProperties properties = new Sca4djConcurrentProperties();
        properties.getRateLimit().setKeyPrefix("rl");
        CurrentRequest auth = new CurrentRequest();
        auth.setSubject(new Subject(7L, "Alice"));
        auth.setPrincipal(Principal.of(11L, PrincipalType.DEFAULT, 7L, 100L));
        CurrentRequestHolder.set(auth);

        RateLimitKeyGenerator generator = new RateLimitKeyGenerator(properties);

        assertThat(generator.generate(RateLimitType.USER, httpContext(), null))
                .isEqualTo("rl:user:" + DigestUtil.sha256Hex("principal:11") + ":"
                        + DigestUtil.sha256Hex("GET:/orders"));
    }

    @Test
    void shouldUseContextIpUserAgentAndConnection() {
        Sca4djConcurrentProperties properties = new Sca4djConcurrentProperties();
        properties.getRateLimit().setKeyPrefix("rl");

        RateLimitKeyGenerator generator = new RateLimitKeyGenerator(properties);
        RateLimitContext context = new RateLimitContext(
                "WS:SEND:/app/chat.send", "10.0.0.1", "Demo Browser/1.0", "sess-1");

        assertThat(generator.generate(RateLimitType.IP, context, null))
                .isEqualTo("rl:ip:" + DigestUtil.sha256Hex("10.0.0.1") + ":"
                        + DigestUtil.sha256Hex("WS:SEND:/app/chat.send"));
        assertThat(generator.generate(RateLimitType.UA, context, null))
                .isEqualTo("rl:ua:" + DigestUtil.sha256Hex("Demo Browser/1.0") + ":"
                        + DigestUtil.sha256Hex("WS:SEND:/app/chat.send"));
        assertThat(generator.generate(RateLimitType.CONNECTION, context, null))
                .isEqualTo("rl:connection:" + DigestUtil.sha256Hex("sess-1") + ":"
                        + DigestUtil.sha256Hex("WS:SEND:/app/chat.send"));
    }

    @Test
    void shouldKeepCustomDimensionCaseSensitiveAndOpaque() {
        Sca4djConcurrentProperties properties = new Sca4djConcurrentProperties();
        properties.getRateLimit().setKeyPrefix("rl");
        RateLimitKeyGenerator generator = new RateLimitKeyGenerator(properties);

        String upper = generator.generate(RateLimitType.CUSTOM, httpContext(), "Customer-A");
        String lower = generator.generate(RateLimitType.CUSTOM, httpContext(), "customer-a");

        assertThat(upper).isNotEqualTo(lower).doesNotContain("Customer-A");
        assertThat(lower).doesNotContain("customer-a");
    }

    /**
     * 创建固定 HTTP 路由、地址和 User-Agent 的限流上下文。
     *
     * @return 不包含连接标识的 HTTP 限流上下文
     */
    private @NotNull RateLimitContext httpContext() {
        return new RateLimitContext("GET:/orders", "127.0.0.1", "JUnit", null);
    }
}
