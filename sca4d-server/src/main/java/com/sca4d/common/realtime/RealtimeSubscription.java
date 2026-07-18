package com.sca4d.common.realtime;

import org.jetbrains.annotations.NotNull;

/**
 * 当前连接可订阅的低层实时目标。
 */
public class RealtimeSubscription {

    private final RealtimeTargetType type;
    private final String value;
    private final String routingKey;
    private final String stompDestination;

    /**
     * 创建实时订阅目标。
     *
     * @param target 实时目标
     */
    public RealtimeSubscription(@NotNull RealtimeTarget target) {
        this.type = target.getType();
        this.value = target.getValue();
        this.routingKey = target.routingKey();
        this.stompDestination = target.stompDestination();
    }

    public @NotNull RealtimeTargetType getType() {
        return type;
    }

    public @NotNull String getValue() {
        return value;
    }

    public @NotNull String getRoutingKey() {
        return routingKey;
    }

    public @NotNull String getStompDestination() {
        return stompDestination;
    }
}
