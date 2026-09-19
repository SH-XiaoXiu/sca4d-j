package com.sca4d.iot.state;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 保存 Repository 锁定后返回给 Service 的 reported group 当前投影。 */
public final class ReportedGroupState {

    private final @NotNull String orderScope;

    private final @NotNull String orderValue;

    private final @NotNull String contentHash;

    private final @NotNull Map<String, Object> values;

    private final long version;

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    /** 创建完整 current projection。 */
    @SuppressWarnings("ParameterNumber")
    public ReportedGroupState(
            @NotNull String orderScope,
            @NotNull String orderValue,
            @NotNull String contentHash,
            @NotNull Map<String, Object> values,
            long version,
            @NotNull Instant observedAt,
            @NotNull Instant receivedAt) {
        this.orderScope = Objects.requireNonNull(orderScope, "order scope 不能为空");
        this.orderValue = Objects.requireNonNull(orderValue, "order value 不能为空");
        this.contentHash = Objects.requireNonNull(contentHash, "content hash 不能为空");
        this.values = Map.copyOf(Objects.requireNonNull(values, "reported values 不能为空"));
        this.version = version;
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt 不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt 不能为空");
    }

    /** @return 顺序范围 */
    public @NotNull String orderScope() {
        return orderScope;
    }

    /** @return 规范顺序值 */
    public @NotNull String orderValue() {
        return orderValue;
    }

    /** @return 规范内容摘要 */
    public @NotNull String contentHash() {
        return contentHash;
    }

    /** @return latest values */
    public @NotNull Map<String, Object> values() {
        return values;
    }

    /** @return latest version */
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
