package com.sca4d.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证固定和资源级客户端状态目标的编码规则。 */
class ClientStateTargetTest {

    @Test
    void shouldCreateComparableFixedAndResourceTargets() {
        assertThat(ClientStateTarget.of("menus")).isEqualTo(ClientStateTarget.of("menus"));
        assertThat(ClientStateTarget.resource("principal", 10L).getValue()).isEqualTo("principal:10");
    }

    @Test
    void shouldRejectValuesThatCannotBeWrittenToRefreshHeader() {
        assertThatThrownBy(() -> ClientStateTarget.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientStateTarget.of("menus,accounts"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientStateTarget.of("menus\naccounts"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientStateTarget.resource("Principal", 10L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
