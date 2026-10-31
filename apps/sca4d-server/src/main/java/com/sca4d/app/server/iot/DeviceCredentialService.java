package com.sca4d.app.server.iot;

import com.sca4d.auth.engine.AuthenticationMethods;
import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.hmac.IssuedHmacKey;
import com.sca4d.auth.sharedsecret.IssuedSharedSecret;
import com.sca4d.auth.sharedsecret.SharedSecretAuthenticationMethod;
import com.sca4d.auth.x509.X509AuthenticationMethod;
import com.sca4d.auth.x509.X509CredentialRegistration;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 管理 Sca4d Server 的 Device Account 以及 Shared Secret、MAC 和 X.509 Credential。 */
@Service
public class DeviceCredentialService {

    /** Sca4d Server 的 Device Account 固定认证域。 */
    public static final @NotNull RealmKey DEVICE_REALM = new RealmKey("iot-device");

    /** 不需要 Device Account 的匿名接入方式。 */
    public static final @NotNull DeviceAccessMethod ANONYMOUS = DeviceAccessMethod.anonymous();

    /** 使用 Device Account 通用 Session 的接入方式。 */
    public static final @NotNull DeviceAccessMethod SESSION = DeviceAccessMethod.session();

    /** 使用 Shared Secret 直接认证 Device Account 的接入方式。 */
    public static final @NotNull DeviceAccessMethod SHARED_SECRET = DeviceAccessMethod
            .authentication(SharedSecretAuthenticationMethod.CODE);

    /** 使用 TLS 已验证 X.509 证书认证 Device Account 的接入方式。 */
    public static final @NotNull DeviceAccessMethod X509 = DeviceAccessMethod
            .authentication(X509AuthenticationMethod.CODE);

    private static final Set<DeviceAccessMethod> SUPPORTED_ACCESS_METHODS = Set.of(
            ANONYMOUS,
            SESSION,
            SHARED_SECRET,
            X509);

    private final @NotNull IdentityService identities;

    private final @NotNull SharedSecretAuthenticationMethod sharedSecrets;

    private final @NotNull HmacAuthenticationMethod hmac;

    private final @NotNull X509AuthenticationMethod x509;

    /** 创建 Sca4d Server 安装的 Device Credential 管理服务。 */
    public DeviceCredentialService(
            @NotNull IdentityService identities,
            @NotNull AuthenticationMethods authenticationMethods) {
        this.identities = Objects.requireNonNull(identities, "IdentityService 不能为空");
        AuthenticationMethods methods = Objects.requireNonNull(authenticationMethods, "认证方法目录不能为空");
        this.sharedSecrets = methods.method(SharedSecretAuthenticationMethod.class);
        this.hmac = methods.method(HmacAuthenticationMethod.class);
        this.x509 = methods.method(X509AuthenticationMethod.class);
    }

    /**
     * 校验 Device 接入方式与可选证书登记数据可以一起提交。
     *
     * @param accessMethods 具体应用选择的 Device 接入方式
     * @param certificate 可选 X.509 公钥证书登记数据
     * @throws BusinessException 接入方式不受支持、匿名方式与其他方式混用或证书没有对应方式时抛出
     */
    public void validateCredentials(
            @NotNull Set<DeviceAccessMethod> accessMethods,
            @Nullable X509CredentialRegistration certificate) {
        Set<DeviceAccessMethod> methods = Set.copyOf(
                Objects.requireNonNull(accessMethods, "Device 接入方式不能为空"));
        if (!SUPPORTED_ACCESS_METHODS.containsAll(methods)
                || (methods.contains(ANONYMOUS) && methods.size() != 1)
                || (certificate != null && !methods.contains(X509))) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 判断一组 Device 接入方式是否需要创建固定 Realm Account。
     *
     * @param accessMethods 已经通过 {@link #validateCredentials(Set, X509CredentialRegistration)} 校验的接入方式
     * @return 需要 Session、Shared Secret 或 X.509 时为 {@code true}
     */
    @CheckReturnValue
    public boolean requiresAccount(@NotNull Set<DeviceAccessMethod> accessMethods) {
        Set<DeviceAccessMethod> methods = Set.copyOf(
                Objects.requireNonNull(accessMethods, "Device 接入方式不能为空"));
        validateCredentials(methods, null);
        return !methods.isEmpty() && !methods.contains(ANONYMOUS);
    }

    /** @return 按稳定 code 排列的全部受支持 Device 接入方式 */
    @CheckReturnValue
    public @NotNull List<DeviceAccessMethod> supportedAccessMethods() {
        return SUPPORTED_ACCESS_METHODS.stream()
                .sorted(Comparator.comparing(DeviceAccessMethod::code))
                .toList();
    }

    /**
     * 为新 Device 签发它声明的全部 Credential。
     *
     * @param device 已写入 IoT 台账的新 Device
     * @param account 认证 Device 的固定 Realm Account；匿名接入时为空
     * @param certificate 可选 X.509 公钥证书登记数据
     * @return Device、可选 Account 标识和本次新签发的一次性 Secret
     */
    @CheckReturnValue
    public @NotNull IotDeviceCredentials issueCredentials(
            @NotNull Device device,
            @Nullable Account account,
            @Nullable X509CredentialRegistration certificate) {
        Device checkedDevice = Objects.requireNonNull(device, "Device 不能为空");
        validateCredentials(checkedDevice.accessMethods(), certificate);
        if (!requiresAccount(checkedDevice.accessMethods())) {
            if (account != null) {
                throw new IllegalArgumentException("不使用 Account 的 Device 不能创建认证 Account");
            }
            return new IotDeviceCredentials(checkedDevice, null, null, null);
        }
        Account checkedAccount = account(checkedDevice, account);

        // 按 Device 声明签发通用 Credential；原始 Secret 只保留在本次返回值中。
        String issuedSharedSecret = checkedDevice.accessMethods().contains(SHARED_SECRET)
                ? sharedSecrets.issue(checkedAccount).secret()
                : null;
        String issuedHmacKey = checkedDevice.accessMethods().contains(SESSION)
                ? hmac.issue(checkedAccount).key()
                : null;
        if (certificate != null) {
            x509.register(checkedAccount, certificate);
        }
        return new IotDeviceCredentials(
                checkedDevice,
                checkedAccount.accountId(),
                issuedSharedSecret,
                issuedHmacKey);
    }

    /**
     * 使 Device Account 的 Credential 与当前接入方式一致。
     *
     * @param device 已保存新接入方式的 Device
     * @param account Device 原有或本次新建的固定 Realm Account
     * @param certificate 可选的新 X.509 公钥证书登记数据
     * @param changedAt 本次接入方式变更的主事务时间
     * @return Device、Account 标识和本次新签发的一次性 Secret
     */
    @CheckReturnValue
    public @NotNull IotDeviceCredentials replaceCredentials(
            @NotNull Device device,
            @Nullable Account account,
            @Nullable X509CredentialRegistration certificate,
            @NotNull Instant changedAt) {
        Device checkedDevice = Objects.requireNonNull(device, "Device 不能为空");
        Set<DeviceAccessMethod> methods = checkedDevice.accessMethods();
        validateCredentials(methods, certificate);
        Instant changed = Objects.requireNonNull(changedAt, "Credential 修改时间不能为空");

        if (!requiresAccount(methods)) {
            if (account != null) {
                if (sharedSecrets.active(account.accountId())) {
                    sharedSecrets.revoke(account.accountId(), changed);
                }
                if (hmac.active(account.accountId())) {
                    hmac.revoke(account.accountId(), changed);
                }
                x509.revokeAll(account.accountId());
            }
            return new IotDeviceCredentials(
                    checkedDevice,
                    account == null ? null : account.accountId(),
                    null,
                    null);
        }
        Account checkedAccount = account(checkedDevice, account);

        // 新增方式只在缺少 Credential 时签发，移除方式立即撤销对应 Credential。
        String issuedSharedSecret = null;
        if (methods.contains(SHARED_SECRET)) {
            if (!sharedSecrets.active(checkedAccount.accountId())) {
                issuedSharedSecret = sharedSecrets.issue(checkedAccount).secret();
            }
        } else if (sharedSecrets.active(checkedAccount.accountId())) {
            sharedSecrets.revoke(checkedAccount.accountId(), changed);
        }
        String issuedHmacKey = null;
        if (methods.contains(SESSION)) {
            if (!hmac.active(checkedAccount.accountId())) {
                issuedHmacKey = hmac.issue(checkedAccount).key();
            }
        } else if (hmac.active(checkedAccount.accountId())) {
            hmac.revoke(checkedAccount.accountId(), changed);
        }
        if (!methods.contains(X509)) {
            x509.revokeAll(checkedAccount.accountId());
        } else if (certificate != null) {
            x509.register(checkedAccount, certificate);
        }
        return new IotDeviceCredentials(
                checkedDevice,
                checkedAccount.accountId(),
                issuedSharedSecret,
                issuedHmacKey);
    }

    /**
     * 重置 Device 当前 Shared Secret。
     *
     * @param device 已声明 {@code account:shared-secret} 接入的 Device
     * @return 新 Credential 和只在本次调用可见的 Secret
     */
    @CheckReturnValue
    public @NotNull IssuedSharedSecret resetSharedSecret(@NotNull Device device) {
        Device checked = Objects.requireNonNull(device, "Device 不能为空");
        if (!checked.accessMethods().contains(SHARED_SECRET)) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        return sharedSecrets.issue(account(checked, findAccount(checked.id()).orElse(null)));
    }

    /**
     * 重置 Device 建立 Session 使用的 MAC Key。
     *
     * @param device 已声明 {@code session} 接入的 Device
     * @return 新 Credential 和只在本次调用可见的 Key
     */
    @CheckReturnValue
    public @NotNull IssuedHmacKey resetHmacKey(@NotNull Device device) {
        Device checked = Objects.requireNonNull(device, "Device 不能为空");
        if (!checked.accessMethods().contains(SESSION)) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        return hmac.issue(account(checked, findAccount(checked.id()).orElse(null)));
    }

    /**
     * 校验 Device 当前接入方式已经具备启用所需的 Account 和 Credential。
     *
     * @param device 已通过 IoT 技术启用检查的 Device
     * @param account Device 固定 Realm 下的可选 Account
     */
    public void validateActivation(@NotNull Device device, @Nullable Account account) {
        Device checked = Objects.requireNonNull(device, "Device 不能为空");
        validateCredentials(checked.accessMethods(), null);
        if (!requiresAccount(checked.accessMethods())) {
            return;
        }
        Account checkedAccount = account(checked, account);
        if ((checked.accessMethods().contains(SHARED_SECRET)
                && !sharedSecrets.active(checkedAccount.accountId()))
                || (checked.accessMethods().contains(SESSION)
                        && !hmac.active(checkedAccount.accountId()))
                || (checked.accessMethods().contains(X509)
                        && !x509.active(checkedAccount.accountId()))) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
    }

    /**
     * 读取 Device 固定 Realm 下保留的 Account。
     *
     * @param deviceId Device 与 Subject 共用的标识
     * @return 已创建的 Device Account；匿名接入或尚未创建时为空
     */
    @CheckReturnValue
    public @NotNull Optional<Account> findAccount(long deviceId) {
        return identities.findAccounts(deviceId)
                .stream()
                .filter(account -> DEVICE_REALM.equals(account.realmKey()))
                .findFirst();
    }

    /** 要求 Account 属于指定 Device 的固定 Realm，不要求 Device 或 Account 当前启用。 */
    private static @NotNull Account account(
            @NotNull Device device,
            @Nullable Account account) {
        Device checkedDevice = Objects.requireNonNull(device, "Device 不能为空");
        Account checkedAccount = Optional.ofNullable(account)
                .orElseThrow(() -> new BusinessException(IotErrorCode.STATE_CONFLICT));
        if (checkedAccount.subjectId() != checkedDevice.id()
                || !DEVICE_REALM.equals(checkedAccount.realmKey())) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        return checkedAccount;
    }
}
