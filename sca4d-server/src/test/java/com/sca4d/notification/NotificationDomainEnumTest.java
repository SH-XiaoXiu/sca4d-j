package com.sca4d.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证通知状态枚举的固定值和解析规则。 */
class NotificationDomainEnumTest {

    @Test
    void shouldResolveDefaultCategory() {
        assertThat(NotificationCategory.resolve(null)).isEqualTo(NotificationCategory.SYSTEM);
        assertThat(NotificationCategory.resolve("system")).isEqualTo(NotificationCategory.SYSTEM);
    }

    @Test
    void shouldMatchDeliveryStatus() {
        assertThat(NotificationDeliveryStatus.UNREAD.matches("unread")).isTrue();
        assertThat(NotificationDeliveryStatus.READ.matches("unread")).isFalse();
    }
}
