package com.sca4d.common.realtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 实时消息发布请求。
 *
 * <p>{@code event} 是由各业务模块以常量定义并随消费者契约扩展的固定事件编码，不是框架内置有限枚举。
 */
public class RealtimePublishRequest {

    private final RealtimeTarget target;
    private final String event;
    private final Object data;
    private final Set<RealtimeTransport> transports;
    private final String userDestination;
    private final Map<String, Object> metadata;

    /**
     * 创建实时消息发布请求。
     *
     * @param target 投递目标
     * @param event 业务模块定义的固定事件编码
     * @param data 业务载荷
     * @param transports 投递通道集合
     * @param userDestination 用户队列地址；为空时按目标 topic 投递
     * @param metadata 发布器与消费者按业务协议共同定义的扩展元数据
     */
    public RealtimePublishRequest(
            @NotNull RealtimeTarget target,
            @NotNull String event,
            @Nullable Object data,
            @NotNull Set<RealtimeTransport> transports,
            @Nullable String userDestination,
            @NotNull Map<String, Object> metadata) {
        this.target = target;
        this.event = event;
        this.data = data;
        this.transports = transports.isEmpty() ? EnumSet.noneOf(RealtimeTransport.class) : EnumSet.copyOf(transports);
        this.userDestination = userDestination;
        this.metadata = Map.copyOf(metadata);
    }

    /**
     * 创建同时投递 WebSocket 与 SSE 的请求。
     *
     * @param target 投递目标
     * @param event 事件名称
     * @param data 业务载荷
     * @return 实时消息发布请求
     */
    public static @NotNull RealtimePublishRequest all(
            @NotNull RealtimeTarget target, @NotNull String event, @Nullable Object data) {
        return of(target, event, data, EnumSet.allOf(RealtimeTransport.class));
    }

    /**
     * 创建指定通道的发布请求。
     *
     * @param target 投递目标
     * @param event 事件名称
     * @param data 业务载荷
     * @param transports 投递通道集合
     * @return 实时消息发布请求
     */
    public static @NotNull RealtimePublishRequest of(
            @NotNull RealtimeTarget target,
            @NotNull String event,
            @Nullable Object data,
            @NotNull Set<RealtimeTransport> transports) {
        return new RealtimePublishRequest(target, event, data, transports, null, Map.of());
    }

    /**
     * 创建 WebSocket 用户队列发布请求。
     *
     * @param target 用户目标
     * @param userDestination 用户队列地址
     * @param event 事件名称
     * @param data 业务载荷
     * @return 实时消息发布请求
     */
    public static @NotNull RealtimePublishRequest websocketUserQueue(
            @NotNull RealtimeTarget target,
            @NotNull String userDestination,
            @NotNull String event,
            @Nullable Object data) {
        return new RealtimePublishRequest(
                target, event, data, EnumSet.of(RealtimeTransport.WEBSOCKET), userDestination, Map.of());
    }

    /**
     * 创建 SSE 发布请求。
     *
     * @param target 投递目标
     * @param event 事件名称
     * @param data 业务载荷
     * @return 实时消息发布请求
     */
    public static @NotNull RealtimePublishRequest sse(
            @NotNull RealtimeTarget target, @NotNull String event, @Nullable Object data) {
        return of(target, event, data, EnumSet.of(RealtimeTransport.SSE));
    }

    public @NotNull RealtimeTarget getTarget() {
        return target;
    }

    public @NotNull String getEvent() {
        return event;
    }

    public @Nullable Object getData() {
        return data;
    }

    public @NotNull Set<RealtimeTransport> getTransports() {
        return EnumSet.copyOf(transports);
    }

    public @Nullable String getUserDestination() {
        return userDestination;
    }

    public @NotNull Map<String, Object> getMetadata() {
        return metadata;
    }
}
