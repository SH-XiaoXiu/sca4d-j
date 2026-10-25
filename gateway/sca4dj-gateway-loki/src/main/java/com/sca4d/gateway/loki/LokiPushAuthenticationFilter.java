package com.sca4d.gateway.loki;

import com.sca4d.core.api.gateway.GatewayConstants;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 验证 Alloy 的独立 Basic Auth 凭据，并在转发到 Loki 前删除认证 Header。 */
public class LokiPushAuthenticationFilter implements GatewayFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LokiPushAuthenticationFilter.class);

    private final byte @NotNull [] expectedCredential;

    /** @param properties 已校验并启用的 Loki 写入配置 */
    public LokiPushAuthenticationFilter(@NotNull LokiGatewayProperties properties) {
        this.expectedCredential = (properties.validatedUsername() + ":" + properties.validatedPassword())
                .getBytes(StandardCharsets.UTF_8);
    }

    /** @return 认证成功时继续转发且不携带凭据，认证失败时返回 401 */
    @Override
    public @NotNull Mono<Void> filter(
            @NotNull ServerWebExchange exchange,
            @NotNull GatewayFilterChain chain) {
        List<String> authorization = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.size() != 1 || !matches(authorization.getFirst())) {
            LOGGER.warn(
                    "Loki 日志写入认证失败，当前请求已拒绝，使用正确凭据后可重试 | path={}",
                    exchange.getRequest().getPath().value());
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Loki push\"");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(
                exchange.mutate()
                        .request(exchange.getRequest().mutate().headers(headers -> {
                            headers.remove(HttpHeaders.AUTHORIZATION);
                            for (String name : new ArrayList<>(headers.keySet())) {
                                if (name.regionMatches(
                                        true,
                                        0,
                                        GatewayConstants.HEADER_PREFIX,
                                        0,
                                        GatewayConstants.HEADER_PREFIX.length())) {
                                    headers.remove(name);
                                }
                            }
                        }).build())
                        .build());
    }

    /** 对解码后的完整 `username:password` 字节执行固定时间比较。 */
    private boolean matches(@NotNull String authorization) {
        int separator = authorization.indexOf(' ');
        if (separator <= 0
                || !"Basic".equalsIgnoreCase(authorization.substring(0, separator))
                || authorization.substring(separator + 1).isBlank()) {
            return false;
        }
        try {
            byte[] actual = Base64.getDecoder().decode(authorization.substring(separator + 1));
            return MessageDigest.isEqual(expectedCredential, actual);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }
}
