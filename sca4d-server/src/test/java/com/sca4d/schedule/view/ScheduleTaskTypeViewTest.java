package com.sca4d.schedule.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.schedule.ScheduleTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/** 验证 ScheduleTask 目录在 HTTP 边界使用当前请求语言。 */
class ScheduleTaskTypeViewTest {

    @Test
    void shouldResolveDisplayNameForCurrentRequestLocale() {
        LocaleContextHolder.setLocale(Locale.US);
        try {
            ScheduleTaskTypeView view = ScheduleTaskTypeView.from(
                    new ScheduleTaskType("notification.sse", 1, "schedule.task.notification-sse"),
                    LocalizedMessagesFixture.create());

            assertThat(view.getDisplayName()).isEqualTo("Realtime in-app notification");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
