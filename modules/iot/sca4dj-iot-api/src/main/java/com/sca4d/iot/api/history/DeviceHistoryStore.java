package com.sca4d.iot.api.history;

import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.query.DeviceEvent;
import com.sca4d.iot.api.schedule.DeviceScheduleExecution;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;

/** 从具体应用选择的历史存储读取 Device 事件、ACK、定时结果和 Telemetry。 */
public interface DeviceHistoryStore {

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按发生时间倒序排列的 Device 事件页
     * @throws IllegalArgumentException Device 标识或分页参数不合法时抛出
     */
    @NotNull
    Page<DeviceEvent> events(long deviceId, @NotNull PageRequest page);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按接收时间倒序排列的 Device ACK 页
     * @throws IllegalArgumentException Device 标识或分页参数不合法时抛出
     */
    @NotNull
    Page<DeviceAcknowledgement> acknowledgements(long deviceId, @NotNull PageRequest page);

    /**
     * @param messageId 下行消息标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按接收时间倒序排列的该下行 ACK 历史
     */
    @NotNull
    Page<DeviceAcknowledgement> acknowledgementsByMessageId(long messageId, @NotNull PageRequest page);

    /**
     * @param scheduleId 设备定时规则标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按计划时间倒序排列的设备定时执行结果页
     * @throws IllegalArgumentException 定时规则标识或分页参数不合法时抛出
     */
    @NotNull
    Page<DeviceScheduleExecution> scheduleExecutions(long scheduleId, @NotNull PageRequest page);

    /**
     * @param deviceId Device 标识
     * @param propertyCode 物模型属性编码
     * @param from 包含的观测开始时间
     * @param to 不包含的观测结束时间
     * @param limit 不超过存储实现配置上限的最大返回数量
     * @return 半开时间范围内按观察时间升序排列的不可修改 Telemetry 列表
     * @throws IllegalArgumentException Device、属性、时间范围或数量上限不合法时抛出
     */
    @NotNull
    @Unmodifiable
    List<@NotNull TelemetrySample> telemetry(
            long deviceId,
            @NotNull String propertyCode,
            @NotNull Instant from,
            @NotNull Instant to,
            int limit);

}
