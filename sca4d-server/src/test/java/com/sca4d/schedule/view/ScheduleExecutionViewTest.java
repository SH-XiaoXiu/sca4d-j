package com.sca4d.schedule.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.ScheduleExecution;
import com.sca4d.schedule.TriggerSource;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

/** 验证执行历史只向 HTTP 返回本地化错误文本，不暴露内部消息键。 */
class ScheduleExecutionViewTest {

    @Test
    void shouldResolveStoredBusinessErrorKeyAtResponseBoundary() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T12:00:00Z");
        ScheduleExecution execution = new ScheduleExecution(
                2L,
                1L,
                TriggerSource.SCHEDULED,
                now,
                now,
                now,
                ExecutionStatus.SKIPPED,
                "notification.sse",
                null,
                "com.sca4d.common.exception.BizException",
                "error.schedule.execution-not-allowed",
                "node-1",
                now);

        ScheduleExecutionView view = ScheduleExecutionView.from(
                execution, LocalizedMessagesFixture.create());

        assertThat(view.getSafeErrorMessage()).isEqualTo("当前调度计划状态不允许执行");
        assertThat(view.getSafeErrorMessage()).doesNotContain("error.schedule");
    }
}
