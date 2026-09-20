package com.sca4d.core.spring.redis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.util.function.Supplier;

class RedisRateLimiterTest {

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
                                "example-app:sca4dj:rate-limit:login:account:42:5:60000000000"),
                        any(Supplier.class)))
                .thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        RedisRateLimiter rateLimiter = new RedisRateLimiter(
                proxyManager,
                new RuntimeNamespace("example-app"));

        assertTrue(
                rateLimiter.tryAcquire(
                        "login:account:42",
                        new RateLimit(5, Duration.ofMinutes(1))).isAllowed());
    }

    @Test
    @DisplayName("Rejects An Invalid Business Key Before Accessing Redis")
    @SuppressWarnings("unchecked")
    void rejectsAnInvalidBusinessKeyBeforeAccessingRedis() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        RedisRateLimiter rateLimiter = new RedisRateLimiter(
                proxyManager,
                new RuntimeNamespace("example-app"));

        assertThrows(
                IllegalArgumentException.class,
                () -> rateLimiter.tryAcquire(
                        "login::42",
                        new RateLimit(5, Duration.ofMinutes(1))));
        verifyNoInteractions(proxyManager);
    }
}
