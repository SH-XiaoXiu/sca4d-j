package com.sca4d.common.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证启动环境变量的读取、缺失处理和敏感值隐藏。 */
class BootEnvironmentTest {

    @Test
    void shouldResolveSingleEnvironmentProfile() {
        assertThat(BootEnvironment.resolve("feature-x", "staging")).isEqualTo(BootEnvironment.STAGING);
        assertThat(BootEnvironment.STAGING.isProductionLike()).isTrue();
        assertThat(BootEnvironment.DEV.isProductionLike()).isFalse();
    }

    @Test
    void shouldRejectMissingOrConflictingEnvironmentProfiles() {
        assertThatThrownBy(() -> BootEnvironment.resolve("feature-x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须且只能激活一个环境 Profile");
        assertThatThrownBy(() -> BootEnvironment.resolve("local", "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须且只能激活一个环境 Profile");
    }
}
