package com.sca4d.app.server.iot;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.SessionIdentityLoader;
import com.sca4d.core.api.authentication.SessionIdentityUnavailableException;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iot.IotErrorCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 重新读取 Sca4d Server 中 `iot-device` Realm Account 的 Device Subject。 */
@Component
public final class IotDeviceSessionIdentityLoader implements SessionIdentityLoader {

    private final @NotNull IdentityService identities;

    /**
     * 创建 Device Session 身份读取器。
     *
     * @param identities IAM 身份操作
     */
    public IotDeviceSessionIdentityLoader(@NotNull IdentityService identities) {
        this.identities = Objects.requireNonNull(identities, "IdentityService 不能为空");
    }

    @Override
    @CheckReturnValue
    @Contract(pure = true)
    public boolean supports(@NotNull Account account) {
        return DeviceCredentialService.DEVICE_REALM.equals(
                Objects.requireNonNull(account, "Account 不能为空").realmKey());
    }

    /**
     * 重新读取可用 Device Account 及其 Device Subject。
     *
     * @param account Session 中保存的 `iot-device` Realm Account
     * @return 当前可用的 Device Subject 和 Account
     * @throws IllegalArgumentException Account 不属于 `iot-device` Realm 时抛出
     * @throws SessionIdentityUnavailableException Account、Subject 或 Device Subject 类型已经不可用时抛出
     */
    @Override
    @CheckReturnValue
    public @NotNull SessionIdentity load(@NotNull Account account) {
        Account checked = Objects.requireNonNull(account, "Account 不能为空");
        if (!supports(checked)) {
            throw new IllegalArgumentException("Device Identity Loader 收到非 iot-device Account");
        }
        try {
            Account current = identities.activeAccountById(checked.accountId());
            Subject subject = identities.activeSubjectById(current.subjectId());
            if (subject.type() != SubjectType.DEVICE) {
                throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
            }
            return new SessionIdentity(subject, current);
        } catch (BusinessException exception) {
            throw new SessionIdentityUnavailableException(exception);
        }
    }
}
