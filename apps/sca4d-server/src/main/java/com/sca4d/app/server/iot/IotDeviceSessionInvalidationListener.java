package com.sca4d.app.server.iot;

import com.sca4d.core.api.authentication.SessionInvalidationListener;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iot.connection.DeviceConnectionService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 在 Device Account 的 Session 失效后可靠登记当前 Device 连接的断开消息。 */
@Component
public class IotDeviceSessionInvalidationListener implements SessionInvalidationListener {

    private final @NotNull DeviceConnectionService connections;

    private final @NotNull IdentityService identities;

    /**
     * 创建 Device Session 失效监听器。
     *
     * @param identities IAM 身份操作
     * @param connections Device 连接操作
     */
    public IotDeviceSessionInvalidationListener(
            @NotNull IdentityService identities,
            @NotNull DeviceConnectionService connections) {
        this.identities = Objects.requireNonNull(identities, "IdentityService 不能为空");
        this.connections = Objects.requireNonNull(connections, "DeviceConnectionService 不能为空");
    }

    /**
     * 断开 `iot-device` Realm Account 对应 Device 的当前连接。
     *
     * <p>非 Device Account 不产生断连消息。断连消息保存明确连接代次，重复消费不会断开后来建立的连接。
     *
     * @param accountId Session 已失效的 Account 标识
     */
    @Override
    public void invalidated(long accountId) {
        identities.findAccount(accountId)
                .filter(account -> DeviceCredentialService.DEVICE_REALM.equals(account.realmKey()))
                .ifPresent(
                        account -> connections.disconnectDevice(
                                account.subjectId(),
                                SessionTerminationReason.IDENTITY_UNAVAILABLE.code()));
    }
}
