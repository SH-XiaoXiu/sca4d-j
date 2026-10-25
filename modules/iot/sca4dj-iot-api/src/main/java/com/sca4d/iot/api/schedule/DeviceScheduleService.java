package com.sca4d.iot.api.schedule;

import com.sca4d.core.api.error.exception.BusinessException;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 创建、替换、暂停和取消负责生成设备 command 下行的定时规则。 */
public interface DeviceScheduleService {

    /**
     * 创建并登记下一次触发时间；command 在每次到期时按 Device 当前绑定物模型重新校验。
     *
     * @param deviceId Device 标识
     * @param command 到期时提交的 command 和输入
     * @param trigger 未来的单次时间或带时区六字段 Cron
     * @param validFor 从计划时间开始计算的正数下行有效期
     * @return 已保存的 ACTIVE 规则
     * @throws BusinessException Device 不存在或触发规则非法时抛出
     * @throws IllegalArgumentException command 或有效期为空、有效期不是正数或毫秒数溢出时抛出
     */
    @CheckReturnValue
    @NotNull
    DeviceSchedule create(
            long deviceId,
            @NotNull DeviceScheduledCommand command,
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Duration validFor);

    /**
     * 按乐观锁版本完整替换 command、触发规则和有效期。
     *
     * @param scheduleId 规则标识
     * @param expectedVersion 调用方最后读取到的非负版本，新建规则初始为 0
     * @param command 替换后的 command 和输入
     * @param trigger 替换后的未来单次时间或带时区六字段 Cron
     * @param validFor 从每次计划时间开始计算的正数下行有效期
     * @return 版本已经推进的 ACTIVE 规则
     * @throws BusinessException 触发规则非法、规则不存在或版本变化时抛出
     * @throws IllegalArgumentException command 或有效期为空、有效期不是正数或毫秒数溢出时抛出
     */
    @CheckReturnValue
    @NotNull
    DeviceSchedule replace(
            long scheduleId,
            long expectedVersion,
            @NotNull DeviceScheduledCommand command,
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Duration validFor);

    /**
     * 按乐观锁版本暂停或恢复尚未取消、尚未完成的规则。
     *
     * @param scheduleId 规则标识
     * @param expectedVersion 调用方最后读取到的非负版本，新建规则初始为 0
     * @param status 只允许 `ACTIVE` 或 `PAUSED`
     * @return 状态已经修改的规则；目标状态与当前状态相同时返回当前规则
     * @throws BusinessException 状态非法、规则不存在、版本变化或规则已经结束时抛出
     */
    @CheckReturnValue
    @NotNull
    DeviceSchedule changeStatus(
            long scheduleId,
            long expectedVersion,
            @NotNull DeviceScheduleStatus status);

    /**
     * 按乐观锁版本永久取消规则，已经生成的下行继续遵守自身状态机。
     *
     * @param scheduleId 规则标识
     * @param expectedVersion 调用方最后读取到的非负版本，新建规则初始为 0
     * @throws BusinessException 规则不存在、版本变化或规则已经取消时抛出
     */
    void cancel(long scheduleId, long expectedVersion);
}
