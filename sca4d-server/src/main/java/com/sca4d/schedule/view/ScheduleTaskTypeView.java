package com.sca4d.schedule.view;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.schedule.ScheduleTaskType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 管理端任务类型选择目录项。 */
@Data
@Accessors(chain = true)
public class ScheduleTaskTypeView {
    private String taskType;
    private Integer payloadVersion;
    private String displayName;

    /**
     * @param taskType 已注册任务类型
     * @param messages 当前请求语言的消息解析入口
     * @return 管理端安全目录项
     */
    public static @NotNull ScheduleTaskTypeView from(
            @NotNull ScheduleTaskType taskType, @NotNull LocalizedMessages messages) {
        return new ScheduleTaskTypeView()
                .setTaskType(taskType.getTaskType())
                .setPayloadVersion(taskType.getPayloadVersion())
                .setDisplayName(messages.get(taskType.getDisplayNameKey()));
    }
}
