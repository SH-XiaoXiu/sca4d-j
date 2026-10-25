package com.sca4d.iot.api.query;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
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
     * 批量读取每个设备最近的下行消息，按创建时间倒序排列。
     *
     * @param deviceIds 正数设备标识；重复标识只查询一次，空集合直接返回空映射
     * @param limitPerDevice 每个设备的记录上限，范围为 1 到 100
     * @return 以设备标识为键的不可修改映射和列表；没有记录时对应空列表
     * @throws IllegalArgumentException 标识或数量上限不合法时抛出
     * @throws BusinessException 任一设备不存在时抛出
     */
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull List<@NotNull DeviceDownlink>> recentDownlinks(
            @NotNull Collection<@NotNull Long> deviceIds,
            int limitPerDevice);

    /**
     * 批量读取每个设备最近的事件，按发生时间倒序排列。
     *
     * @param deviceIds 正数设备标识；重复标识只查询一次，空集合直接返回空映射
     * @param limitPerDevice 每个设备的记录上限，范围为 1 到 100
     * @return 以设备标识为键的不可修改映射和列表；没有记录时对应空列表
     * @throws IllegalArgumentException 标识或数量上限不合法时抛出
     * @throws BusinessException 任一设备不存在时抛出；事件查询未安装历史存储时抛出依赖不可用错误
     */
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull List<@NotNull DeviceEvent>> recentEvents(
            @NotNull Collection<@NotNull Long> deviceIds,
            int limitPerDevice);

    /**
     * 批量读取每条消息最近的回执，按接收时间倒序排列。历史回执优先；某条消息没有历史回执或未安装历史存储时返回主库最新回执。
     *
     * @param messageIds 正数消息标识；重复标识只查询一次，空集合直接返回空映射
     * @param limitPerMessage 每个消息的记录上限，范围为 1 到 100
     * @return 以消息标识为键的不可修改映射和列表；没有记录时对应空列表
     * @throws IllegalArgumentException 标识或数量上限不合法时抛出
     */
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull List<@NotNull DeviceAcknowledgement>> recentAcknowledgements(
            @NotNull Collection<@NotNull Long> messageIds,
            int limitPerMessage);

    /**
     * @param messageId 全局下行消息标识
     * @return 当前主库中的下行消息快照；不存在或已按保留策略删除时为空
     */
    @NotNull
    Optional<DeviceDownlink> downlink(long messageId);

    /**
     * @param messageId 全局下行消息标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按接收时间倒序排列的下行 ACK 历史；主库只保留最新投影
     */
    @NotNull
    Page<DeviceAcknowledgement> acknowledgementsByMessageId(long messageId, @NotNull PageRequest page);

    /**
     * @param messageIds 全局下行消息标识；重复标识只查询一次
     * @return 仍保存在主库中的下行消息；已删除或不存在的标识不出现在结果中
     */
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull DeviceDownlink> downlinks(
            @NotNull Collection<@NotNull Long> messageIds);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按创建时间倒序排列的下行消息页
     * @throws BusinessException Device 不存在时抛出
     * @throws IllegalArgumentException 分页参数超出范围时抛出
     */
    @NotNull
    Page<DeviceDownlink> downlinks(long deviceId, @NotNull PageRequest page);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按接收时间倒序排列的业务回执页
     * @throws BusinessException Device 不存在时抛出
     * @throws IllegalArgumentException 分页参数超出范围时抛出
     */
    @NotNull
    Page<DeviceAcknowledgement> acknowledgements(long deviceId, @NotNull PageRequest page);

    /**
     * @param deviceId Device 标识
     * @param page 从 1 开始且页大小不超过 100 的分页要求
     * @return 按发生时间倒序排列的设备事件页
     * @throws BusinessException Device 不存在时抛出
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
     * @throws BusinessException Device 不存在、参数非法或高频存储未安装时抛出
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
