package com.sca4d.iot.timescale.persistence;

import com.sca4d.iot.api.history.DeviceEventMessage;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** 定义独立 TimescaleDB 中 Telemetry、事件查询、分区键内幂等写入和保留期清理 SQL。 */
public interface TimescaleMapper {

    /** 查询 Device 一个属性在半开时间范围内的 Telemetry。 */
    @NotNull
    List<EncodedTelemetrySample> selectTelemetry(
            @Param("deviceId") long deviceId,
            @Param("propertyCode") @NotNull String propertyCode,
            @Param("from") @NotNull Instant from,
            @Param("to") @NotNull Instant to,
            @Param("limit") int limit);

    /** 统计按 Device、事件编码或定时规则筛选的事件。 */
    long countEvents(
            @Param("deviceId") @Nullable Long deviceId,
            @Param("eventCode") @Nullable String eventCode,
            @Param("scheduleId") @Nullable Long scheduleId);

    /** 分页读取按 Device、事件编码或定时规则筛选的事件。 */
    @NotNull
    List<TimescaleThingEvent> selectEvents(
            @Param("deviceId") @Nullable Long deviceId,
            @Param("eventCode") @Nullable String eventCode,
            @Param("scheduleId") @Nullable Long scheduleId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 统计指定下行消息的 ACK 事件。 */
    long countAcknowledgementsByMessageId(@Param("messageId") long messageId);

    /** 分页读取指定下行消息的 ACK 事件。 */
    @NotNull
    List<TimescaleThingEvent> selectAcknowledgementsByMessageId(
            @Param("messageId") long messageId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 一次 SQL 幂等写入同一消息的全部 Telemetry 样本。 */
    int insertTelemetrySamples(@Param("samples") @NotNull List<TimescaleTelemetrySample> samples);

    /** 按发生时间和 MessageId 写入事件；同一发生时间内也约束来源事实键唯一。 */
    int insertEvent(
            @Param("messageId") long messageId,
            @Param("event") @NotNull DeviceEventMessage event,
            @Param("dataJson") @NotNull String dataJson);

    /** 删除早于截止时间的完整 Telemetry chunk。 */
    @NotNull
    List<String> dropTelemetryChunks(@Param("olderThan") @NotNull Instant olderThan);

    /** 删除早于截止时间的完整事件 chunk。 */
    @NotNull
    List<String> dropEventChunks(@Param("olderThan") @NotNull Instant olderThan);
}
