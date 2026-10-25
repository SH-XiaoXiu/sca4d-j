package com.sca4d.messaging.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 验证发布确认超时和租约时长无法保留毫秒精度或超过毫秒范围时被拒绝。 */
class MessagingPropertiesTest {

    @Test
    @DisplayName("拒绝无法保留毫秒语义的发布和 Outbox 时长")
    void rejectsDurationsThatCannotRetainMillisecondSemantics() {
        MessagingProperties properties = new MessagingProperties();

        assertThatThrownBy(() -> properties.setConfirmTimeout(Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能短于 1 毫秒");
        assertThatThrownBy(() -> properties.setLeaseDuration(Duration.ofSeconds(Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过毫秒表示范围");
    }
}
