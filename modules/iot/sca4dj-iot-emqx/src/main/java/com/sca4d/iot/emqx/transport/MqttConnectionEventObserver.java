package com.sca4d.iot.emqx.transport;

import org.jetbrains.annotations.NotNull;

/** 观察应用 MQTT Runtime 从 EMQX `$SYS` 共享订阅实际收到的 Client 连接和断开事件。 */
@FunctionalInterface
public interface MqttConnectionEventObserver {

    /**
     * 在连接投影刷新前同步观察事件，不要求对应 Client 已登记为 Device。
     *
     * <p>Runtime 按构造时的集合顺序在 HiveMQ 回调线程上通知，Spring 自动配置按 Bean 顺序注入。
     * 实现应快速返回，慢回调会延迟后续观察器、投影刷新和确认。单个观察器抛出 RuntimeException 时，
     * Runtime 记录警告后继续通知和刷新，并保留既有确认语义；Error 不在隔离范围内。
     * 回调时不保证连接投影已经更新，事件也不是 Broker 全量、可靠的连接历史。
     *
     * @param event 当前应用共享订阅组收到并成功解析 Client ID 的事件
     */
    void connectionChanged(@NotNull MqttConnectionEvent event);
}
