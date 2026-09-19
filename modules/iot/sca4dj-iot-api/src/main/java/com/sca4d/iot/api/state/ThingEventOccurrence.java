package com.sca4d.iot.api.state;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 保存协议 Pipeline 输出的一次离散 ThingEvent 事实。 */
public final class ThingEventOccurrence {

    private final @NotNull String eventCode;

    private final @Nullable String sourceEventId;

    private final @NotNull Map<String, Object> outputs;

    private final @Nullable Instant occurredAt;

    /** @param eventCode 事件 code @param sourceEventId 设备稳定事实键 @param outputs 完整输出 @param occurredAt 设备发生时间 */
    public ThingEventOccurrence(
            @NotNull String eventCode,
            @Nullable String sourceEventId,
            @NotNull Map<String, ?> outputs,
            @Nullable Instant occurredAt) {
        this.eventCode = TextUtils.requireText(eventCode, "ThingEvent code");
        this.sourceEventId = optionalSourceEventId(sourceEventId);
        this.outputs = Map.copyOf(Objects.requireNonNull(outputs, "ThingEvent outputs 不能为空"));
        this.occurredAt = occurredAt;
    }

    /** @return event code */
    public @NotNull String eventCode() {
        return eventCode;
    }

    /** @return 可稳定去重的设备事实键 */
    public @NotNull Optional<String> sourceEventId() {
        return Optional.ofNullable(sourceEventId);
    }

    /** @return 事件输出 */
    public @NotNull @Unmodifiable Map<String, Object> outputs() {
        return outputs;
    }

    /** @return 可选设备发生时间 */
    public @NotNull Optional<Instant> occurredAt() {
        return Optional.ofNullable(occurredAt);
    }

    /** 校验可写入事件历史的来源事实标识。 */
    private static @Nullable String optionalSourceEventId(@Nullable String sourceEventId) {
        if (sourceEventId == null) {
            return null;
        }
        String checked = TextUtils.requireText(sourceEventId, "sourceEventId");
        if (checked.length() > 256) {
            throw new IllegalArgumentException("sourceEventId 不能超过 256 个字符");
        }
        return checked;
    }
}
