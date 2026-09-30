package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 映射一条设备显式回执或 reported 推导回执。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EncodedDeviceAcknowledgement {

    private final @NotNull Instant acknowledgedAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull String ackCode;

    private final @NotNull String dataJson;

    private final @NotNull String source;

    private final @Nullable String sourceAcknowledgementId;

    private final long messageId;

    @SuppressWarnings("ParameterNumber")
    public EncodedDeviceAcknowledgement(
            long messageId,
            @NotNull String ackCode,
            @NotNull String dataJson,
            @NotNull String source,
            @Nullable String sourceAcknowledgementId,
            @NotNull Instant acknowledgedAt,
            @NotNull Instant receivedAt) {
        this.messageId = messageId;
        this.ackCode = ackCode;
        this.dataJson = dataJson;
        this.source = source;
        this.sourceAcknowledgementId = sourceAcknowledgementId;
        this.acknowledgedAt = acknowledgedAt;
        this.receivedAt = receivedAt;
    }

    /** @return 下行 messageId */
    public long messageId() {
        return messageId;
    }

    /** @return 回执 code */
    public @NotNull String ackCode() {
        return ackCode;
    }

    /** @return 回执数据 JSON */
    public @NotNull String dataJson() {
        return dataJson;
    }

    /** @return 回执来源 */
    public @NotNull String source() {
        return source;
    }

    /** @return 可选来源回执标识 */
    public @Nullable String sourceAcknowledgementId() {
        return sourceAcknowledgementId;
    }

    /** @return 回执表示的发生时间 */
    public @NotNull Instant acknowledgedAt() {
        return acknowledgedAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
