package com.sca4d.auth.apple.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.auth.apple.AppleProviderDriver;
import com.sca4d.auth.federation.FederatedProviderDriver;
import com.sca4d.auth.federation.FederatedProviderDriverRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.util.List;

/** 验证 Apple Driver 的登记条件。 */
class AppleAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(Clock.class, Clock::systemUTC)
            .withConfiguration(AutoConfigurations.of(AppleAutoConfiguration.class));

    @Test
    @DisplayName("默认登记 Apple Driver，Driver 目录能按 APPLE 编码找到它")
    void registersDriverByDefault() {
        runner.run(context -> {
            List<FederatedProviderDriver> drivers = List
                    .copyOf(context.getBeansOfType(FederatedProviderDriver.class).values());
            assertThat(drivers).singleElement().isInstanceOf(AppleProviderDriver.class);
            assertThat(new FederatedProviderDriverRegistry(drivers).contains(AppleProviderDriver.CODE)).isTrue();
        });
    }

    @Test
    @DisplayName("sca4dj.auth.apple.enabled 为 false 时不登记 Driver")
    void skipsDriverWhenDisabled() {
        runner.withPropertyValues("sca4dj.auth.apple.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FederatedProviderDriver.class));
    }
}
