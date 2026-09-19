package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RuntimeNamespaceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuntimeNamespaceAutoConfiguration.class));

    @Test
    @DisplayName("Uses Spring Application Name As The Runtime Application ID")
    void usesSpringApplicationNameAsTheRuntimeApplicationId() {
        contextRunner
                .withPropertyValues("spring.application.name=example-app")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RuntimeNamespace.class);
                    assertThat(context.getBean(RuntimeNamespace.class).applicationId())
                            .isEqualTo("example-app");
                });
    }

    @Test
    @DisplayName("Fails When Spring Application Name Is Missing")
    void failsWhenSpringApplicationNameIsMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }
}
