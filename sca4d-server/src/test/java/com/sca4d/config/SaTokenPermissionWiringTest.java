package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.permission.PermissionChecker;
import com.sca4d.permission.satoken.SaTokenPermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证 Sa-Token 权限检查实现替换默认拒绝实现。 */
class SaTokenPermissionWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(AuthConfig.class, SaTokenPermissionChecker.class);

    @Test
    void shouldUseSaTokenPermissionCheckerInsteadOfFallback() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PermissionChecker.class);
            assertThat(context.getBean(PermissionChecker.class))
                    .isInstanceOf(SaTokenPermissionChecker.class);
        });
    }
}
