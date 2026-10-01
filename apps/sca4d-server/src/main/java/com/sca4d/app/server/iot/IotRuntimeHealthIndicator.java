package com.sca4d.app.server.iot;

import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.emqx.transport.EmqxMqttRuntime;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 把启用 EMQX 的 IoT 实例 MQTT 运行状态加入 Actuator readiness。 */
@Component("iotRuntimeHealthIndicator")
@ConditionalOnCapability(IotCapabilities.TRANSPORT)
@ConditionalOnProperty(prefix = "sca4dj.iot.emqx", name = "enabled", havingValue = "true")
public class IotRuntimeHealthIndicator implements HealthIndicator {

    private final @NotNull EmqxMqttRuntime runtime;

    /** 创建 MQTT 运行状态检查。 */
    public IotRuntimeHealthIndicator(@NotNull EmqxMqttRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "EMQX MQTT Runtime 不能为空");
    }

    /** @return MQTT 客户端已由 Spring 启动时为 UP，否则为 DOWN */
    @Override
    public @NotNull Health health() {
        return runtime.isRunning()
                ? Health.up().withDetail("mqtt", "connected").build()
                : Health.down().withDetail("mqtt", "disconnected").build();
    }
}
