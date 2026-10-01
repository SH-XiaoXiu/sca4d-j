package com.sca4d.web.realtime.config;

import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.web.realtime.stomp.WebRealtimeProtocol;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/** 在 WebSocket 与 STOMP Bean 创建前固定客户端协议路径。 */
public class WebRealtimeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment,
            @NotNull SpringApplication application) {
        Map<String, String> values = ConfiguredConstants
                .initialize(WebRealtimeProtocol.class, environment::getProperty);
        WebRealtimeProtocol.validate();
        environment.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "sca4djConfiguredWebRealtime",
                                new HashMap<String, Object>(values)));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
