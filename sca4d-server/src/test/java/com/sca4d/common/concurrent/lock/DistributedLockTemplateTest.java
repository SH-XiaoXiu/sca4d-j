package com.sca4d.common.concurrent.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.config.properties.Sca4djConcurrentProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 验证锁模板传递等待时间和租约，并在释放锁后传播执行结果。 */
class DistributedLockTemplateTest {

    @Test
    void shouldDelegateToExecutorWithDefaultDurations() throws Throwable {
        FakeExecutor executor = new FakeExecutor();
        Sca4djConcurrentProperties properties = new Sca4djConcurrentProperties();
        properties.getLock().setWaitTime(Duration.ofSeconds(2));
        properties.getLock().setLeaseTime(Duration.ofSeconds(9));
        DistributedLockTemplate template = new DistributedLockTemplate(executor, properties);

        String result = template.execute("lock:test", () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(executor.key).isEqualTo("lock:test");
        assertThat(executor.waitTime).isEqualTo(Duration.ofSeconds(2));
        assertThat(executor.leaseTime).isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void shouldRejectInvalidLockConfiguration() {
        Sca4djConcurrentProperties.Lock configuration = new Sca4djConcurrentProperties.Lock();

        assertThatThrownBy(() -> configuration.setKeyPrefix(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分布式锁 key 前缀不能为空");
        assertThatThrownBy(() -> configuration.setWaitTime(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分布式锁等待时间不能为负数");
        assertThatThrownBy(() -> configuration.setLeaseTime(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分布式锁租约时间不能为负数");
    }

    /** 记录模板传入锁参数并在当前线程执行操作的测试执行器。 */
    private static class FakeExecutor implements DistributedLockExecutor {

        private String key;
        private Duration waitTime;
        private Duration leaseTime;

        @Override
        public <T> T execute(
                @NotNull String key,
                @NotNull Duration waitTime,
                @NotNull Duration leaseTime,
                @NotNull LockedOperation<T> operation)
                throws Throwable {
            this.key = key;
            this.waitTime = waitTime;
            this.leaseTime = leaseTime;
            return operation.execute();
        }
    }
}
