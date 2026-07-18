package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;

/** 管理端创建 Schedule 时使用的已注册任务类型目录项。 */
public final class ScheduleTaskType {

    private final String taskType;
    private final int payloadVersion;
    private final String displayNameKey;

    /**
     * @param taskType ScheduleTask 注册的任务类型编码
     * @param payloadVersion 当前创建时写入的 Payload 版本
     * @param displayNameKey 当前任务类型的固定本地化消息键
     */
    public ScheduleTaskType(@NotNull String taskType, int payloadVersion, @NotNull String displayNameKey) {
        this.taskType = taskType;
        this.payloadVersion = payloadVersion;
        this.displayNameKey = displayNameKey;
    }

    /** @return ScheduleTask 注册的任务类型编码 */
    public @NotNull String getTaskType() {
        return taskType;
    }

    /** @return 当前创建时写入的 Payload 版本 */
    public int getPayloadVersion() {
        return payloadVersion;
    }

    /** @return 当前任务类型的固定本地化消息键 */
    public @NotNull String getDisplayNameKey() {
        return displayNameKey;
    }
}
