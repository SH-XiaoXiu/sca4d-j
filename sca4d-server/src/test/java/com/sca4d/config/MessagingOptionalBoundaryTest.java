package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.messaging.outbox.MessageOutboxRepository;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 验证关闭消息服务时不注册 RabbitMQ 和 Outbox 发送组件。 */
class MessagingOptionalBoundaryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxRepositoryConfiguration.class);

    @Test
    void shouldNotCreateOutboxRepositoryWhenMessagingIsDisabled() {
        contextRunner.withPropertyValues("sca4dj.messaging.enabled=false").run(context -> assertThat(context).doesNotHaveBean(MessageOutboxRepository.class));
    }

    @Test
    void shouldKeepOptionalCapabilityPropertiesAvailableToScheduler() {
        new ApplicationContextRunner()
                .withUserConfiguration(SchedulerConfig.class)
                .withPropertyValues("sca4dj.messaging.enabled=false", "sca4dj.file.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(Sca4djMessagingProperties.class);
                    assertThat(context).hasSingleBean(Sca4djFileProperties.class);
                });
    }

    /** 只导入 OutboxRepository，用于验证消息开关的条件装配。 */
    @Configuration(proxyBeanMethods = false)
    @Import(MessageOutboxRepository.class)
    static class OutboxRepositoryConfiguration {
    }
}
