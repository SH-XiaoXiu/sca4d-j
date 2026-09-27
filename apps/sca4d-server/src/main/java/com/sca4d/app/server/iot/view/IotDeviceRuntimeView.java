package com.sca4d.app.server.iot.view;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.app.server.iot.IotDeviceRuntimeDetails;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.query.DesiredStateSnapshot;
import com.sca4d.iot.api.query.DeviceRuntimeSnapshot;
import com.sca4d.iot.api.query.ReportedStateSnapshot;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 返回 Device 当前连接、reported 和 desired 三类可重新读取的运行投影。 */
@SuppressWarnings("unused")
public final class IotDeviceRuntimeView {

    private final @NotNull @Unmodifiable List<Connection> connections;

    private final @NotNull @Unmodifiable List<Reported> reported;

    private final @NotNull @Unmodifiable List<Desired> desired;

    private final boolean online;

    /** 把模块运行态快照转换成 HTTP 字段。 */
    private IotDeviceRuntimeView(@NotNull IotDeviceRuntimeDetails details) {
        IotDeviceRuntimeDetails checkedDetails = Objects.requireNonNull(details, "Device 运行态应用结果不能为空");
        DeviceRuntimeSnapshot snapshot = checkedDetails.snapshot();
        DeviceRuntimeSnapshot checked = Objects.requireNonNull(snapshot, "Device 运行态快照不能为空");
        this.connections = checked.connections().stream().map(Connection::new).toList();
        this.reported = checked.reported().stream().map(Reported::new).toList();
        this.desired = checked.desired().stream().map(Desired::new).toList();
        this.online = checkedDetails.online();
    }

    /** @return 指定 Device 的当前运行投影 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotDeviceRuntimeView from(@NotNull IotDeviceRuntimeDetails details) {
        return new IotDeviceRuntimeView(details);
    }

    /** @return 当前仍有效的连接信息 */
    public @NotNull @Unmodifiable List<Connection> getConnections() {
        return List.copyOf(connections);
    }

    /** @return 当前应用在线策略的判断结果 */
    public boolean isOnline() {
        return online;
    }

    /** @return 当前绑定的 reported 字段组 */
    public @NotNull @Unmodifiable List<Reported> getReported() {
        return List.copyOf(reported);
    }

    /** @return 当前绑定的 desired 属性 */
    public @NotNull @Unmodifiable List<Desired> getDesired() {
        return List.copyOf(desired);
    }

    /** 返回一条当前 Device 连接信息。 */
    public static final class Connection {

        private final @NotNull String connectionId;

        private final @NotNull String protocol;

        private final @Nullable String sessionId;

        private final @NotNull String accessMethodCode;

        private final @NotNull Instant connectedAt;

        /** 从协议无关的连接信息创建 HTTP 视图。 */
        private Connection(@NotNull DeviceConnection connection) {
            this.connectionId = connection.id().value();
            this.protocol = connection.protocol().code();
            this.sessionId = connection.sessionId().map(SessionId::value).orElse(null);
            this.accessMethodCode = connection.accessMethod().code().format(COLON);
            this.connectedAt = connection.connectedAt();
        }

        /** @return 接入实现提供的连接代次标识 */
        public @NotNull String getConnectionId() {
            return connectionId;
        }

        /** @return 连接协议 code */
        public @NotNull String getProtocol() {
            return protocol;
        }

        /** @return 可选 backing Session ID */
        public @Nullable String getSessionId() {
            return sessionId;
        }

        /** @return 本次连接使用的 Device 接入方式 code */
        public @NotNull String getAccessMethodCode() {
            return accessMethodCode;
        }

        /** @return 接入实现报告的连接时间 */
        public @NotNull Instant getConnectedAt() {
            return connectedAt;
        }
    }

    /** 返回一个 reported 字段组当前快照。 */
    public static final class Reported {

        private final @NotNull String groupCode;

        private final @NotNull @Unmodifiable Map<String, Object> values;

        private final long version;

        private final @NotNull Instant observedAt;

        private final @NotNull Instant receivedAt;

        /** 从模块 reported 快照创建 HTTP 视图。 */
        private Reported(@NotNull ReportedStateSnapshot snapshot) {
            this.groupCode = snapshot.groupCode();
            this.values = snapshot.values();
            this.version = snapshot.version();
            this.observedAt = snapshot.observedAt();
            this.receivedAt = snapshot.receivedAt();
        }

        /** @return 字段组 code */
        public @NotNull String getGroupCode() {
            return groupCode;
        }

        /** @return 当前字段值 */
        public @NotNull @Unmodifiable Map<String, Object> getValues() {
            return Map.copyOf(values);
        }

        /** @return 当前字段组 version */
        public long getVersion() {
            return version;
        }

        /** @return 设备或平台观测时间 */
        public @NotNull Instant getObservedAt() {
            return observedAt;
        }

        /** @return 平台接收时间 */
        public @NotNull Instant getReceivedAt() {
            return receivedAt;
        }
    }

    /** 返回一个 desired 属性当前快照。 */
    public static final class Desired {

        private final @NotNull String propertyCode;

        private final long desiredSequence;

        private final @NotNull Object value;

        private final @Nullable Instant convergedAt;

        /** 从模块 desired 快照创建 HTTP 视图。 */
        private Desired(@NotNull DesiredStateSnapshot snapshot) {
            this.propertyCode = snapshot.propertyCode();
            this.desiredSequence = snapshot.desiredSequence();
            this.value = snapshot.value();
            this.convergedAt = snapshot.convergedAt().orElse(null);
        }

        /** @return 属性 code */
        public @NotNull String getPropertyCode() {
            return propertyCode;
        }

        /** @return 最近修改序号 */
        public long getDesiredSequence() {
            return desiredSequence;
        }

        /** @return 当前持续期望值 */
        public @NotNull Object getValue() {
            return value;
        }

        /** @return 最近收敛时间；尚未收敛时为空 */
        public @Nullable Instant getConvergedAt() {
            return convergedAt;
        }
    }
}
