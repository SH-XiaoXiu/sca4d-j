package com.sca4d.audit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.audit.recording.AfterCommitAuditRecorder;
import com.sca4d.audit.recording.DatabaseAuditRecorder;
import com.sca4d.audit.recording.QueuedAuditRecorder;
import com.sca4d.audit.recording.mapper.AuditMapper;
import com.sca4d.core.api.audit.AuditCallInterceptor;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 验证审计方式装配，队列模式缺少可靠消息入口时启动失败。 */
class AuditModeConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class))
            .withUserConfiguration(Inputs.class)
            .withBean("auditMapper", AuditMapper.class, () -> mock(AuditMapper.class));

    @Test
    void defaultsToDirectAndRequiresMessageBusForQueuedMode() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(AopUtils.getTargetClass(application.getBean("databaseAuditRecorder", AuditRecorder.class)))
                    .isEqualTo(DatabaseAuditRecorder.class);
            assertThat(AopUtils.getTargetClass(application.getBean("afterCommitAuditRecorder", AuditRecorder.class)))
                    .isEqualTo(AfterCommitAuditRecorder.class);
        });
        context.withPropertyValues("sca4dj.audit.mode=QUEUED").run(application -> {
            assertThat(application).hasFailed();
            assertThat(application.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void allowsBestEffortAfterCommitWithoutMessaging() {
        context.withPropertyValues("sca4dj.audit.mode=AFTER_COMMIT").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).hasSingleBean(AuditCallInterceptor.class);
            assertThat(application).doesNotHaveBean("queuedAuditRecorder");
        });
    }

    @Test
    void registersQueuedRecorderWhenReliableMessagingIsInstalled() {
        context.withPropertyValues("sca4dj.audit.mode=QUEUED")
                .withBean(MessageBus.class, () -> mock(MessageBus.class))
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).hasSingleBean(AuditCallInterceptor.class);
                    assertThat(AopUtils.getTargetClass(application.getBean("queuedAuditRecorder", AuditRecorder.class)))
                            .isEqualTo(QueuedAuditRecorder.class);
                    assertThat(application).hasSingleBean(MessageHandler.class);
                });
    }

    @Test
    void installsQueuedDeliveryEvenWhenTheDefaultIsDirect() {
        context.withBean(MessageBus.class, () -> mock(MessageBus.class)).run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).hasSingleBean(MessageHandler.class);
            assertThat(application).hasBean("queuedAuditRecorder");
        });
    }

    @Test
    void rejectsDuplicateCustomModesAndUnresolvedConfigurationDefaults() {
        context.withBean("firstCustom", AuditRecorder.class, () -> record -> {
        })
                .withBean("secondCustom", AuditRecorder.class, () -> record -> {
                })
                .run(application -> {
                    assertThat(application).hasFailed();
                    assertThat(application.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
        context.withPropertyValues("sca4dj.audit.mode=DEFAULT").run(application -> {
            assertThat(application).hasFailed();
            assertThat(application.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    /** 明确提供装配所需基础对象，不引入数据库或 RabbitMQ 连接。 */
    @Configuration(proxyBeanMethods = false)
    static class Inputs {
        @Bean
        SPICallValueResolver expressionResolver() {
            return (expression, execution) -> null;
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        @Sca4djJson
        Sca4djMapper json() {
            return new Sca4djMapper();
        }

        @Bean
        IdGenerator ids() {
            return () -> Id.of(1);
        }

        @Bean
        InstanceProperties instance() {
            return new InstanceProperties();
        }
    }
}
