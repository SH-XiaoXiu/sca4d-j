package com.sca4d.iot.api.query;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 保存一个 reported 字段组当前已经接受的值和观测时间。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class ReportedStateSnapshot {

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull Map<String, Object> values;

    private final @NotNull String groupCode;

    private final long version;

    /** 创建 reported 字段组查询快照。 */
    public ReportedStateSnapshot(
            @NotNull String groupCode,
            @NotNull Map<String, Object> values,
            long version,
            @NotNull Instant observedAt,
            @NotNull Instant receivedAt) {
        if (version <= 0) {
            throw new IllegalArgumentException("reported version 必须为正数");
        }
        this.groupCode = TextUtils.requireText(groupCode, "reported 字段组 code");
        this.values = Map.copyOf(Objects.requireNonNull(values, "reported 值不能为空"));
        this.version = version;
        this.observedAt = Objects.requireNonNull(observedAt, "reported 观测时间不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "reported 接收时间不能为空");
    }

    /** @return 字段组 code */
    public @NotNull String groupCode() {
        return groupCode;
    }

    /** @return 当前不可修改字段值 */
    public @NotNull @Unmodifiable Map<String, Object> values() {
        return values;
    }

    /** @return 当前投影 version */
    public long version() {
        return version;
    }

    /** @return 设备或平台观测时间 */
    public @NotNull Instant observedAt() {
        return observedAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
