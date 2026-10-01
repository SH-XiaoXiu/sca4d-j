package com.sca4d.core.spring.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.gateway.GatewayCallback;
import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayCallbackRequest;
import com.sca4d.core.api.gateway.GatewayCallbackResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 把 `/callbacks/{code}` 的 JSON 请求转换为应用声明的 `GatewayCallback` 调用。 */
@RestController
@AnonymousAccess
@Hidden
@RequestMapping("${sca4dj.gateway.protocol.callback-path}")
public class GatewayCallbackDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayCallbackDispatcher.class);

    private final @NotNull Map<GatewayCallbackCode, GatewayCallback<?, ?>> callbacks;

    private final @NotNull ObjectMapper objectMapper;

    /**
     * 创建回调分发器并拒绝重复编码。
     *
     * @param callbacks 应用声明的全部回调 Bean
     * @param objectMapper 当前 HTTP JSON 映射器
     * @throws IllegalStateException 两个回调使用同一编码时抛出
     */
    public GatewayCallbackDispatcher(
            @NotNull List<@NotNull GatewayCallback<?, ?>> callbacks,
            @NotNull ObjectMapper objectMapper) {
        LinkedHashMap<GatewayCallbackCode, GatewayCallback<?, ?>> indexed = new LinkedHashMap<>();
        for (GatewayCallback<?, ?> callback : callbacks) {
            GatewayCallback<?, ?> checked = Objects.requireNonNull(callback, "Gateway 回调不能为空");
            GatewayCallbackCode code = checked.code();
            if (indexed.putIfAbsent(code, checked) != null) {
                throw new IllegalStateException("Gateway 回调编码重复: " + code);
            }
        }
        this.callbacks = Map.copyOf(indexed);
        this.objectMapper = Objects.requireNonNull(objectMapper, "HTTP ObjectMapper 不能为空");
    }

    /**
     * 按编码执行回调，并原样采用回调给出的 HTTP 状态、Header 和 JSON body。
     *
     * @param code 路径中的回调编码
     * @param headers HTTP 请求 Header
     * @param body 可选 JSON 请求体
     * @return 回调给出的原始 HTTP 结果；编码不存在时返回 404
     */
    @PostMapping("/{code}")
    @CheckReturnValue
    public @NotNull ResponseEntity<Object> dispatch(
            @PathVariable @NotNull String code,
            @RequestHeader @NotNull HttpHeaders headers,
            @RequestBody(required = false) @Nullable JsonNode body) {
        GatewayCallback<?, ?> callback = callbacks.get(new GatewayCallbackCode(code));
        if (callback == null) {
            return ResponseEntity.notFound().build();
        }
        LOGGER.trace("Gateway 回调已匹配 | callbackCode={}", code);
        return dispatch(callback, headers, body);
    }

    /** 转换精确请求类型后调用泛型回调，避免 Controller 对应用输入类型作假设。 */
    private <I, O> @NotNull ResponseEntity<Object> dispatch(
            @NotNull GatewayCallback<I, O> callback,
            @NotNull HttpHeaders headers,
            @Nullable JsonNode body) {
        I requestBody = body == null || body.isNull()
                ? null
                : objectMapper.convertValue(body, callback.requestType());
        GatewayCallbackRequest<I> request = new GatewayCallbackRequest<>(headers, requestBody);
        GatewayCallbackResponse<O> response = callback.respond(request);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(response.headers());
        return response.body()
                .<ResponseEntity<Object>>map(
                        value -> ResponseEntity.status(response.status())
                                .headers(responseHeaders)
                                .body(value))
                .orElseGet(
                        () -> ResponseEntity.status(response.status())
                                .headers(responseHeaders)
                                .build());
    }
}
