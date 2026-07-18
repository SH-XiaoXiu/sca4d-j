package com.sca4d.gateway.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证连续成功和失败次数如何改变 upstream 健康状态。 */
class GatewayUpstreamHealthStateTest {

    private final GatewayUpstreamHealthState healthState = new GatewayUpstreamHealthState();

    @Test
    void shouldBecomeHealthyAfterThreshold() {
        assertThat(healthState.record("sca4d-app/app-1", true, 2, 3))
                .isEqualTo(GatewayUpstreamHealthState.HealthChange.UNCHANGED);
        assertThat(healthState.healthy("sca4d-app/app-1")).isFalse();

        assertThat(healthState.record("sca4d-app/app-1", true, 2, 3))
                .isEqualTo(GatewayUpstreamHealthState.HealthChange.BECAME_HEALTHY);
        assertThat(healthState.healthy("sca4d-app/app-1")).isTrue();
    }

    @Test
    void shouldBecomeUnhealthyAfterThreshold() {
        healthState.record("sca4d-app/app-1", true, 1, 2);
        assertThat(healthState.healthy("sca4d-app/app-1")).isTrue();

        assertThat(healthState.record("sca4d-app/app-1", false, 1, 2))
                .isEqualTo(GatewayUpstreamHealthState.HealthChange.UNCHANGED);
        assertThat(healthState.record("sca4d-app/app-1", false, 1, 2))
                .isEqualTo(GatewayUpstreamHealthState.HealthChange.BECAME_UNHEALTHY);
        assertThat(healthState.healthy("sca4d-app/app-1")).isFalse();
    }

    @Test
    void shouldDiscardHealthStateRemovedFromLatestSnapshot() {
        healthState.record("sca4d-app/app-1", true, 1, 1);
        healthState.record("sca4d-app/app-2", true, 1, 1);

        healthState.retain(Set.of("sca4d-app/app-2"));

        assertThat(healthState.healthy("sca4d-app/app-1")).isFalse();
        assertThat(healthState.healthy("sca4d-app/app-2")).isTrue();
    }

    @Test
    void shouldRejectNonPositiveTransitionThresholds() {
        assertThatThrownBy(() -> healthState.record("sca4d-app/app-1", true, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("健康状态转换阈值必须大于 0");
        assertThatThrownBy(() -> healthState.record("sca4d-app/app-1", false, 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("健康状态转换阈值必须大于 0");
    }
}
