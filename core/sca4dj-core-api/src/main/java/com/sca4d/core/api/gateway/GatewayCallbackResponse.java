package com.sca4d.core.api.gateway;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 保存回调返回的原始 HTTP 状态、大小写不敏感 Header 和可选 JSON 响应体。
 *
 * @param <O> JSON 响应体类型
 */
public final class GatewayCallbackResponse<O> {

    private final int status;

    private final @NotNull Map<String, List<String>> headers;

    private final @Nullable O body;

    /**
     * 创建回调响应。
     *
     * @param status 100 至 599 的 HTTP 状态码
     * @param headers HTTP 响应 Header；名称按大小写不敏感方式读取，值会复制为不可修改列表
     * @param body 可选 JSON 响应体；无响应体时为 null
     */
    public GatewayCallbackResponse(
            int status,
            @NotNull Map<@NotNull String, @NotNull List<@NotNull String>> headers,
            @Nullable O body) {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Gateway 回调 HTTP 状态必须在 100 至 599 之间");
        }
        this.status = status;
        this.headers = immutableHeaders(headers);
        this.body = body;
    }

    /**
     * 创建没有额外 Header 的回调响应。
     *
     * @param status HTTP 状态码
     * @param body 可选 JSON 响应体
     */
    public GatewayCallbackResponse(int status, @Nullable O body) {
        this(status, Map.of(), body);
    }

    /** @return 100 至 599 的 HTTP 状态码 */
    @Contract(pure = true)
    public int status() {
        return status;
    }

    /** @return 大小写不敏感且不可修改的全部响应 Header */
    @Contract(pure = true)
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "headers 是构造时创建的深度不可变快照，Map 和其中的 List 均不可修改")
    public @NotNull @Unmodifiable Map<String, List<String>> headers() {
        return headers;
    }

    /** @return 可选 JSON 响应体 */
    @Contract(pure = true)
    public @NotNull Optional<O> body() {
        return Optional.ofNullable(body);
    }

    /** 复制 Header 名称和值，避免回调返回后继续修改响应。 */
    private static @NotNull Map<String, List<String>> immutableHeaders(
            @NotNull Map<@NotNull String, @NotNull List<@NotNull String>> source) {
        TreeMap<String, List<String>> copied = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        source.forEach((name, values) -> {
            ArrayList<String> copiedValues = new ArrayList<>(values.size());
            for (String value : values) {
                copiedValues.add(java.util.Objects.requireNonNull(value, "Gateway 回调 Header 值不能为空"));
            }
            copied.put(name, List.copyOf(copiedValues));
        });
        return Collections.unmodifiableMap(copied);
    }
}
