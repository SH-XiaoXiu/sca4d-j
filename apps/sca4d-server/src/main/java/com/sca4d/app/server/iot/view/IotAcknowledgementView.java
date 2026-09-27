package com.sca4d.app.server.iot.view;

import com.sca4d.iot.api.message.DeviceAcknowledgement;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 返回一条设备显式 ACK 或 reported 推导 ACK。 */
@SuppressWarnings("unused")
public class IotAcknowledgementView {

    private final long messageId;

    private final @NotNull String ackCode;

    private final @NotNull @Unmodifiable Map<String, Object> data;

    private final @NotNull String source;

    private final @Nullable String sourceAcknowledgementId;

    private final @NotNull Instant acknowledgedAt;

    private final @NotNull Instant receivedAt;

    /** 从模块 ACK 查询结果创建 HTTP 视图。 */
    private IotAcknowledgementView(@NotNull DeviceAcknowledgement acknowledgement) {
        this.messageId = acknowledgement.messageId();
        this.ackCode = acknowledgement.ackCode();
        this.data = acknowledgement.data();
        this.source = acknowledgement.source();
        this.sourceAcknowledgementId = acknowledgement.sourceAcknowledgementId();
        this.acknowledgedAt = acknowledgement.acknowledgedAt();
        this.receivedAt = acknowledgement.receivedAt();
    }

    /** @return ACK HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotAcknowledgementView from(@NotNull DeviceAcknowledgement acknowledgement) {
        return new IotAcknowledgementView(Objects.requireNonNull(acknowledgement, "Device ACK 不能为空"));
    }

    /** @return 关联下行 messageId */
    public long getMessageId() {
        return messageId;
    }

    /** @return 协议或推导器产生的 ACK code */
    public @NotNull String getAckCode() {
        return ackCode;
    }

    /** @return ACK 结构化数据 */
    public @NotNull @Unmodifiable Map<String, Object> getData() {
        return Map.copyOf(data);
    }

    /** @return DEVICE 或 REPORTED 等来源 code */
    public @NotNull String getSource() {
        return source;
    }

    /** @return 可选设备侧稳定 ACK 标识 */
    public @Nullable String getSourceAcknowledgementId() {
        return sourceAcknowledgementId;
    }

    /** @return ACK 表示的发生时间 */
    public @NotNull Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant getReceivedAt() {
        return receivedAt;
    }
}
