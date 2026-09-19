package com.sca4d.web.realtime.stomp;

import com.sca4d.web.realtime.config.WebRealtimeProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

/** 在 `BROKER_RELAY` 模式启动完成前确认 RabbitMQ STOMP plugin TCP 端口可连接。 */
@ApiStatus.Internal
public final class StompRelayPortCheck implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(StompRelayPortCheck.class);

    private final @NotNull WebRealtimeProperties properties;

    /** 创建 relay 启动可用性检查。 */
    public StompRelayPortCheck(@NotNull WebRealtimeProperties properties) {
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
    }

    /** relay 模式下无法建立 TCP 连接时直接阻止 Spring 应用启动。 */
    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getStomp().getMode() != StompBrokerMode.BROKER_RELAY) {
            return;
        }
        WebRealtimeProperties.Relay relay = properties.getStomp().getRelay();
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(relay.getHost(), relay.getPort()),
                    (int) relay.getStartupConnectTimeout().toMillis());
            LOG.info(
                    "RabbitMQ STOMP relay TCP 端口可连接 | host={} port={}",
                    relay.getHost(),
                    relay.getPort());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "BROKER_RELAY 模式无法连接 RabbitMQ STOMP relay: "
                            + relay.getHost() + ':' + relay.getPort(),
                    exception);
        }
    }
}
