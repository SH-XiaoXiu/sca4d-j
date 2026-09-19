package com.sca4d.app.server.iot;

import com.sca4d.auth.engine.AuthenticationMethods;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionLifecycle;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.connection.DeviceConnectionAccess;
import com.sca4d.iot.api.connection.DeviceConnectionAdmission;
import com.sca4d.iot.api.connection.DeviceConnectionRequest;
import com.sca4d.iot.device.DeviceService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 使用 Sca4d Server 安装的 IAM、Auth 和 Session 检查一次 Device 连接准入。 */
@Service
public class Sca4dDeviceConnectionAdmission implements DeviceConnectionAdmission {

    private final @NotNull DeviceService devices;

    private final @NotNull IdentityService identities;

    private final @NotNull AuthenticationMethods authenticationMethods;

    private final @NotNull SessionLifecycle sessions;

    /** 创建只负责 Device 连接准入的应用服务。 */
    public Sca4dDeviceConnectionAdmission(
            @NotNull DeviceService devices,
            @NotNull IdentityService identities,
            @NotNull AuthenticationMethods authenticationMethods,
            @NotNull SessionLifecycle sessions) {
        this.devices = Objects.requireNonNull(devices, "DeviceService 不能为空");
        this.identities = Objects.requireNonNull(identities, "IdentityService 不能为空");
        this.authenticationMethods = Objects.requireNonNull(authenticationMethods, "认证方法目录不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 生命周期不能为空");
    }

    /**
     * 检查 Device 声明的接入方式，并把 Account 认证统一交给 {@link AuthenticationMethods}。
     *
     * @param request EMQX 等协议适配器创建的互斥连接输入
     * @return 允许建立连接的 Device、接入方式和可选 Session
     * @throws BusinessException Device、Subject、Account、Credential 或 Session 不可用时抛出统一失败
     */
    @Override
    @CheckReturnValue
    public @NotNull DeviceConnectionAccess admit(@NotNull DeviceConnectionRequest request) {
        DeviceConnectionRequest input = Objects.requireNonNull(request, "Device 连接请求不能为空");
        Device device = devices.findByConnection(input.connection())
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED));
        DeviceAccessMethod accessMethod = input.accessMethod();
        if (!device.accessMethods().contains(accessMethod)) {
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
        }

        if (accessMethod.anonymousAccess()) {
            activeDeviceSubject(device.id());
            return new DeviceConnectionAccess(device, accessMethod, null, null);
        }
        if (accessMethod.usesSession()) {
            Session session = sessions.resolveToken(
                    input.sessionToken()
                            .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED)))
                    .filter(
                            value -> value.subject().subjectId() == device.id()
                                    && !value.authentications().isEmpty())
                    .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED));
            Account account = activeAccount(device);
            if (session.account().accountId() != account.accountId()) {
                throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
            }
            return new DeviceConnectionAccess(device, accessMethod, session.id(), session.expiresAt());
        }

        Account account = activeAccount(device);
        VerifiedAuthentication verified = authenticationMethods.authenticate(
                account,
                input.authentication()
                        .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED)));
        if (verified.account().accountId() != account.accountId()) {
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
        }
        return new DeviceConnectionAccess(
                device,
                accessMethod,
                null,
                verified.validUntil().orElse(null));
    }

    /** 要求 Device 的 Subject 和固定 Realm Account 均处于可用状态。 */
    private @NotNull Account activeAccount(@NotNull Device device) {
        Subject subject = activeDeviceSubject(device.id());
        Account account = identities.findAccounts(subject.subjectId())
                .stream()
                .filter(value -> DeviceCredentialService.DEVICE_REALM.equals(value.realmKey()))
                .findFirst()
                .map(value -> identities.activeAccountById(value.accountId()))
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED));
        if (account.subjectId() != subject.subjectId()) {
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
        }
        return account;
    }

    /** 要求 Device 对应的 IAM Subject 仍处于可用状态。 */
    private @NotNull Subject activeDeviceSubject(long deviceId) {
        Subject subject = identities.activeSubjectById(deviceId);
        if (subject.type() != SubjectType.DEVICE) {
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
        }
        return subject;
    }
}
