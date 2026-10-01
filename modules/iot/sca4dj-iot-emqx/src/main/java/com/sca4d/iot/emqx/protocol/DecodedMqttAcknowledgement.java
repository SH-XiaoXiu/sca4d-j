package com.sca4d.iot.emqx.protocol;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 保存厂商 ACK decoder 已解析、尚未分配平台标识的业务回执。 */
public class DecodedMqttAcknowledgement {

    private final @Nullable Instant acknowledgedAt;

    private final @NotNull Map<String, Object> data;

    private final @NotNull String ackCode;

    private final @Nullable String sourceAcknowledgementId;

    private final long messageId;

    /**
     * @param messageId 下行消息携带给设备并由设备原样返回的标识
     * @param ackCode 交给 `AcknowledgementPolicy` 解释的开放结果 code
     * @param data 协议已经转换为结构化值的回执数据
     * @param sourceAcknowledgementId 设备提供的稳定回执标识；协议没有该字段时为空
     * @param acknowledgedAt 设备提供的发生时间；协议没有可靠时钟时为空
     */
    public DecodedMqttAcknowledgement(
            long messageId,
            @NotNull String ackCode,
            @NotNull Map<String, Object> data,
            @Nullable String sourceAcknowledgementId,
            @Nullable Instant acknowledgedAt) {
        if (messageId <= 0)
            throw new IllegalArgumentException("ACK messageId 必须为正数");
        this.messageId = messageId;
        this.ackCode = TextUtils.requireText(ackCode, "ACK code");
        this.data = Map.copyOf(Objects.requireNonNull(data, "ACK 数据不能为空"));
        this.sourceAcknowledgementId = sourceAcknowledgementId == null
                ? null
                : TextUtils.requireText(sourceAcknowledgementId, "设备 ACK 标识");
        this.acknowledgedAt = acknowledgedAt;
    }

    /** @return 关联的标准下行 messageId */
    public long messageId() {
        return messageId;
    }

    /** @return 交给 ACK 策略解释的开放结果 code */
    public @NotNull String ackCode() {
        return ackCode;
    }

    /** @return 不可修改的结构化回执数据 */
    public @NotNull @Unmodifiable Map<String, Object> data() {
        return data;
    }

    /** @return 设备侧稳定回执标识；厂商协议没有该字段时为空 */
    public @NotNull Optional<String> sourceAcknowledgementId() {
        return Optional.ofNullable(sourceAcknowledgementId);
    }

    /** @return 设备记录的 ACK 发生时间；设备没有可靠时钟时为空 */
    public @NotNull Optional<Instant> acknowledgedAt() {
        return Optional.ofNullable(acknowledgedAt);
    }
}
