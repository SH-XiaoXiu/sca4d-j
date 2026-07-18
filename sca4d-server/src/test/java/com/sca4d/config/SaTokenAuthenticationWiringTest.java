package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.satoken.SaTokenAuthentication;
import com.sca4d.common.auth.Authentication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证 Sa-Token 认证实现替换默认拒绝实现。 */
class SaTokenAuthenticationWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(AuthConfig.class, SaTokenAuthentication.class);

    @Test
    void shouldUseSaTokenAuthenticationInsteadOfFallback() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Authentication.class);
            assertThat(context.getBean(Authentication.class))
                    .isInstanceOf(SaTokenAuthentication.class);
        });
    }
}
