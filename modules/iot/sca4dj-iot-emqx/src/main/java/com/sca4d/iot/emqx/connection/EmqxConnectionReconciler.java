package com.sca4d.iot.emqx.connection;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.connection.Connection;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.connection.ConnectionProtocol;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceConnectionResolver;
import com.sca4d.iot.emqx.authentication.MqttAuthorizationService;
import com.sca4d.iot.emqx.management.EmqxManagementClient;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** 用单 Client 当前状态实时刷新连接，并在 watchdog 分布式锁内使用完整快照修复遗漏事件。 */
public class EmqxConnectionReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(EmqxConnectionReconciler.class);

    private static final LockKey RECONCILIATION_LOCK = new LockKey(TreeCode.of("IOT:EMQX_RECONCILIATION"));

    private static final Duration LOCK_WAIT = Duration.ZERO;

    private final @NotNull MqttAuthorizationService authorization;

    private final @NotNull EmqxConnectionRepository emqxConnections;

    private final @NotNull EmqxManagementClient management;

    private final @Nullable ConnectionRegistry commonConnections;

    private final @NotNull Clock clock;

    private final @NotNull DeviceConnectionReceiver connections;

    private final @NotNull DeviceConnectionResolver devices;

    private final @NotNull DistributedLock distributedLock;

    /** 创建集群中单执行者的 EMQX 连接对账服务。 */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "ConnectionRegistry 是注入的共享协作接口，对账服务不拥有或修改其内部表示")
    public EmqxConnectionReconciler(
            @NotNull EmqxManagementClient management,
            @NotNull DeviceConnectionResolver devices,
            @NotNull DeviceConnectionReceiver connections,
            @NotNull EmqxConnectionRepository emqxConnections,
            @NotNull MqttAuthorizationService authorization,
            @NotNull DistributedLock distributedLock,
            @NotNull Clock clock,
            @Nullable ConnectionRegistry commonConnections) {
        this.management = Objects.requireNonNull(management, "EMQX ManagementClient 不能为空");
        this.devices = Objects.requireNonNull(devices, "Device 连接身份解析入口不能为空");
        this.connections = Objects.requireNonNull(connections, "Device 连接事件接收入口不能为空");
        this.emqxConnections = Objects.requireNonNull(emqxConnections, "EMQX Connection Repository 不能为空");
        this.authorization = Objects.requireNonNull(authorization, "MQTT 授权服务不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "EMQX 对账分布式锁不能为空");
        this.clock = Objects.requireNonNull(clock, "EMQX 对账时钟不能为空");
        this.commonConnections = commonConnections;
    }

    /**
     * 取得租约时对账一次，其他实例同时调用直接返回 {@code false}。
     *
     * @return 当前实例是否取得并执行对账
     */
    public boolean runOnce() {
        AtomicBoolean started = new AtomicBoolean();
        try {
            return distributedLock.execute(RECONCILIATION_LOCK, LOCK_WAIT, () -> {
                started.set(true);
                reconcile();
                return true;
            });
        } catch (BusinessException exception) {
            if (!started.get()) {
                return false;
            }
            throw exception;
        }
    }

    /** 在 watchdog 持有对账锁期间读取一次 Broker 快照并修复连接投影。 */
    private void reconcile() {
        // 读取同一时点的 Broker 快照，并把全部 Client ID 一次批量解析成当前 Device。
        Instant observedAt = clock.instant();
        List<EmqxClientConnection> brokerClients = management.findClients();
        LinkedHashMap<String, DeviceConnectionBinding> bindings = new LinkedHashMap<>();
        for (EmqxClientConnection managed : brokerClients) {
            bindings.put(managed.clientId(), MqttDeviceConnection.clientId(managed.clientId()));
        }
        Map<DeviceConnectionBinding, Device> resolvedDevices = devices
                .findDevices(bindings.values());

        // 校验连接属性和当前授权，只保留能够形成标准 DeviceConnection 的 Broker 连接。
        List<EmqxClientConnection> deviceClients = new ArrayList<>();
        List<DeviceConnection> standardConnections = new ArrayList<>();
        for (EmqxClientConnection managed : brokerClients) {
            Device device = resolvedDevices.get(bindings.get(managed.clientId()));
            DeviceConnection connection = deviceConnection(managed, device);
            if (connection != null) {
                deviceClients.add(managed);
                standardConnections.add(connection);
            }
        }

        // 先修复 EMQX 专用投影，再以仍指向当前连接代次的结果更新通用连接投影。
        emqxConnections.reconciled(deviceClients, observedAt);
        Map<String, DeviceConnectionId> projectedConnectionIds = emqxConnections.findConnectionIds(
                deviceClients.stream().map(EmqxClientConnection::clientId).toList());
        List<EmqxClientConnection> currentClients = new ArrayList<>();
        List<DeviceConnection> currentConnections = new ArrayList<>();
        for (int index = 0; index < deviceClients.size(); index++) {
            EmqxClientConnection managed = deviceClients.get(index);
            DeviceConnection connection = standardConnections.get(index);
            if (connection.id().equals(projectedConnectionIds.get(managed.clientId()))) {
                currentClients.add(managed);
                currentConnections.add(connection);
                registerCommon(connection, managed.brokerNode());
            }
        }
        connections.reconciled(DeviceConnectionProtocol.MQTT, currentConnections, observedAt);

        // 通用投影写入后再次确认反向索引，清理对账期间被更新事件替换的旧连接代次。
        Map<String, DeviceConnectionId> confirmedConnectionIds = emqxConnections.findConnectionIds(
                currentClients.stream().map(EmqxClientConnection::clientId).toList());
        for (int index = 0; index < currentClients.size(); index++) {
            EmqxClientConnection managed = currentClients.get(index);
            DeviceConnection connection = currentConnections.get(index);
            if (!connection.id().equals(confirmedConnectionIds.get(managed.clientId()))) {
                removeConnection(connection.id());
            }
        }
        LOG.info(
                "EMQX Connection 对账已完成 | brokerClients={} deviceConnections={}",
                brokerClients.size(),
                currentConnections.size());
    }

    /** 取得集群分布式锁后使用 EMQX 当前连接列表修正 Connection 投影。 */
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public void execute() {
        runOnce();
    }

    /**
     * 查询一个 MQTT Client 的 Broker 当前状态并立即刷新连接投影。
     *
     * <p>方法只处理已登记 Device 或已有 EMQX 投影的 Client ID。查询时间早于之后的连接事件时，
     * Redis 观察时间会阻止旧结果覆盖新连接代次。</p>
     *
     * @param clientId `$SYS` 连接或断线事件中的 MQTT Client ID
     */
    public void refreshClient(@NotNull String clientId) {
        String checkedClientId = Objects.requireNonNull(clientId, "MQTT Client ID 不能为空");
        Device device = devices.findDevice(MqttDeviceConnection.clientId(checkedClientId)).orElse(null);
        if (device == null && emqxConnections.findConnectionId(checkedClientId).isEmpty()) {
            return;
        }
        // 查询时间早于其他事件时，后续写入会按 observedAt 与当前连接代次比较，旧结果不会覆盖新连接。
        Instant observedAt = clock.instant();
        Optional<EmqxClientConnection> current = management.findClient(checkedClientId);
        // Broker 上已没有该 Client 时，删除 EMQX 投影和通用投影中的当前连接代次，按离线处理。
        if (current.isEmpty()) {
            disconnectCurrent(checkedClientId, observedAt);
            return;
        }
        EmqxClientConnection managed = current.orElseThrow();
        DeviceConnection connection = deviceConnection(managed, device);
        if (connection == null) {
            disconnectCurrent(checkedClientId, observedAt);
            return;
        }
        // 先写入通用连接投影和公共 Registry，再写 EMQX 专用投影；写入后若反向索引已指向更新的连接代次，则删除本次写入的通用投影。
        connections.connected(connection);
        registerCommon(connection, managed.brokerNode());
        emqxConnections.connected(managed, observedAt);
        if (!emqxConnections.isCurrent(checkedClientId, connection.id())) {
            removeConnection(connection.id());
            return;
        }
        LOG.info(
                "EMQX Device Connection 当前状态已刷新 | deviceId={} clientId={} connectionId={}",
                connection.deviceId(),
                checkedClientId,
                connection.id().value());
    }

    /** 把带有 Sca4d-J 认证属性的 Management API 连接转换为通用连接信息。 */
    private @Nullable DeviceConnection deviceConnection(EmqxClientConnection managed, @Nullable Device device) {
        Map<String, String> attributes = managed.clientAttributes();
        // 没有 device_id 属性且未登记 Device 的 Client 不属于设备连接，直接忽略，不断开 Broker 连接。
        if (!attributes.containsKey("device_id") && device == null) {
            return null;
        }
        // 连接属性与登记 Device 不一致时断开 Broker 连接并返回 null，使该 Client 不进入任何连接投影。
        if (device == null
                || !Long.toString(device.id()).equals(attributes.get("device_id"))
                || !device.product().code().equals(attributes.get("product_code"))
                || !device.product().source().name().equals(attributes.get("product_source"))
                || !device.protocolCode().equals(attributes.get("protocol_code"))) {
            management.disconnect(managed.clientId());
            LOG.warn(
                    "EMQX 当前连接无法映射到登记 Device 并已断开 | clientId={} node={}",
                    managed.clientId(),
                    managed.brokerNode());
            return null;
        }
        // 授权验证异常时无法确认连接是否仍被允许，按不安全处理：断开连接并记录 ERROR，不把该连接写入投影。
        boolean currentAuthorization;
        try {
            currentAuthorization = authorization.isCurrent(device, managed.clientId(), attributes);
        } catch (RuntimeException exception) {
            management.disconnect(managed.clientId());
            LOG.error(
                    "EMQX 当前 Device 连接授权无法验证并已断开 | deviceId={} clientId={} node={}",
                    device.id(),
                    managed.clientId(),
                    managed.brokerNode(),
                    exception);
            return null;
        }
        if (!currentAuthorization) {
            management.disconnect(managed.clientId());
            LOG.warn(
                    "EMQX 当前 Device 连接授权规则已过期并断开 | deviceId={} clientId={} node={}",
                    device.id(),
                    managed.clientId(),
                    managed.brokerNode());
            return null;
        }
        String methodCode = attributes.get("access_method");
        if (methodCode == null || methodCode.isBlank()) {
            management.disconnect(managed.clientId());
            LOG.warn(
                    "EMQX 当前 Device 连接缺少接入方式并已断开 | deviceId={} clientId={}",
                    device.id(),
                    managed.clientId());
            return null;
        }
        DeviceAccessMethod accessMethod;
        try {
            accessMethod = DeviceAccessMethod.parse(methodCode);
        } catch (IllegalArgumentException exception) {
            management.disconnect(managed.clientId());
            LOG.warn(
                    "EMQX 当前 Device 连接接入方式无效并已断开 | deviceId={} clientId={} method={}",
                    device.id(),
                    managed.clientId(),
                    methodCode);
            return null;
        }
        String sessionValue = attributes.get("device_session_id");
        boolean hasSession = sessionValue != null;
        boolean sessionMismatch = accessMethod.usesSession() != hasSession;
        if (!device.accessMethods().contains(accessMethod) || sessionMismatch) {
            management.disconnect(managed.clientId());
            LOG.warn(
                    "EMQX 当前 Device 连接属性不一致并已断开 | deviceId={} clientId={} method={}",
                    device.id(),
                    managed.clientId(),
                    methodCode);
            return null;
        }
        return new DeviceConnection(
                managed.connectionId(),
                device.id(),
                DeviceConnectionProtocol.MQTT,
                sessionValue == null ? null : new SessionId(sessionValue),
                accessMethod,
                managed.connectedAt());
    }

    /** 把已确认离线的 Client 当前连接代次从三份共享投影中删除。 */
    private void disconnectCurrent(@NotNull String clientId, @NotNull Instant observedAt) {
        Optional<DeviceConnectionId> removed = emqxConnections.disconnected(clientId, observedAt);
        if (removed.isEmpty()) {
            return;
        }
        DeviceConnectionId connectionId = removed.orElseThrow();
        boolean changed = connections.disconnected(DeviceConnectionProtocol.MQTT, connectionId);
        if (commonConnections != null) {
            commonConnections.remove(new ConnectionId(connectionId.value()));
        }
        LOG.info(
                "EMQX Device Connection 当前状态已刷新为离线 | clientId={} connectionId={} changed={}",
                clientId,
                connectionId.value(),
                changed);
    }

    /** 把 Device 连接登记到可选的公共 App ConnectionRegistry。 */
    private void registerCommon(@NotNull DeviceConnection connection, @NotNull String brokerNode) {
        if (commonConnections == null) {
            return;
        }
        commonConnections.register(
                new Connection(
                        new ConnectionId(connection.id().value()),
                        new ConnectionProtocol("mqtt"),
                        connection.sessionId().orElse(null),
                        connection.connectedAt(),
                        connection.connectedAt(),
                        brokerNode));
    }

    /** 删除协议无关和公共 App Connection 投影中的指定连接代次。 */
    private void removeConnection(@NotNull DeviceConnectionId connectionId) {
        connections.disconnected(DeviceConnectionProtocol.MQTT, connectionId);
        if (commonConnections != null) {
            commonConnections.remove(new ConnectionId(connectionId.value()));
        }
    }

}
