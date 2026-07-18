package com.sca4d.gateway.discovery;

import io.netty.channel.ChannelOption;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;

/**
 * upstream 实例主动健康检查器。
 */
@Component
public class GatewayUpstreamHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(GatewayUpstreamHealthMonitor.class);

    private final GatewayUpstreamRegistry registry;

    private final GatewayUpstreamHealthState healthState;

    /**
     * 创建主动健康检查器。
     *
     * @param registry 提供当前生效 upstream 快照的注册表
     * @param healthState 保存连续检查结果和可路由状态的健康状态表
     */
    public GatewayUpstreamHealthMonitor(
            @NotNull GatewayUpstreamRegistry registry, @NotNull GatewayUpstreamHealthState healthState) {
        this.registry = registry;
        this.healthState = healthState;
    }

    /**
     * 周期性检查所有启用实例。
     */
    @Scheduled(fixedDelayString = "#{@sca4djGatewayProperties.healthIntervalMillis}")
    public void checkHealth() {
        long startedAt = System.nanoTime();
        GatewayUpstreamSnapshot snapshot = registry.snapshot();
        List<Mono<Void>> checks = new ArrayList<>();
        snapshot.getUpstreams().values().forEach(upstream -> upstream.getInstances()
                .forEach(instance -> checks.add(check(snapshot, upstream, instance))));
        // fixed-delay 从整轮检查完成后开始计时；等待所有带响应超时的请求结束，避免慢实例产生重叠轮次和乱序结果。
        Flux.merge(checks).blockLast();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.trace(
                "网关上游健康检查完成 | instanceCount={} durationMs={} version={}",
                checks.size(),
                durationMs,
                snapshot.getVersion());
    }

    /**
     * 对一个实例执行异步健康检查并更新共享健康状态。
     *
     * <p>已停用实例不发起网络调用，直接从健康候选集中摘除；网络异常统一视为失败。
     *
     * @param snapshot 发起检查时生效的配置快照
     * @param upstream 实例所属的服务定义
     * @param instance 待检查的实例
     */
    private @NotNull Mono<Void> check(
            @NotNull GatewayUpstreamSnapshot snapshot,
            @NotNull GatewayUpstream upstream,
            @NotNull GatewayInstance instance) {
        if (!instance.isEnabled()) {
            healthState.disable(upstream.healthKey(instance));
            return Mono.empty();
        }
        HttpClient httpClient = HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(upstream.getConnectTimeout().toMillis()))
                .responseTimeout(upstream.getResponseTimeout());
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        return webClient
                .get()
                .uri(instance.getUri().resolve(upstream.getHealthPath()))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().is2xxSuccessful()))
                .onErrorReturn(false)
                .doOnNext(success -> {
                    if (registry.snapshot() != snapshot) {
                        return;
                    }
                    GatewayUpstreamHealthState.HealthChange change = healthState.record(
                            upstream.healthKey(instance),
                            success,
                            upstream.getHealthyThreshold(),
                            upstream.getUnhealthyThreshold());
                    if (change == GatewayUpstreamHealthState.HealthChange.BECAME_HEALTHY) {
                        log.info("网关上游实例恢复 | serviceId={} instanceId={}",
                                upstream.getServiceId(),
                                instance.getId());
                    }
                    if (change == GatewayUpstreamHealthState.HealthChange.BECAME_UNHEALTHY) {
                        log.warn("网关上游实例不可用，已停止转发新请求 | serviceId={} instanceId={}",
                                upstream.getServiceId(),
                                instance.getId());
                    }
                })
                .then();
    }
}
