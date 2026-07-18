package com.sca4d.schedule.view;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.ScheduleExecution;
import com.sca4d.schedule.TriggerSource;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** ScheduleExecution 子资源的安全管理端表示。 */
@Data
@Accessors(chain = true)
public class ScheduleExecutionView {
    private Long executionId;
    private Long scheduleId;
    private TriggerSource triggerSource;
    private OffsetDateTime scheduledFireTime;
    private OffsetDateTime actualFireTime;
    private OffsetDateTime completedAt;
    private ExecutionStatus status;
    private String taskType;
    private String resultSummary;
    private String safeErrorType;
    private String safeErrorMessage;
    private String executedInstanceId;
    private OffsetDateTime createdAt;

    /**
     * @param execution 当前执行数据
     * @param messages 当前请求语言的消息解析入口
     * @return 不暴露内部 fireKey 或消息键的安全资源表示
     */
    public static @NotNull ScheduleExecutionView from(
            @NotNull ScheduleExecution execution, @NotNull LocalizedMessages messages) {
        return new ScheduleExecutionView()
                .setExecutionId(execution.getExecutionId())
                .setScheduleId(execution.getScheduleId())
                .setTriggerSource(execution.getTriggerSource())
                .setScheduledFireTime(execution.getScheduledFireTime())
                .setActualFireTime(execution.getActualFireTime())
                .setCompletedAt(execution.getCompletedAt())
                .setStatus(execution.getStatus())
                .setTaskType(execution.getTaskType())
                .setResultSummary(execution.getResultSummary())
                .setSafeErrorType(execution.getSafeErrorType())
                .setSafeErrorMessage(execution.getSafeErrorMessageKey() == null
                        ? null
                        : messages.get(execution.getSafeErrorMessageKey()))
                .setExecutedInstanceId(execution.getExecutedInstanceId())
                .setCreatedAt(execution.getCreatedAt());
    }
}
