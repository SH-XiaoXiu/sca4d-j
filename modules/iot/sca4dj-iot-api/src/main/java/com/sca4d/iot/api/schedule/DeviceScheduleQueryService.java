package com.sca4d.iot.api.schedule;

import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import org.jetbrains.annotations.NotNull;

/** 查询 Device 定时规则和每次计划时间的下行生成结果。 */
public interface DeviceScheduleQueryService {

    /**
     * @param scheduleId 规则标识
     * @return 指定规则
     * @throws com.sca4d.core.api.error.BusinessException 规则不存在时抛出状态冲突错误
     */
    @NotNull
    DeviceSchedule loadById(long scheduleId);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return Device 的定时规则页，按标识倒序排列
     * @throws com.sca4d.core.api.error.BusinessException Device 不存在时抛出
     * @throws IllegalArgumentException 分页参数超出范围时抛出
     */
    @NotNull
    Page<DeviceSchedule> schedules(long deviceId, @NotNull PageRequest page);

    /**
     * @param scheduleId 规则标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 规则执行记录页，按计划时间倒序排列
     * @throws com.sca4d.core.api.error.BusinessException 规则不存在时抛出状态冲突错误
     * @throws IllegalArgumentException 分页参数超出范围时抛出
     */
    @NotNull
    Page<DeviceScheduleExecution> executions(long scheduleId, @NotNull PageRequest page);
}
