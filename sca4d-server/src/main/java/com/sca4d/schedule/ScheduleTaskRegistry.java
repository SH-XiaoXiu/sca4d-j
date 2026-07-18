package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 按固定 taskType 建立且不依赖 Bean 注册顺序的 ScheduleTask 目录。 */
public class ScheduleTaskRegistry {

    private static final Pattern TASK_TYPE = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+$");

    private final Map<String, ScheduleTask<?>> tasks;

    /** @param tasks 当前应用提供的全部任务类型 */
    public ScheduleTaskRegistry(@NotNull List<ScheduleTask<?>> tasks) {
        Map<String, ScheduleTask<?>> registered = new LinkedHashMap<>();
        for (ScheduleTask<?> task : tasks) {
            if (task == null) {
                throw new IllegalStateException("ScheduleTask 不能为空");
            }
            String taskType = task.taskType() == null ? "" : task.taskType().trim();
            if (!TASK_TYPE.matcher(taskType).matches()) {
                throw new IllegalStateException("Schedule taskType 非法: " + taskType);
            }
            if (task.payloadVersion() < 1) {
                throw new IllegalStateException("Schedule Payload 版本必须为正数: " + taskType);
            }
            if (registered.putIfAbsent(taskType, task) != null) {
                throw new IllegalStateException("Schedule taskType 重复: " + taskType);
            }
            if (task.displayNameKey() == null || task.displayNameKey().isBlank()) {
                throw new IllegalStateException("Schedule displayNameKey 不能为空: " + taskType);
            }
        }
        this.tasks = Map.copyOf(registered);
    }

    /** @param taskType 固定任务类型 @return 已注册任务 @throws IllegalStateException 类型不存在时抛出 */
    public @NotNull ScheduleTask<?> require(@NotNull String taskType) {
        ScheduleTask<?> task = tasks.get(taskType);
        if (task == null) {
            throw new IllegalStateException("未注册 Schedule taskType: " + taskType);
        }
        return task;
    }

    /** @return 按 taskType 排列的不可变任务目录 */
    public @NotNull List<ScheduleTask<?>> tasks() {
        return tasks.values().stream().sorted(Comparator.comparing(ScheduleTask::taskType)).toList();
    }

    /**
     * 使用任务当前 Payload 版本解码、校验并重新编码客户端 JSON。
     *
     * <p>重新编码使相同业务 Payload 得到固定持久化结构，Schedule 核心操作无需按 taskType 分支。
     *
     * @param taskType 客户端选择的已注册任务类型
     * @param json 客户端提交的完整任务参数 JSON
     * @return 通过任务校验并按当前版本转换的持久化 JSON
     */
    public @NotNull String validateAndEncode(@NotNull String taskType, @NotNull String json) {
        ScheduleTask<?> task = require(taskType);
        return validateAndEncode(task, json);
    }

    /** @param taskType 已注册任务类型 @return 当前写入的 Payload 版本 */
    public int payloadVersion(@NotNull String taskType) {
        return require(taskType).payloadVersion();
    }

    /**
     * 按持久化版本解码并执行一次类型化任务。
     *
     * @param taskType Schedule 保存的固定任务类型
     * @param payloadVersion Schedule 保存的 Payload 版本
     * @param json Schedule 保存的 Payload JSON
     * @param execution 当前执行的幂等和时间数据
     * @return 允许写入执行历史的安全摘要
     */
    public @NotNull ScheduleTaskResult run(
            @NotNull String taskType,
            int payloadVersion,
            @NotNull String json,
            @NotNull ScheduleTaskExecution execution) {
        ScheduleTask<?> task = require(taskType);
        return run(task, payloadVersion, json, execution);
    }

    /**
     * 从注册表中的通配类型恢复任务的精确 Payload 泛型，并转换为当前版本。
     *
     * @param task 已按 taskType 精确选中的任务实现
     * @param json 客户端任务参数 JSON
     * @param <P> 任务声明的精确 Payload 类型
     * @return 通过任务校验的转换持久化 JSON
     */
    private static <P extends ScheduleTaskPayload> @NotNull String validateAndEncode(
            @NotNull ScheduleTask<P> task, @NotNull String json) {
        P payload = task.decode(task.payloadVersion(), json);
        task.validate(payload);
        return task.encode(payload);
    }

    /**
     * 从注册表中的通配类型恢复任务的精确 Payload 泛型，并调用唯一注册实现。
     *
     * @param task 已按 taskType 精确选中的任务实现
     * @param payloadVersion 持久化 Payload 版本
     * @param json 持久化 Payload JSON
     * @param execution 当前执行数据
     * @param <P> 任务声明的精确 Payload 类型
     * @return 允许写入执行历史的安全摘要
     */
    private static <P extends ScheduleTaskPayload> @NotNull ScheduleTaskResult run(
            @NotNull ScheduleTask<P> task,
            int payloadVersion,
            @NotNull String json,
            @NotNull ScheduleTaskExecution execution) {
        return task.execute(task.decode(payloadVersion, json), execution);
    }
}
