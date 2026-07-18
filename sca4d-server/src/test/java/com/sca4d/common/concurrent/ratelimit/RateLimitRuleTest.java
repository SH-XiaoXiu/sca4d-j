package com.sca4d.common.concurrent.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 验证限流规则的容量、补充速率和持续时间校验。 */
class RateLimitRuleTest {

    @Test
    void shouldCreateStableConfigurationVersionFromRule() {
        RateLimitRule left = new RateLimitRule(10, 5, Duration.ofSeconds(30));
        RateLimitRule right = new RateLimitRule(10, 5, Duration.ofSeconds(30));
        RateLimitRule changed = new RateLimitRule(20, 5, Duration.ofSeconds(30));

        assertThat(left.configurationVersion()).isEqualTo(right.configurationVersion());
        assertThat(left.configurationVersion()).isNotEqualTo(changed.configurationVersion());
    }

    @Test
    void shouldRejectInvalidRule() {
        assertThatThrownBy(() -> new RateLimitRule(0, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule(1, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule(1, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
