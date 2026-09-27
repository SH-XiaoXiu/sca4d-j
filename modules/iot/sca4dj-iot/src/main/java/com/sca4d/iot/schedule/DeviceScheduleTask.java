package com.sca4d.iot.schedule;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.scheduling.ClusterTask;
import com.sca4d.iot.api.IotCapabilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** 连续触发一批通过 Redis 分布式锁互斥执行的到期设备定时规则。 */
public class DeviceScheduleTask implements ClusterTask {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceScheduleTask.class);

    private final @NotNull DefaultDeviceScheduleService schedules;

    private final int batchSize;

    /**
     * 创建不持有 Redis 索引或本机规则状态的设备定时任务。
     *
     * @param schedules 领取并触发到期规则的设备定时服务
     * @param batchSize 每次执行最多处理的规则数，范围为 1 到 10000
     * @throws NullPointerException 设备定时服务为空时抛出
     * @throws IllegalArgumentException 批量无效时抛出
     */
    public DeviceScheduleTask(
            @NotNull DefaultDeviceScheduleService schedules,
            int batchSize) {
        this.schedules = Objects.requireNonNull(schedules, "设备定时服务不能为空");
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("设备定时单轮批量必须在 1 到 10000 之间");
        }
        this.batchSize = batchSize;
    }

    /** 每条规则先取得分布式锁再使用独立事务；业务校验失败会暂停规则，基础设施失败留给下一次触发重新扫描。 */
    @Override
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public void execute() {
        int handled = 0;
        while (handled < batchSize) {
            try {
                if (!schedules.triggerNextDueSchedule()) {
                    break;
                }
                handled++;
            } catch (DeviceScheduleTriggerFailure failure) {
                try {
                    schedules.pauseFailedSchedule(failure);
                    handled++;
                    LOG.warn(
                            "设备定时规则已因业务校验失败暂停 | scheduleId={} version={} failure={}",
                            failure.scheduleId(),
                            failure.scheduleVersion(),
                            failure.failureCode());
                } catch (RuntimeException exception) {
                    LOG.warn(
                            "设备定时失败结果未保存，规则留到下一轮重试 | scheduleId={} version={}",
                            failure.scheduleId(),
                            failure.scheduleVersion(),
                            exception);
                    break;
                }
            } catch (RuntimeException exception) {
                LOG.warn(
                        "设备定时扫描失败，规则保留并等待下一轮重试 | errorType={}",
                        exception.getClass().getSimpleName(),
                        exception);
                break;
            }
        }
        if (handled > 0) {
            LOG.debug("到期 Device 定时规则已处理 | count={}", handled);
        }
    }
}
