package com.sca4d.iot.api.message;

import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.api.state.ThingEventOccurrence;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 保存 DeviceProtocol 解码后的一次标准 reported、acknowledgement 或两者兼有的上行结果。 */
public final class DeviceUplink {

    /** 标准上行结果类型。 */
    public enum Kind {
        /** reported state 和同一报文事件。 */
        REPORTED,
        /** 设备显式业务回执。 */
        ACKNOWLEDGEMENT,
        /** 同一设备报文既上报状态又确认一条下行消息。 */
        REPORTED_AND_ACKNOWLEDGEMENT
    }

    private final @NotNull Kind kind;

    private final @Nullable ReportedStateUpdate reportedState;

    private final @NotNull List<ThingEventOccurrence> events;

    private final @Nullable DeviceAcknowledgement acknowledgement;

    private DeviceUplink(
            @NotNull Kind kind,
            @Nullable ReportedStateUpdate reportedState,
            @NotNull List<ThingEventOccurrence> events,
            @Nullable DeviceAcknowledgement acknowledgement) {
        this.kind = kind;
        this.reportedState = reportedState;
        this.events = events;
        this.acknowledgement = acknowledgement;
    }

    /** 创建 reported state 和同一报文产生的事件。 */
    @CheckReturnValue
    public static @NotNull DeviceUplink reported(
            @NotNull ReportedStateUpdate update,
            @NotNull List<ThingEventOccurrence> events) {
        return new DeviceUplink(
                Kind.REPORTED,
                Objects.requireNonNull(update, "reported state 不能为空"),
                List.copyOf(Objects.requireNonNull(events, "ThingEvent 集合不能为空")),
                null);
    }

    /** 创建设备显式业务回执。 */
    @CheckReturnValue
    public static @NotNull DeviceUplink acknowledged(@NotNull DeviceAcknowledgement acknowledgement) {
        return new DeviceUplink(
                Kind.ACKNOWLEDGEMENT,
                null,
                List.of(),
                Objects.requireNonNull(acknowledgement, "Device acknowledgement 不能为空"));
    }

    /** 创建同一报文携带的 reported state、事件和设备显式业务回执。 */
    @CheckReturnValue
    public static @NotNull DeviceUplink reportedAndAcknowledged(
            @NotNull ReportedStateUpdate update,
            @NotNull List<ThingEventOccurrence> events,
            @NotNull DeviceAcknowledgement acknowledgement) {
        return new DeviceUplink(
                Kind.REPORTED_AND_ACKNOWLEDGEMENT,
                Objects.requireNonNull(update, "reported state 不能为空"),
                List.copyOf(Objects.requireNonNull(events, "ThingEvent 集合不能为空")),
                Objects.requireNonNull(acknowledgement, "Device acknowledgement 不能为空"));
    }

    /** @return reported、acknowledgement 或两者兼有 */
    public @NotNull Kind kind() {
        return kind;
    }

    /** @return reported 结果；仅 acknowledgement 时为空 */
    public @NotNull Optional<ReportedStateUpdate> reportedState() {
        return Optional.ofNullable(reportedState);
    }

    /** @return 与 reported 同一报文产生的事件；仅 acknowledgement 时为空集合 */
    public @NotNull @Unmodifiable List<ThingEventOccurrence> events() {
        return List.copyOf(events);
    }

    /** @return acknowledgement 结果；仅 reported 时为空 */
    public @NotNull Optional<DeviceAcknowledgement> acknowledgement() {
        return Optional.ofNullable(acknowledgement);
    }
}
