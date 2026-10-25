package com.sca4d.iot.emqx.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceConnectionResolver;
import com.sca4d.iot.emqx.authentication.EmqxAclRule;
import com.sca4d.iot.emqx.authentication.MqttAuthorizationPolicy;
import com.sca4d.iot.emqx.authentication.MqttAuthorizationService;
import com.sca4d.iot.emqx.management.EmqxManagementClient;
import com.sca4d.iot.emqx.protocol.MqttProtocolCatalog;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** 验证 EMQX 单 Client 刷新与完整连接对账。 */
class EmqxConnectionReconcilerTest {

    static {
        AppName.publish("SCA4D_TEST");
    }

    private static final Instant NOW = Instant.parse("2026-08-07T13:00:00Z");

    @Test
    @DisplayName("其他实例持有对账锁时不读取 Broker")
    void skipsReconciliationWhenLockIsUnavailable() {
        EmqxManagementClient management = mock(EmqxManagementClient.class);
        DistributedLock distributedLock = mock(DistributedLock.class);
        when(distributedLock.execute(any(), any(), any()))
                .thenThrow(new BusinessException(CoreErrorCode.CONFLICT));
        EmqxConnectionReconciler reconciler = new EmqxConnectionReconciler(
                management,
                mock(DeviceConnectionResolver.class),
                mock(DeviceConnectionReceiver.class),
                mock(EmqxConnectionRepository.class),
                authorization(),
                distributedLock,
                Clock.fixed(NOW, ZoneOffset.UTC),
                null);

        assertThat(reconciler.runOnce()).isFalse();

        verify(management, never()).findClients();
    }

    @Test
    @DisplayName("对账断开缺少当前 ACL 指纹的 Device 连接")
    void disconnectsConnectionWithoutCurrentAuthorization() {
        EmqxManagementClient management = mock(EmqxManagementClient.class);
        DeviceConnectionResolver devices = mock(DeviceConnectionResolver.class);
        DeviceConnectionReceiver connections = mock(DeviceConnectionReceiver.class);
        EmqxConnectionRepository emqxConnections = mock(EmqxConnectionRepository.class);
        DistributedLock distributedLock = mock(DistributedLock.class);
        when(distributedLock.execute(any(), any(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(2).get());

        Device device = new Device(
                71,
                ProductBinding.code("mqtt-debug"),
                "debug-key",
                null,
                "Debug Key",
                null,
                1,
                1,
                "mqtt-debug-v1",
                MqttDeviceConnection.clientId("dbg-71"),
                Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")));
        EmqxClientConnection managed = new EmqxClientConnection(
                "dbg-71",
                "emqx@node",
                NOW.minusSeconds(30),
                Map.of(
                        "device_id",
                        "71",
                        "product_code",
                        "mqtt-debug",
                        "product_source",
                        "CODE",
                        "protocol_code",
                        "mqtt-debug-v1",
                        "access_method",
                        "ACCOUNT:AUTHENTICATION/SHARED_SECRET"));
        when(management.findClients()).thenReturn(List.of(managed));
        when(devices.findDevices(any()))
                .thenReturn(Map.of(MqttDeviceConnection.clientId("dbg-71"), device));

        MqttAuthorizationPolicy policy = new MqttAuthorizationPolicy() {
            @Override
            public boolean supports(@NotNull Device candidate) {
                return true;
            }

            @Override
            public @NotNull List<EmqxAclRule> rules(
                    @NotNull Device candidate,
                    @NotNull String clientId) {
                return List.of(
                        new EmqxAclRule("allow", "all", "#", List.of(0, 1, 2), null),
                        new EmqxAclRule("deny", "all", "#", List.of(0, 1, 2), null));
            }
        };
        MqttAuthorizationService authorization = new MqttAuthorizationService(
                new MqttProtocolCatalog(List.of()),
                List.of(policy));
        EmqxConnectionReconciler reconciler = new EmqxConnectionReconciler(
                management,
                devices,
                connections,
                emqxConnections,
                authorization,
                distributedLock,
                Clock.fixed(NOW, ZoneOffset.UTC),
                null);

        assertThat(reconciler.runOnce()).isTrue();

        verify(management).disconnect("dbg-71");
        verify(emqxConnections).reconciled(List.of(), NOW);
        verify(connections).reconciled(DeviceConnectionProtocol.MQTT, List.of(), NOW);
    }

    @Test
    @DisplayName("单 Client 查询在线时立即登记 Management API 返回的当前连接代次")
    void refreshesCurrentOnlineConnection() {
        EmqxManagementClient management = mock(EmqxManagementClient.class);
        DeviceConnectionResolver devices = mock(DeviceConnectionResolver.class);
        DeviceConnectionReceiver connections = mock(DeviceConnectionReceiver.class);
        EmqxConnectionRepository emqxConnections = mock(EmqxConnectionRepository.class);
        Device device = device();
        MqttAuthorizationService authorization = authorization();
        EmqxClientConnection managed = new EmqxClientConnection(
                "dbg-71",
                "emqx@node",
                NOW.minusSeconds(5),
                Map.of(
                        "device_id",
                        "71",
                        "product_code",
                        "mqtt-debug",
                        "product_source",
                        "CODE",
                        "protocol_code",
                        "mqtt-debug-v1",
                        "access_method",
                        "ACCOUNT:AUTHENTICATION/SHARED_SECRET",
                        MqttAuthorizationService.FINGERPRINT_ATTRIBUTE,
                        authorization.resolve(device, "dbg-71").fingerprint()));
        when(devices.findDevice(MqttDeviceConnection.clientId("dbg-71"))).thenReturn(Optional.of(device));
        when(management.findClient("dbg-71")).thenReturn(Optional.of(managed));
        when(emqxConnections.isCurrent("dbg-71", managed.connectionId())).thenReturn(true);
        EmqxConnectionReconciler reconciler = reconciler(
                management,
                devices,
                connections,
                emqxConnections,
                authorization);

        reconciler.refreshClient("dbg-71");

        verify(emqxConnections).connected(managed, NOW);
        verify(connections).connected(any(DeviceConnection.class));
    }

    @Test
    @DisplayName("单 Client 查询离线时立即删除当前连接代次")
    void refreshesCurrentOfflineConnection() {
        EmqxManagementClient management = mock(EmqxManagementClient.class);
        DeviceConnectionResolver devices = mock(DeviceConnectionResolver.class);
        DeviceConnectionReceiver connections = mock(DeviceConnectionReceiver.class);
        EmqxConnectionRepository emqxConnections = mock(EmqxConnectionRepository.class);
        DeviceConnectionId connectionId = EmqxConnectionIds.from("dbg-71", NOW.minusSeconds(30));
        when(devices.findDevice(MqttDeviceConnection.clientId("dbg-71"))).thenReturn(Optional.of(device()));
        when(management.findClient("dbg-71")).thenReturn(Optional.empty());
        when(emqxConnections.disconnected("dbg-71", NOW)).thenReturn(Optional.of(connectionId));
        EmqxConnectionReconciler reconciler = reconciler(
                management,
                devices,
                connections,
                emqxConnections,
                authorization());

        reconciler.refreshClient("dbg-71");

        verify(connections).disconnected(DeviceConnectionProtocol.MQTT, connectionId);
    }

    /** 创建用于单 Client 刷新测试的直接 MQTT Device。 */
    private static Device device() {
        return new Device(
                71,
                ProductBinding.code("mqtt-debug"),
                "debug-key",
                null,
                "Debug Key",
                null,
                1,
                1,
                "mqtt-debug-v1",
                MqttDeviceConnection.clientId("dbg-71"),
                Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")));
    }

    /** 创建固定关闭 ACL 的当前授权服务。 */
    private static MqttAuthorizationService authorization() {
        MqttAuthorizationPolicy policy = new MqttAuthorizationPolicy() {
            @Override
            public boolean supports(@NotNull Device candidate) {
                return true;
            }

            @Override
            public @NotNull List<EmqxAclRule> rules(
                    @NotNull Device candidate,
                    @NotNull String clientId) {
                return List.of(
                        new EmqxAclRule("allow", "all", "#", List.of(0, 1, 2), null),
                        new EmqxAclRule("deny", "all", "#", List.of(0, 1, 2), null));
            }
        };
        return new MqttAuthorizationService(
                new MqttProtocolCatalog(List.of()),
                List.of(policy));
    }

    /** 创建不执行完整对账租约的单 Client 刷新服务。 */
    private static EmqxConnectionReconciler reconciler(
            EmqxManagementClient management,
            DeviceConnectionResolver devices,
            DeviceConnectionReceiver connections,
            EmqxConnectionRepository emqxConnections,
            MqttAuthorizationService authorization) {
        return new EmqxConnectionReconciler(
                management,
                devices,
                connections,
                emqxConnections,
                authorization,
                mock(DistributedLock.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null);
    }
}
