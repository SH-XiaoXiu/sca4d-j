package com.sca4d.app.server.iot;

import com.sca4d.auth.engine.AuthenticationMethods;
import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.hmac.HmacProof;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.EstablishedSession;
import com.sca4d.core.api.authentication.SessionLifecycle;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.device.DeviceService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** 使用 Device MAC 认证结果建立 Sca4d Server 的通用 Session。 */
@Service
public class DeviceSessionService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceSessionService.class);

    private final @NotNull DeviceService devices;

    private final @NotNull IdentityService identities;

    private final @NotNull AuthenticationMethods authenticationMethods;

    private final @NotNull SessionLifecycle sessions;

    /** 创建 Device MAC Session 服务。 */
    public DeviceSessionService(
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
     * 验证 Device HTTP MAC canonical request 并建立 Session。
     *
     * @param request 已按 Sca4d Server HTTP 规则规范化的请求
     * @param signature 无填充 Base64URL MAC-SHA256
     * @return 新建 Session 和仅返回一次的 Token
     */
    @CheckReturnValue
    public @NotNull EstablishedSession establish(
            @NotNull HmacCanonicalRequest request,
            @NotNull String signature) {
        HmacCanonicalRequest checked = Objects.requireNonNull(request, "HMAC request 不能为空");
        Device device;
        VerifiedAuthentication verification;
        try {
            device = devices.loadById(checked.deviceId());
            if (!device.accessMethods().contains(DeviceCredentialService.SESSION)) {
                throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
            }
            Subject subject = identities.activeSubjectById(device.id());
            if (subject.type() != SubjectType.DEVICE) {
                throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
            }
            Account account = identities.findAccounts(subject.subjectId())
                    .stream()
                    .filter(value -> DeviceCredentialService.DEVICE_REALM.equals(value.realmKey()))
                    .findFirst()
                    .map(value -> identities.activeAccountById(value.accountId()))
                    .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED));
            if (account.subjectId() != subject.subjectId()) {
                throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
            }
            verification = authenticationMethods.authenticate(
                    account,
                    new AuthenticationSubmission<>(
                            HmacAuthenticationMethod.CODE,
                            HmacProof.class,
                            new HmacProof(
                                    checked.timestamp(),
                                    checked.nonce(),
                                    checked.canonicalBytes(),
                                    Objects.requireNonNull(signature, "签名不能为空"))));
        } catch (BusinessException | IllegalArgumentException exception) {
            LOG.debug("IoT HMAC Device 认证已拒绝 | deviceId={}", checked.deviceId());
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED, exception);
        }
        EstablishedSession established = sessions.establish(
                verification.account(),
                List.of(verification.authentication()));
        LOG.info(
                "IoT HMAC Session 已建立 | deviceId={} accountId={} sessionId={}",
                device.id(),
                verification.account().accountId(),
                established.session().id().value());
        return established;
    }
}
