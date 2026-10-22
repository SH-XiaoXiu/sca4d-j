package com.sca4d.iot.persistence.mapper;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.persistence.EncodedDeviceAcknowledgement;
import com.sca4d.iot.persistence.EncodedDownlinkMessage;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 读写 `iot_downlink_message` 中的业务下行状态和最新 ACK。 */
public interface DeviceDownlinkMapper {

    /** 按设备集合读取每个设备最近的下行消息，不统计完整历史数量。 */
    @NotNull
    List<EncodedDownlinkMessage> selectRecentDeviceDownlinks(
            @Param("deviceIds") @NotNull List<@NotNull Long> deviceIds,
            @Param("limit") int limit);

    /** 批量读取仍保存在主库中的最新回执投影。 */
    @NotNull
    List<EncodedDeviceAcknowledgement> selectAcknowledgementsByMessageIds(
            @Param("messageIds") @NotNull List<@NotNull Long> messageIds);

    /** 统计 Device 的全部下行消息。 */
    long countDeviceDownlinks(@Param("deviceId") long deviceId);

    /** 分页读取 Device 下行消息。 */
    @NotNull
    List<EncodedDownlinkMessage> selectDeviceDownlinks(
            @Param("deviceId") long deviceId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 统计 Device 下行消息中已经保存最新 ACK 的记录。 */
    long countLatestAcknowledgements(@Param("deviceId") long deviceId);

    /** 分页读取 Device 下行消息保存的最新 ACK。 */
    @NotNull
    List<EncodedDeviceAcknowledgement> selectLatestAcknowledgements(
            @Param("deviceId") long deviceId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /** 按下行消息标识读取主库保存的最近 ACK 投影。 */
    @Nullable
    EncodedDeviceAcknowledgement selectAcknowledgementByMessageId(@Param("messageId") long messageId);

    /** 查询当前物模型绑定仍未完成的下行消息数量。 */
    long countActiveDownlinkMessages(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration);

    /** 新增标准下行业务状态。 */
    int insertDownlinkMessage(
            @Param("messageId") long messageId,
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("kind") @NotNull DeviceDownlinkKind kind,
            @Param("targetCode") @NotNull String targetCode,
            @Param("desiredSequence") @Nullable Long desiredSequence,
            @Param("scheduleId") @Nullable Long scheduleId,
            @Param("scheduledAt") @Nullable Instant scheduledAt,
            @Param("dataJson") @NotNull String dataJson,
            @Param("acknowledgementPolicy") @NotNull TreeCode acknowledgementPolicy,
            @Param("acknowledgementTimeoutMillis") long acknowledgementTimeoutMillis,
            @Param("retryPolicy") @NotNull TreeCode retryPolicy,
            @Param("expiresAt") @Nullable Instant expiresAt);

    /**
     * @param messageIds 非空且数量受 Repository 分片限制的全局消息标识
     * @return 按消息标识升序排列的现存下行消息
     */
    @NotNull
    List<EncodedDownlinkMessage> selectDownlinkMessages(
            @Param("messageIds") @NotNull List<@NotNull Long> messageIds);

    /** 按消息或 desired 属性范围和当前进展条件执行字段 Patch。 */
    int updateProgress(
            @Param("messageId") @Nullable Long messageId,
            @Param("deviceId") @Nullable Long deviceId,
            @Param("bindingGeneration") @Nullable Long bindingGeneration,
            @Param("kind") @Nullable DeviceDownlinkKind kind,
            @Param("targetCode") @Nullable String targetCode,
            @Param("expectedStatuses") @NotNull Set<DeviceDownlinkStatus> expectedStatuses,
            @Param("expectedAttempt") @Nullable Integer expectedAttempt,
            @Param("status") @NotNull Patch<DeviceDownlinkStatus> status,
            @Param("attemptCount") @NotNull Patch<Integer> attemptCount,
            @Param("ackDeadline") @NotNull Patch<Instant> ackDeadline,
            @Param("completedAt") @NotNull Patch<Instant> completedAt,
            @Param("failureCode") @NotNull Patch<String> failureCode);

    /** 只在 ACK 事实标识变化时替换业务状态中的最新 ACK。 */
    int updateLatestAcknowledgement(
            @Param("messageId") long messageId,
            @Param("identityHash") @NotNull String identityHash,
            @Param("ackCode") @NotNull String ackCode,
            @Param("dataJson") @NotNull String dataJson,
            @Param("source") @NotNull String source,
            @Param("sourceAcknowledgementId") @Nullable String sourceAcknowledgementId,
            @Param("acknowledgedAt") @NotNull Instant acknowledgedAt,
            @Param("receivedAt") @NotNull Instant receivedAt);

    /** 分批删除保留期外的最终状态下行消息。 */
    int deleteCompletedBefore(
            @Param("completedBefore") @NotNull Instant completedBefore,
            @Param("batchSize") int batchSize);

}
