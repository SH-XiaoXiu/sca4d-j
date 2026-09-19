package com.sca4d.iot.api.query;

import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 分页读取 Device 下行、回执、事件和 Telemetry 历史。 */
public interface DeviceHistoryQueryService {

    /**
     * @param messageId 全局下行消息标识
     * @return 当前主库中的下行消息快照；不存在或已按保留策略删除时为空
     */
    @NotNull
    Optional<DeviceDownlinkMessage> downlink(long messageId);

    /**
     * @param messageIds 全局下行消息标识；重复标识只查询一次
     * @return 仍保存在主库中的下行消息；已删除或不存在的标识不出现在结果中
     */
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull DeviceDownlinkMessage> downlinks(
            @NotNull Collection<@NotNull Long> messageIds);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按创建时间倒序排列的下行消息页
     * @throws com.sca4d.core.api.error.BusinessException Device 不存在时抛出
     * @throws IllegalArgumentException 分页参数超出范围时抛出
     */
    @NotNull
    Page<DeviceDownlinkMessage> downlinks(long deviceId, @NotNull PageRequest page);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按接收时间倒序排列的业务回执页
     * @throws com.sca4d.core.api.error.BusinessException Device 不存在时抛出
     * @throws IllegalArgumentException 分页参数超出范围时抛出
     */
    @NotNull
    Page<DeviceAcknowledgement> acknowledgements(long deviceId, @NotNull PageRequest page);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按发生时间倒序排列的设备事件页
     * @throws com.sca4d.core.api.error.BusinessException Device 不存在时抛出
     * @throws IllegalArgumentException 分页参数超出范围时抛出
     */
    @NotNull
    Page<DeviceEvent> events(long deviceId, @NotNull PageRequest page);

    /**
     * @param deviceId Device 标识
     * @param propertyCode 物模型属性 code
     * @param from 包含的起始时间
     * @param to 不包含的结束时间，必须晚于 `from`
     * @param limit 不超过历史存储配置上限的最大返回数量
     * @return 指定半开时间范围内按观测时间升序排列的 Telemetry
     * @throws com.sca4d.core.api.error.BusinessException Device 不存在、参数非法或高频存储未安装时抛出
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
