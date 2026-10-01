package com.sca4d.gateway.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证 readiness 有界并发和失败隔离，不连接真实服务。 */
class GatewayReadinessMonitorTest {
    @Test
    void failingProbeDoesNotCancelOtherLeasesAndConcurrencyIsBounded() throws Exception {
        GatewayProperties properties = new GatewayProperties();
        properties.setHealthConcurrency(2);
        properties.setHealthTimeout(java.time.Duration.ofSeconds(30));
        GatewayInstanceRegistry registry = mock(GatewayInstanceRegistry.class);
        when(registry.leasedInstances()).thenReturn(List.of(instance("one"), instance("two"), instance("three")));
        LinkedBlockingQueue<MonoSink<ClientResponse>> pending = new LinkedBlockingQueue<>();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.<ClientResponse>create(sink -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            pending.add(sink);
        }).doOnEach(signal -> {
            if (signal.isOnNext() || signal.isOnError())
                active.decrementAndGet();
        })).build();
        var monitor = new GatewayReadinessMonitor(registry, properties, client);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var task = executor.submit(monitor::checkReadiness);
            var first = pending.poll(5, TimeUnit.SECONDS);
            var second = pending.poll(5, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            first.error(new IllegalStateException("unavailable"));
            var third = pending.poll(5, TimeUnit.SECONDS);
            assertThat(third).isNotNull();
            second.success(ClientResponse.create(HttpStatus.OK).build());
            third.success(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
            task.get(5, TimeUnit.SECONDS);
        }
        assertThat(peak.get()).isEqualTo(2);
        verify(registry).readiness("one", "lease-one", false);
        verify(registry).readiness("two", "lease-two", true);
        verify(registry).readiness("three", "lease-three", false);
    }

    private static GatewayInstance instance(String id) {
        return new GatewayInstance(
                id,
                "lease-" + id,
                URI.create("http://" + id + ".invalid"),
                "/ready",
                Set.of(),
                Set.of(),
                Map.of(),
                Instant.MAX);
    }
}
