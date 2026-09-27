package com.sca4d.auth.passkey.registration;

import static com.sca4d.auth.passkey.credential.PasskeyCredentialRepository.CredentialSlotReservation.ACCOUNT_MISSING;
import static com.sca4d.auth.passkey.credential.PasskeyCredentialRepository.CredentialSlotReservation.LIMIT_REACHED;
import static com.sca4d.auth.passkey.credential.PasskeyCredentialRepository.CredentialSlotReservation.RESERVED;

import com.sca4d.auth.passkey.PasskeyCeremonyId;
import com.sca4d.auth.passkey.PasskeyErrorCode;
import com.sca4d.auth.passkey.ceremony.PasskeyCeremonyStore;
import com.sca4d.auth.passkey.config.PasskeySettings;
import com.sca4d.auth.passkey.credential.PasskeyCredential;
import com.sca4d.auth.passkey.credential.PasskeyCredentialRepository;
import com.sca4d.auth.passkey.credential.PasskeyCredentialRepository.CredentialSlotReservation;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.utils.TextUtils;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RegistrationExtensionInputs;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理 discoverable Passkey registration 和 Credential 列表、删除操作。
 */
public class PasskeyRegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(PasskeyRegistrationService.class);

    private static final int BACKUP_ELIGIBLE_FLAG = 0x08;

    private static final int BACKED_UP_FLAG = 0x10;

    private static final int USER_HANDLE_BYTES = 32;

    private final RelyingParty relyingParty;

    private final PasskeyCeremonyStore ceremonies;

    private final PasskeyCredentialRepository credentials;

    private final PasskeySettings settings;

    private final Clock clock;

    private final SecureRandom secureRandom;

    /**
     * 创建 Passkey registration 服务。
     *
     * @param relyingParty       Yubico WebAuthn 校验入口
     * @param ceremonies         Redis ceremony 存储
     * @param credentials        Passkey Credential Repository
     * @param settings            保存 Credential 时读取数量上限的 Settings
     * @param clock              Credential 时间来源
     * @param secureRandom       生成不透明 user handle 的安全随机数来源
     */
    public PasskeyRegistrationService(
            @NotNull RelyingParty relyingParty,
            @NotNull PasskeyCeremonyStore ceremonies,
            @NotNull PasskeyCredentialRepository credentials,
            @NotNull PasskeySettings settings,
            @NotNull Clock clock,
            @NotNull SecureRandom secureRandom) {
        this.relyingParty = Objects.requireNonNull(relyingParty, "WebAuthn RelyingParty 不能为空");
        this.ceremonies = Objects.requireNonNull(ceremonies, "Passkey ceremony 存储不能为空");
        this.credentials = Objects.requireNonNull(credentials, "Passkey Credential Repository 不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.secureRandom = Objects.requireNonNull(secureRandom, "安全随机数来源不能为空");
    }

    /**
     * 为已经通过 App 动作认证的账号开始 registration。
     *
     * @param account     注册 Passkey 的账号
     * @param displayName 浏览器账号选择器显示的名称
     * @return 强制 resident key、UV 和 credProps 的 registration options
     */
    @Transactional
    public @NotNull PasskeyRegistrationOptions start(
            @NotNull Account account,
            @NotNull String displayName) {
        Account checkedAccount = Objects.requireNonNull(account, "Passkey Account 不能为空");

        // Service 生成不透明 user handle；Repository 只负责并发首次写入并返回数据库中的稳定值。
        byte[] userHandle = credentials.findUserHandle(checkedAccount.accountId());
        if (userHandle == null) {
            byte[] generated = new byte[USER_HANDLE_BYTES];
            secureRandom.nextBytes(generated);
            userHandle = credentials.createUserHandleIfAbsent(checkedAccount.accountId(), generated);
        }
        UserIdentity user = UserIdentity.builder()
                .name(Long.toString(checkedAccount.accountId()))
                .displayName(TextUtils.requireText(displayName, "Passkey 显示名称"))
                .id(new ByteArray(userHandle))
                .build();
        PublicKeyCredentialCreationOptions request = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(user)
                        .authenticatorSelection(
                                AuthenticatorSelectionCriteria.builder()
                                        .residentKey(ResidentKeyRequirement.REQUIRED)
                                        .userVerification(UserVerificationRequirement.REQUIRED)
                                        .build())
                        .extensions(RegistrationExtensionInputs.builder().credProps().build())
                        .build());
        try {
            PasskeyCeremonyStore.StoredCeremony stored = ceremonies
                    .storeRegistration(checkedAccount.accountId(), request.toJson());
            LOG.debug(
                    "Passkey 注册流程已创建 | accountId={} ceremonyId={} expiresAt={}",
                    checkedAccount.accountId(),
                    stored.id().value(),
                    stored.expiresAt());
            return new PasskeyRegistrationOptions(
                    stored.id(),
                    request.toCredentialsCreateJson(),
                    stored.expiresAt());
        } catch (IOException exception) {
            throw new IllegalStateException("Passkey registration options 编码失败", exception);
        }
    }

    /**
     * 原子消费 registration ceremony，验证浏览器响应并通过账号锚点乐观版本保存 Credential。
     *
     * @param account      当前已通过 App 动作认证的账号
     * @param ceremonyId   registration 开始时返回的标识
     * @param responseJson 浏览器 WebAuthn JSON API 返回的 registration JSON
     * @param name         用户为新 Credential 设置的名称
     * @return 数据库保存后的 Credential
     */
    @Transactional
    public @NotNull PasskeyCredential finish(
            @NotNull Account account,
            @NotNull PasskeyCeremonyId ceremonyId,
            @NotNull String responseJson,
            @NotNull String name) {
        Account checkedAccount = Objects.requireNonNull(account, "Passkey Account 不能为空");

        // ceremony 在校验前原子消费，并复核开始 registration 时绑定的 Account。
        PasskeyCeremonyStore.RegistrationCeremony ceremony = ceremonies.consumeRegistration(ceremonyId);
        if (ceremony.accountId() != checkedAccount.accountId()) {
            throw new BusinessException(PasskeyErrorCode.REGISTRATION_REJECTED);
        }
        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions
                    .fromJson(ceremony.requestJson());
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> response = PublicKeyCredential
                    .parseRegistrationResponseJson(responseJson);

            // Yubico 校验 challenge、Origin、RP ID、attestation、公钥和用户验证结果。
            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(request)
                            .response(response)
                            .build());
            if (!result.isUserVerified() || !result.isDiscoverable().orElse(false)) {
                throw new BusinessException(PasskeyErrorCode.REGISTRATION_REJECTED);
            }
            Set<String> transports = response.getResponse()
                    .getTransports()
                    .stream()
                    .map(AuthenticatorTransport::getId)
                    .collect(Collectors.toUnmodifiableSet());
            byte authenticatorFlags = response.getResponse()
                    .getParsedAuthenticatorData()
                    .getFlags().value;
            boolean backupEligible = (authenticatorFlags & BACKUP_ELIGIBLE_FLAG) != 0;
            boolean backedUp = (authenticatorFlags & BACKED_UP_FLAG) != 0;
            Instant now = clock.instant();

            // 通过账号锚点乐观版本抢占名额，再保存经过验证的 Credential 公开字段。
            PasskeyCredential credential = new PasskeyCredential(
                    0,
                    checkedAccount.accountId(),
                    request.getUser().getId().getBytes(),
                    result.getKeyId().getId().getBytes(),
                    result.getPublicKeyCose().getBytes(),
                    result.getSignatureCount(),
                    transports,
                    backupEligible,
                    backedUp,
                    TextUtils.requireText(name, "Passkey 名称"),
                    now,
                    null,
                    new Version(0));
            int maximumCredentials = settings.MAXIMUM_CREDENTIALS.get();
            byte[] storedUserHandle = credentials.findUserHandle(checkedAccount.accountId());
            if (storedUserHandle == null || !Arrays.equals(storedUserHandle, credential.userHandle())) {
                throw new IllegalStateException("Passkey 账号锚点不存在或 user handle 不一致");
            }

            // Service 根据单次 CAS 的结果决定重试、数量上限和并发失败，不让 Repository 决定业务错误。
            boolean reserved = false;
            for (int attempt = 0; attempt <= maximumCredentials; attempt++) {
                CredentialSlotReservation reservation = credentials.reserveCredentialSlot(
                        checkedAccount.accountId(),
                        maximumCredentials);
                if (reservation == RESERVED) {
                    reserved = true;
                    break;
                }
                if (reservation == LIMIT_REACHED) {
                    throw new BusinessException(PasskeyErrorCode.CREDENTIAL_LIMIT_REACHED);
                }
                if (reservation == ACCOUNT_MISSING) {
                    throw new IllegalStateException("Passkey 账号锚点不存在");
                }
            }
            if (!reserved) {
                throw new OptimisticLockException("Passkey Credential 名额");
            }

            PasskeyCredential saved = credentials.insert(credential);
            if (saved == null) {
                throw new BusinessException(PasskeyErrorCode.REGISTRATION_REJECTED);
            }
            LOG.debug(
                    "Passkey 注册结果已写入事务 | accountId={} credentialId={} credentialCountLimit={}",
                    saved.accountId(),
                    saved.id(),
                    maximumCredentials);
            return saved;
        } catch (RegistrationFailedException | IOException exception) {
            throw new BusinessException(PasskeyErrorCode.REGISTRATION_REJECTED, exception);
        }
    }

    /**
     * 返回账号当前全部有效 Passkey Credential。
     */
    @Transactional(readOnly = true)
    public @NotNull List<PasskeyCredential> list(@NotNull Account account) {
        return credentials.findAll(Objects.requireNonNull(account, "Passkey Account 不能为空").accountId());
    }

    /**
     * 删除已经通过 App 动作认证的账号所拥有的 Credential，不修改方法开关或现有 Session。
     *
     * @param account                     Credential 所属账号
     * @param credentialId                模块内部 Credential 标识
     * @param minimumRemainingCredentials 删除后至少保留的有效 Credential 数量
     */
    @Transactional
    public void delete(
            @NotNull Account account,
            long credentialId,
            int minimumRemainingCredentials) {
        Account checkedAccount = Objects.requireNonNull(account, "Passkey Account 不能为空");

        // 先确认 Credential 归属并取得版本，再通过账号锚点和 Credential 乐观版本执行删除。
        PasskeyCredential credential = credentials.findAll(checkedAccount.accountId())
                .stream()
                .filter(candidate -> candidate.id() == credentialId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(PasskeyErrorCode.CREDENTIAL_UNAVAILABLE));
        credentials.delete(
                checkedAccount.accountId(),
                credentialId,
                credential.version(),
                minimumRemainingCredentials);
        LOG.debug(
                "Passkey 凭据删除已写入事务 | accountId={} credentialId={} minimumRemaining={}",
                checkedAccount.accountId(),
                credentialId,
                minimumRemainingCredentials);
    }

}
