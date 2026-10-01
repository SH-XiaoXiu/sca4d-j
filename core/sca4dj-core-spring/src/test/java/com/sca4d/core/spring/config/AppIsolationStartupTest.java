package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证 App 隔离名称来自启动配置，非法或改变全局名称的上下文拒绝启动。 */
class AppIsolationStartupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AppIsolationAutoConfiguration.class));

    @Test
    void normalizesTheAppNameWithoutChangingTheOriginalProperty() {
        runner.withPropertyValues("spring.application.name=sca4d-test").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("sca4djAppName", Name.class).format(NameFormat.COLON)).isEqualTo("SCA4D_TEST");
            assertThat(context.getEnvironment().getProperty("spring.application.name")).isEqualTo("sca4d-test");
        });
    }

    @Test
    void rejectsMissingOrInvalidAppNames() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.application.name=INVALID NAME")
                .run(context -> assertThat(context).hasFailed());
    }
}
