package com.sca4d.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证全局敏感标识脱敏规则不会泄露完整原值。 */
class SensitiveUtilsTest {

    @Test
    void shouldMaskIdentifiersByLength() {
        assertThat(SensitiveUtils.identifier(null)).isEqualTo("***");
        assertThat(SensitiveUtils.identifier("a")).isEqualTo("*");
        assertThat(SensitiveUtils.identifier("ab")).isEqualTo("a*");
        assertThat(SensitiveUtils.identifier(" administrator ")).isEqualTo("a***r");
    }

    @Test
    void shouldMaskEmailLocalPartAndRejectInvalidValues() {
        assertThat(SensitiveUtils.email(null)).isEqualTo("***");
        assertThat(SensitiveUtils.email("invalid")).isEqualTo("***");
        assertThat(SensitiveUtils.email("a@example.com")).isEqualTo("a***@example.com");
        assertThat(SensitiveUtils.email(" alice@example.com ")).isEqualTo("al***@example.com");
    }
}
