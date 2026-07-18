package com.sca4d.iam.passkey;

import com.sca4d.iam.passkey.repository.PasskeyRepository;
import com.sca4d.iam.passkey.repository.PasskeyRow;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 将项目 Passkey Repository 转换为 Yubico WebAuthn Server 的只读 CredentialRepository。 */
@Component
@RequiredArgsConstructor
public class YubicoCredentialRepository implements CredentialRepository {

    private static final Base64.Encoder USERNAME_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder USERNAME_DECODER = Base64.getUrlDecoder();

    private final PasskeyRepository repository;

    @Override
    public @NotNull Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(
            @NotNull String username) {
        byte[] userHandle = userHandle(username);
        if (userHandle == null) {
            return Set.of();
        }
        return repository.findByUserHandle(userHandle).stream()
                .map(row -> PublicKeyCredentialDescriptor.builder()
                        .id(new ByteArray(row.getPasskeyCredentialId()))
                        .build())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public @NotNull Optional<ByteArray> getUserHandleForUsername(
            @NotNull String username) {
        byte[] userHandle = userHandle(username);
        if (userHandle == null || repository.findByUserHandle(userHandle).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ByteArray(userHandle));
    }

    @Override
    public @NotNull Optional<String> getUsernameForUserHandle(
            @NotNull ByteArray userHandle) {
        if (repository.findByUserHandle(userHandle.getBytes()).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(USERNAME_ENCODER.encodeToString(userHandle.getBytes()));
    }

    @Override
    public @NotNull Optional<RegisteredCredential> lookup(
            @NotNull ByteArray credentialId, @NotNull ByteArray userHandle) {
        return Optional.ofNullable(repository.find(credentialId.getBytes(), userHandle.getBytes()))
                .map(this::registeredCredential);
    }

    @Override
    public @NotNull Set<RegisteredCredential> lookupAll(@NotNull ByteArray credentialId) {
        return repository.findByPasskeyCredentialId(credentialId.getBytes()).stream()
                .map(this::registeredCredential)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 将持久化行转换为 Yubico 验签所需的公钥、user handle、计数和备份状态。
     *
     * @param row 已包含 PASSKEY 子表和 Account user handle 的查询行
     * @return 不包含 Account、Subject 或页面字段的 Yubico Credential
     */
    private @NotNull RegisteredCredential registeredCredential(@NotNull PasskeyRow row) {
        return RegisteredCredential.builder()
                .credentialId(new ByteArray(row.getPasskeyCredentialId()))
                .userHandle(new ByteArray(row.getUserHandle()))
                .publicKeyCose(new ByteArray(row.getPublicKeyCose()))
                .signatureCount(row.getSignatureCount())
                .backupEligible(row.getBackupEligible())
                .backupState(row.getBackupState())
                .build();
    }

    /**
     * 解析服务端生成的 Base64URL user handle 用户名。
     *
     * @return 解析后的字节；格式错误时返回 {@code null}，Yubico 将其视为未知用户
     */
    private byte @Nullable [] userHandle(@NotNull String username) {
        try {
            return USERNAME_DECODER.decode(username);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
