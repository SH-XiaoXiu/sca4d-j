package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 映射一个 Device 当前绑定下可直接展示的 reported 字段组。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EncodedReportedGroupSnapshot {

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull String groupCode;

    private final @NotNull String stateJson;

    private final long deviceId;

    private final long version;

    public EncodedReportedGroupSnapshot(
            long deviceId,
            @NotNull String groupCode,
            @NotNull String stateJson,
            long version,
            @NotNull Instant observedAt,
            @NotNull Instant receivedAt) {
        this.deviceId = deviceId;
        this.groupCode = groupCode;
        this.stateJson = stateJson;
        this.version = version;
        this.observedAt = observedAt;
        this.receivedAt = receivedAt;
    }

    /** @return 字段组所属 Device */
    public long deviceId() {
        return deviceId;
    }

    /** @return 字段组 code */
    public @NotNull String groupCode() {
        return groupCode;
    }

    /** @return 状态 JSON */
    public @NotNull String stateJson() {
        return stateJson;
    }

    /** @return 投影 version */
    public long version() {
        return version;
    }

    /** @return 观测时间 */
    public @NotNull Instant observedAt() {
        return observedAt;
    }

    /** @return 接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
