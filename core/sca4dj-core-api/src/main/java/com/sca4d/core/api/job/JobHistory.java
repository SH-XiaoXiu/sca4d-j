package com.sca4d.core.api.job;

import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 只读查询当前 App 隔离名称中的持久化 Job 历史，不提交或推进 Job。 */
public interface JobHistory {

    /**
     * 按原定触发时间倒序返回指定计划和类型化节点的运行历史。
     *
     * @param scheduleKey 提交定时 run 时使用的计划标识
     * @param output 已由 PipelineJob 校验且不包含实际运行结果的节点输出声明
     * @param page 调用方已经确定大小限制的分页要求
     * @param <T> 节点输出类型
     * @return 当前 App 隔离名称、Job 定义和计划共同限定的运行分页
     */
    @CheckReturnValue
    <T> @NotNull Page<ScheduledJobRun<T>> findScheduledRuns(
            @NotNull ScheduleKey scheduleKey,
            @NotNull JobNodeOutput<T> output,
            @NotNull PageRequest page);
}
