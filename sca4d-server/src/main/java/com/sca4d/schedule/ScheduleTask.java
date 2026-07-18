package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;

/**
 * 可注册 Schedule 任务类型的固定扩展契约。
 *
 * @param <P> 当前任务持久化 Payload 类型
 */
public interface ScheduleTask<P extends ScheduleTaskPayload> {

    /** @return 不依赖 Bean 名或 Java 类名的固定任务类型 */
    @NotNull
    String taskType();

    /** @return 当前写入的 Payload 版本 */
    int payloadVersion();

    /** @return 用于精确类型校验的 Payload Java 类型 */
    @NotNull
    Class<P> payloadClass();

    /** @param payload 已校验的类型化 Payload @return 长期持久化 JSON */
    @NotNull
    String encode(@NotNull P payload);

    /** @param version 持久化版本 @param json 持久化 JSON @return 类型化 Payload */
    @NotNull
    P decode(int version, @NotNull String json);

    /** @param payload 创建、更新或执行前需要校验的完整 Payload */
    void validate(@NotNull P payload);

    /** @param payload 当前持久化 Payload @param execution 执行数据 @return 安全结果摘要 */
    @NotNull
    ScheduleTaskResult execute(@NotNull P payload, @NotNull ScheduleTaskExecution execution);

    /** @return 管理端任务类型目录使用的固定本地化消息键 */
    @NotNull
    String displayNameKey();
}
