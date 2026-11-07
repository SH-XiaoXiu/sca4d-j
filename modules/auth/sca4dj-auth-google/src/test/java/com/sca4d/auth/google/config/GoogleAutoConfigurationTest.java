package com.sca4d.auth.google.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.auth.federation.FederatedProviderDriver;
import com.sca4d.auth.federation.FederatedProviderDriverRegistry;
import com.sca4d.auth.google.GoogleProviderDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

/** 验证 Google Driver 的登记条件。 */
class GoogleAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GoogleAutoConfiguration.class));

    @Test
    @DisplayName("默认登记 Google Driver，Driver 目录能按 GOOGLE 编码找到它")
    void registersDriverByDefault() {
        runner.run(context -> {
            List<FederatedProviderDriver> drivers = List
                    .copyOf(context.getBeansOfType(FederatedProviderDriver.class).values());
            assertThat(drivers).singleElement().isInstanceOf(GoogleProviderDriver.class);
            assertThat(new FederatedProviderDriverRegistry(drivers).contains(GoogleProviderDriver.CODE)).isTrue();
        });
    }

    @Test
    @DisplayName("sca4dj.auth.google.enabled 为 false 时不登记 Driver")
    void skipsDriverWhenDisabled() {
        runner.withPropertyValues("sca4dj.auth.google.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FederatedProviderDriver.class));
    }
}
