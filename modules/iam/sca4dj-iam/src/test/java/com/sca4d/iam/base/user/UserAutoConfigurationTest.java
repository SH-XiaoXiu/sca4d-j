package com.sca4d.iam.base.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UserAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UserAutoConfiguration.class));

    @Test
    @DisplayName("Installs Current User Resolver Without A User Service")
    void installsCurrentUserResolverWithoutAUserService() {
        contextRunner.run(
                context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(CurrentUserArgumentResolver.class));
    }

    @Test
    @DisplayName("Keeps Application Provided Current User Resolver")
    void keepsApplicationProvidedCurrentUserResolver() {
        CurrentUserArgumentResolver custom = new CurrentUserArgumentResolver();
        contextRunner
                .withBean(CurrentUserArgumentResolver.class, () -> custom)
                .run(context -> assertThat(context.getBean(CurrentUserArgumentResolver.class)).isSameAs(custom));
    }
}
