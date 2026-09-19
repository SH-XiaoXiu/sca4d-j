package com.sca4d.iot.api.state;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保存协议 Pipeline 输出的一次 reported 字段组更新。 */
public final class ReportedStateUpdate {

    private final @NotNull String groupCode;

    private final @NotNull ReportedMergeMode mergeMode;

    private final @NotNull Set<String> coveredProperties;

    private final @NotNull Map<String, Object> values;

    private final @Nullable String orderScope;

    private final @Nullable String orderValue;

    private final @NotNull Instant observedAt;

    /** 创建具有明确覆盖范围和可选设备顺序的 reported 更新。 */
    @SuppressWarnings("ParameterNumber")
    public ReportedStateUpdate(
            @NotNull String groupCode,
            @NotNull ReportedMergeMode mergeMode,
            @NotNull Set<String> coveredProperties,
            @NotNull Map<String, ?> values,
            @Nullable String orderScope,
            @Nullable String orderValue,
            @NotNull Instant observedAt) {
        this.groupCode = TextUtils.requireText(groupCode, "reported group code");
        this.mergeMode = Objects.requireNonNull(mergeMode, "reported merge mode 不能为空");
        this.coveredProperties = Set.copyOf(Objects.requireNonNull(coveredProperties, "reported 覆盖范围不能为空"));
        this.values = Map.copyOf(Objects.requireNonNull(values, "reported values 不能为空"));
        this.orderScope = orderScope;
        this.orderValue = orderValue;
        this.observedAt = Objects.requireNonNull(observedAt, "reported observedAt 不能为空");
        if (this.coveredProperties.isEmpty()
                || !this.coveredProperties.containsAll(this.values.keySet())
                || (orderScope == null) != (orderValue == null)) {
            throw new IllegalArgumentException("reported 覆盖范围、值或顺序字段不合法");
        }
        if (orderScope != null) {
            TextUtils.requireText(orderScope, "reported order scope");
            TextUtils.requireText(orderValue, "reported order value");
        }
    }

    /** @return 字段组稳定 code */
    public @NotNull String groupCode() {
        return groupCode;
    }

    /** @return snapshot 或 patch */
    public @NotNull ReportedMergeMode mergeMode() {
        return mergeMode;
    }

    /** @return 该组声明覆盖的属性 code */
    public @NotNull @Unmodifiable Set<String> coveredProperties() {
        return coveredProperties;
    }

    /** @return 本次实际携带的值 */
    public @NotNull @Unmodifiable Map<String, Object> values() {
        return values;
    }

    /** @return 协议顺序范围；为空时由平台分配观测顺序 */
    public @Nullable String orderScope() {
        return orderScope;
    }

    /** @return 规范可比较顺序值；为空时由平台分配 */
    public @Nullable String orderValue() {
        return orderValue;
    }

    /** @return 设备或协议提供的观测时间 */
    public @NotNull Instant observedAt() {
        return observedAt;
    }
}
