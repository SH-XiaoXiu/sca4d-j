package com.sca4d.iot.api.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 保存显式回执或 reported 推导结果共用的业务确认事实。 */
public class DeviceAcknowledgement extends Message {

    /** 业务确认消息的协议版本，与 `protocolVersion()` 返回值一致。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 业务确认消息的稳定类型编码，用于通用 Messaging 路由。 */
    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/ACK/PROCESS");

    private final @NotNull Instant acknowledgedAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull Map<String, Object> data;

    private final @NotNull String ackCode;

    private final @NotNull String source;

    private final @Nullable String sourceAcknowledgementId;

    private final long messageId;

    /** 创建一条由设备接入实现解析、尚未分配数据库标识的业务确认。 */
    @JsonCreator
    public DeviceAcknowledgement(
            @JsonProperty("messageId") long messageId,
            @JsonProperty("ackCode") @NotNull String ackCode,
            @JsonProperty("data") @NotNull Map<String, Object> data,
            @JsonProperty("source") @NotNull String source,
            @JsonProperty("sourceAcknowledgementId") @Nullable String sourceAcknowledgementId,
            @JsonProperty("acknowledgedAt") @NotNull Instant acknowledgedAt,
            @JsonProperty("receivedAt") @NotNull Instant receivedAt) {
        if (messageId <= 0) {
            throw new IllegalArgumentException("ACK messageId 必须为正数");
        }
        this.messageId = messageId;
        this.ackCode = limitedText(ackCode, "ackCode", 128);
        this.data = Map.copyOf(Objects.requireNonNull(data, "ACK 数据不能为空"));
        this.source = limitedText(source, "ACK 来源", 32);
        this.sourceAcknowledgementId = sourceAcknowledgementId == null
                ? null
                : limitedText(sourceAcknowledgementId, "ACK 来源标识", 256);
        this.acknowledgedAt = Objects.requireNonNull(acknowledgedAt, "ACK 发生时间不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "ACK 接收时间不能为空");
    }

    /** @return 关联下行 messageId */
    public long messageId() {
        return messageId;
    }

    /** @return 协议或推导器产生的开放 ackCode */
    public @NotNull String ackCode() {
        return ackCode;
    }

    /** @return 不可修改结构化数据 */
    public @NotNull @Unmodifiable Map<String, Object> data() {
        return data;
    }

    /** @return DEVICE 或 REPORTED 等来源 code */
    public @NotNull String source() {
        return source;
    }

    /** @return 可选设备侧稳定回执标识 */
    public @Nullable String sourceAcknowledgementId() {
        return sourceAcknowledgementId;
    }

    /** @return ACK 表示的发生时间 */
    public @NotNull Instant acknowledgedAt() {
        return acknowledgedAt;
    }

    /** @return Sca4d-J 接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }

    /** 校验与主 PostgreSQL ACK 快照列一致的文本长度。 */
    private static @NotNull String limitedText(
            @NotNull String value,
            @NotNull String name,
            int maxLength) {
        String checked = TextUtils.requireText(value, name);
        if (checked.length() > maxLength) {
            throw new IllegalArgumentException(name + "不能超过 " + maxLength + " 个字符");
        }
        return checked;
    }
}
