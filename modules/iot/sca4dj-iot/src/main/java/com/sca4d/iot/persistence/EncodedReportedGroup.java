package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 映射一个 reported 字段组最后接受的顺序和状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EncodedReportedGroup {

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull String contentHash;

    private final @NotNull String orderScope;

    private final @NotNull String orderValue;

    private final @NotNull String stateJson;

    private final long version;

    public EncodedReportedGroup(
            @NotNull String orderScope,
            @NotNull String orderValue,
            @NotNull String contentHash,
            @NotNull String stateJson,
            long version,
            @NotNull Instant observedAt,
            @NotNull Instant receivedAt) {
        this.orderScope = orderScope;
        this.orderValue = orderValue;
        this.contentHash = contentHash;
        this.stateJson = stateJson;
        this.version = version;
        this.observedAt = observedAt;
        this.receivedAt = receivedAt;
    }

    /** @return 顺序比较范围 */
    public @NotNull String orderScope() {
        return orderScope;
    }

    /** @return 该范围内规范可比较的顺序值 */
    public @NotNull String orderValue() {
        return orderValue;
    }

    /** @return 规范状态摘要 */
    public @NotNull String contentHash() {
        return contentHash;
    }

    /** @return latest 状态 JSON */
    public @NotNull String stateJson() {
        return stateJson;
    }

    /** @return 当前投影 version */
    public long version() {
        return version;
    }

    /** @return 设备或协议观测时间 */
    public @NotNull Instant observedAt() {
        return observedAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
