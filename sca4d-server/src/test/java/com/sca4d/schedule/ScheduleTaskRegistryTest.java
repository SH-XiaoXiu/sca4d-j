package com.sca4d.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证 ScheduleTask 注册、重复拒绝、Payload 解析和版本转换。 */
class ScheduleTaskRegistryTest {

    @Test
    void shouldRegisterTasksInStableOrderAndRejectDuplicateOrUnknownTypes() {
        ScheduleTask<?> second = mock(ScheduleTask.class);
        when(second.taskType()).thenReturn("notification.webhook");
        when(second.payloadVersion()).thenReturn(2);
        when(second.displayNameKey()).thenReturn("schedule.task.notification-webhook");
        ScheduleTask<?> first = mock(ScheduleTask.class);
        when(first.taskType()).thenReturn("notification.sse");
        when(first.payloadVersion()).thenReturn(1);
        when(first.displayNameKey()).thenReturn("schedule.task.notification-sse");

        ScheduleTaskRegistry registry = new ScheduleTaskRegistry(List.of(second, first));

        assertThat(registry.tasks()).containsExactly(first, second);
        assertThat(registry.require("notification.sse")).isSameAs(first);
        assertThatThrownBy(() -> registry.require("notification.unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注册");
        assertThatThrownBy(() -> new ScheduleTaskRegistry(List.of(first, first)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void shouldRejectExecutableNamesAndInvalidPayloadVersions() {
        ScheduleTask<?> className = mock(ScheduleTask.class);
        when(className.taskType()).thenReturn("com.sca4d.Task");
        when(className.payloadVersion()).thenReturn(1);
        when(className.displayNameKey()).thenReturn("schedule.task.class-name");
        ScheduleTask<?> invalidVersion = mock(ScheduleTask.class);
        when(invalidVersion.taskType()).thenReturn("notification.sse");
        when(invalidVersion.payloadVersion()).thenReturn(0);
        when(invalidVersion.displayNameKey()).thenReturn("schedule.task.notification-sse");

        assertThatThrownBy(() -> new ScheduleTaskRegistry(List.of(className)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法");
        assertThatThrownBy(() -> new ScheduleTaskRegistry(List.of(invalidVersion)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("版本");
    }
}
