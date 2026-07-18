package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.audit.AuditMode;
import com.sca4d.config.properties.Sca4djAuditProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

/** 验证审计记录器、审计切面和缺省日志实现的 Spring 装配。 */
class AuditConfigTest {

    @Test
    void shouldExposeAsyncAuditDefaults() {
        Sca4djAuditProperties properties = new Sca4djAuditProperties();

        assertThat(properties.getMode()).isEqualTo(AuditMode.ASYNC);
        assertThat(properties.getAsync().getWorkerCount()).isEqualTo(2);
        assertThat(properties.getAsync().getQueueCapacity()).isEqualTo(1024);
        assertThat(properties.getAsync().getShutdownAwait()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldCreateBoundedAuditExecutor() {
        ThreadPoolTaskExecutor executor = new AuditConfig().auditExecutor(new Sca4djAuditProperties());
        executor.initialize();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("sca4dj-audit-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(1024);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shouldRejectNonPositiveAsyncCapacity() {
        Sca4djAuditProperties properties = new Sca4djAuditProperties();
        properties.getAsync().setQueueCapacity(0);

        assertThatThrownBy(() -> new AuditConfig().auditExecutor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审计异步队列容量必须大于 0");
    }

    @Test
    void shouldRejectNonPositiveWorkerCount() {
        Sca4djAuditProperties properties = new Sca4djAuditProperties();
        properties.getAsync().setWorkerCount(0);

        assertThatThrownBy(() -> new AuditConfig().auditExecutor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审计异步工作线程数必须大于 0");
    }

    @Test
    void shouldRejectNegativeShutdownAwait() {
        Sca4djAuditProperties properties = new Sca4djAuditProperties();
        properties.getAsync().setShutdownAwait(Duration.ofSeconds(-1));

        assertThatThrownBy(() -> new AuditConfig().auditExecutor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审计关闭等待时间不能为负数");
    }
}
