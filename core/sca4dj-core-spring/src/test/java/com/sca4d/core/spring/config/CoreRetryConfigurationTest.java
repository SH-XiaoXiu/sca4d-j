package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.core.api.retry.RetryExecutor;
import com.sca4d.core.spring.retry.SpringRetryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证可选依赖和应用覆盖时同步重试执行器的真实装配结果。 */
class CoreRetryConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CoreRetryConfiguration.class);

    @Test
    void installsWhenLibraryIsPresent() {
        context.run(
                result -> assertThat(result).hasSingleBean(RetryExecutor.class)
                        .getBean(RetryExecutor.class)
                        .isInstanceOf(SpringRetryExecutor.class));
    }

    @Test
    void backsOffWithoutLibrary() {
        context.withClassLoader(new FilteredClassLoader("org.springframework.retry"))
                .run(result -> assertThat(result).hasNotFailed().doesNotHaveBean(RetryExecutor.class));
    }

    @Test
    void usesApplicationExecutor() {
        RetryExecutor replacement = mock(RetryExecutor.class);
        context.withBean(RetryExecutor.class, () -> replacement)
                .run(
                        result -> assertThat(result).hasSingleBean(RetryExecutor.class)
                                .getBean(RetryExecutor.class)
                                .isSameAs(replacement));
    }
}
