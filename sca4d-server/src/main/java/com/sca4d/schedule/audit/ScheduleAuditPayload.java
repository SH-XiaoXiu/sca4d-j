package com.sca4d.schedule.audit;

import com.sca4d.common.audit.AuditPayload;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.TriggerSource;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/** Schedule 写操作允许长期保存且不包含通知正文和完整接收人的审计载荷。 */
@Data
@Accessors(chain = true)
public class ScheduleAuditPayload implements AuditPayload {
    private String taskType;
    private ScheduleType scheduleType;
    private OffsetDateTime executeAt;
    private String cronExpression;
    private String timeZone;
    private ScheduleStatus status;
    private Integer recipientCount;
    private TriggerSource triggerSource;

    /**
     * 构造创建、完整替换或删除 Schedule 的安全审计载荷。
     *
     * @param taskType 已注册开放任务类型
     * @param scheduleType ONCE 或 CRON
     * @param executeAt ONCE 执行时间
     * @param cronExpression CRON 表达式
     * @param timeZone CRON IANA 时区
     * @param status ACTIVE 或 PAUSED
     * @param recipientCount notification.sse 明确接收人数量；其他任务可为空
     * @return 不含完整正文和接收人列表的审计载荷
     */
    public static @NotNull ScheduleAuditPayload schedule(
            @NotNull String taskType,
            @NotNull ScheduleType scheduleType,
            @Nullable OffsetDateTime executeAt,
            @Nullable String cronExpression,
            @Nullable String timeZone,
            @NotNull ScheduleStatus status,
            @Nullable Integer recipientCount) {
        return new ScheduleAuditPayload()
                .setTaskType(taskType)
                .setScheduleType(scheduleType)
                .setExecuteAt(executeAt)
                .setCronExpression(cronExpression)
                .setTimeZone(timeZone)
                .setStatus(status)
                .setRecipientCount(recipientCount);
    }

    /**
     * 构造人工 ScheduleExecution 的安全审计载荷。
     *
     * @param taskType 当前 Schedule 的已注册任务类型
     * @param triggerSource 固定为 MANUAL 的触发来源
     * @return 只记录任务类型和触发来源的审计载荷
     */
    public static @NotNull ScheduleAuditPayload execution(
            @NotNull String taskType, @NotNull TriggerSource triggerSource) {
        return new ScheduleAuditPayload().setTaskType(taskType).setTriggerSource(triggerSource);
    }

    /**
     * 构造业务失败时没有成功结果快照的安全审计载荷。
     *
     * @return 保持 Schedule 审计载荷类型且不虚构未产生的业务数据
     */
    public static @NotNull ScheduleAuditPayload failure() {
        return new ScheduleAuditPayload();
    }

    /**
     * 构造不读取已删除资源正文和接收人快照的删除审计载荷。
     *
     * @return 保持同一长期类型且不额外复制删除前业务内容的载荷
     */
    public static @NotNull ScheduleAuditPayload deleted() {
        return new ScheduleAuditPayload();
    }
}
