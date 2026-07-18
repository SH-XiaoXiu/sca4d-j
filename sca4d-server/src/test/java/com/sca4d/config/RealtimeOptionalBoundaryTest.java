package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.controllers.RealtimeController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 验证关闭实时通信时不注册 SSE、STOMP 和 RabbitMQ 监听组件。 */
class RealtimeOptionalBoundaryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RealtimeControllerConfiguration.class);

    @Test
    void shouldNotCreateRealtimeControllerWhenRealtimeIsDisabled() {
        contextRunner.withPropertyValues("sca4dj.realtime.enabled=false").run(context -> assertThat(context).doesNotHaveBean(RealtimeController.class));
    }

    /** 只导入 RealtimeController，用于验证实时通信开关的条件装配。 */
    @Configuration(proxyBeanMethods = false)
    @Import(RealtimeController.class)
    static class RealtimeControllerConfiguration {
    }
}
