package com.sca4d.core.spring.redis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.util.function.Supplier;

class RedisRateLimiterTest {
    static {
        AppName.publish("sca4d-test");
    }

    @Test
    @DisplayName("Uses The Application Namespace And Qualified Business Key")
    @SuppressWarnings("unchecked")
    void usesTheApplicationNamespaceAndQualifiedBusinessKey() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        BucketProxy bucket = mock(BucketProxy.class);
        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(
                proxyManager.getProxy(
                        ArgumentMatchers.eq(
                                "SCA4D_TEST:RATE_LIMIT:V_6C6F67696E3A6163636F756E743A3432:5:60000000000"),
                        any(Supplier.class)))
                .thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        RedisRateLimiter rateLimiter = new RedisRateLimiter(
                proxyManager);

        assertTrue(
                rateLimiter.tryAcquire(
                        Key.of(TreeCode.of("RATE_LIMIT"), "login:account:42"),
                        new RateLimit(5, Duration.ofMinutes(1))).isAllowed());
    }

    @Test
    @DisplayName("Rejects An Invalid Business Key Before Accessing Redis")
    @SuppressWarnings("unchecked")
    void rejectsAnInvalidBusinessKeyBeforeAccessingRedis() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        RedisRateLimiter rateLimiter = new RedisRateLimiter(
                proxyManager);

        assertThrows(
                IllegalArgumentException.class,
                () -> rateLimiter.tryAcquire(
                        Key.of(TreeCode.of("RATE_LIMIT:*")),
                        new RateLimit(5, Duration.ofMinutes(1))));
        verifyNoInteractions(proxyManager);
    }
}
