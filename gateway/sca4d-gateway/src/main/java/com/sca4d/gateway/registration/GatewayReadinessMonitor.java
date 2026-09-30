package com.sca4d.gateway.registration;

import com.sca4d.gateway.config.GatewayProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** 定期检查所有未过期 Server 租约的 readiness，并更新路由可用状态。 */
@Component
public class GatewayReadinessMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayReadinessMonitor.class);

    private final @NotNull WebClient client;

    private final @NotNull GatewayInstanceRegistry registry;

    private final @NotNull Duration timeout;

    private final int concurrency;

    /**
     * 创建 readiness 监视器。
     *
     * @param registry 内存实例注册表
     * @param properties 已校验的 Gateway 配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "registry 是注入的共享实例注册协作对象，监视器必须更新同一个注册表")
    @Autowired
    public GatewayReadinessMonitor(
            @NotNull GatewayInstanceRegistry registry,
            @NotNull GatewayProperties properties) {
        this(registry, properties, WebClient.builder().build());
    }

    /** 使用指定 HTTP 客户端检查实例。 */
    GatewayReadinessMonitor(
            @NotNull GatewayInstanceRegistry registry,
            @NotNull GatewayProperties properties,
            @NotNull WebClient client) {
        this.registry = registry;
        this.client = client;
        this.timeout = properties.getHealthTimeout();
        this.concurrency = properties.getHealthConcurrency();
        if (concurrency < 1) {
            throw new IllegalArgumentException("readiness 检查并发数必须为正数");
        }
    }

    /** 检查所有未过期租约；任一实例失败不阻止其他实例更新状态。 */
    @Scheduled(fixedDelayString = "${sca4dj.gateway.health-interval:PT5S}")
    public void checkReadiness() {
        Flux.fromIterable(registry.leasedInstances())
                .flatMap(
                        instance -> Mono.defer(() -> readiness(instance))
                                .onErrorResume(exception -> {
                                    LOGGER.trace(
                                            "Server readiness 检查失败 | instanceId={} failureType={}",
                                            instance.instanceId(),
                                            exception.getClass().getSimpleName());
                                    return Mono.just(false);
                                })
                                .doOnNext(
                                        ready -> registry.readiness(instance.instanceId(), instance.leaseId(), ready)),
                        concurrency)
                .blockLast();
    }

    private @NotNull Mono<Boolean> readiness(@NotNull GatewayInstance instance) {
        String baseUrl = instance.baseUrl().toString();
        String readinessUrl = (baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl)
                + instance.readinessPath();
        return client.get()
                .uri(readinessUrl)
                .exchangeToMono(
                        response -> {
                            boolean successful = response.statusCode().is2xxSuccessful();
                            if (!successful) {
                                LOGGER.trace(
                                        "Server readiness 检查返回非成功状态 | instanceId={} status={}",
                                        instance.instanceId(),
                                        response.statusCode().value());
                            }
                            return Mono.just(successful);
                        })
                .timeout(timeout);
    }

}
