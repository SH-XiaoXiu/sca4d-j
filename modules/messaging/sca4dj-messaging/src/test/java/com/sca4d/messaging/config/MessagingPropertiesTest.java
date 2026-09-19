package com.sca4d.messaging.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 验证 Messaging 毫秒时长配置在绑定边界拒绝截断和溢出。 */
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
