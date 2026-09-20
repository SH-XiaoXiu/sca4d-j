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
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 保存回调调用方提交的大小写不敏感 Header 和已经转换类型的 JSON 请求体。
 *
 * @param <I> JSON 请求体转换后的输入类型
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class GatewayCallbackRequest<I> {

    private final @NotNull Map<String, List<String>> headers;

    private final @Nullable I body;

    /**
     * 创建回调请求。
     *
     * @param headers HTTP 请求 Header；名称按大小写不敏感方式读取，值会复制为不可修改列表
     * @param body 已经按 GatewayCallback.requestType() 转换的请求体；没有请求体时为 null
     */
    public GatewayCallbackRequest(
            @NotNull Map<@NotNull String, @NotNull List<@NotNull String>> headers,
            @Nullable I body) {
        this.headers = immutableHeaders(headers);
        this.body = body;
    }

    /** @return 大小写不敏感且不可修改的全部请求 Header */
    @Contract(pure = true)
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "headers 是构造时创建的深度不可变快照，Map 和其中的 List 均不可修改")
    public @NotNull @Unmodifiable Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * 读取指定 Header 的第一个值。
     *
     * @param name Header 名称
     * @return 第一个值；Header 不存在或没有值时为空
     */
    @Contract(pure = true)
    public @NotNull Optional<String> header(@NotNull String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    /** @return 已转换类型的请求体；HTTP 请求没有 body 时为 null */
    @Contract(pure = true)
    public @Nullable I body() {
        return body;
    }

    /** 复制 Header 名称和值，避免调用方在回调执行期间修改请求。 */
    private static @NotNull Map<String, List<String>> immutableHeaders(
            @NotNull Map<@NotNull String, @NotNull List<@NotNull String>> source) {
        TreeMap<String, List<String>> copied = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        source.forEach((name, values) -> {
            ArrayList<String> copiedValues = new ArrayList<>(values.size());
            for (String value : values) {
                copiedValues.add(Objects.requireNonNull(value, "Gateway 回调 Header 值不能为空"));
            }
            copied.put(name, List.copyOf(copiedValues));
        });
        return Collections.unmodifiableMap(copied);
    }
}
