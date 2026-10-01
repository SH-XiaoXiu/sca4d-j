package com.sca4d.auth.passkey.credential;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把 Passkey 模块的 Credential Repository 接入 Yubico WebAuthn 校验器。
 */
public class YubicoCredentialRepository implements CredentialRepository {

    private final PasskeyCredentialRepository credentials;

    /**
     * @param credentials Passkey 模块 Credential Repository
     */
    public YubicoCredentialRepository(@NotNull PasskeyCredentialRepository credentials) {
        this.credentials = Objects.requireNonNull(credentials, "Passkey Credential Repository 不能为空");
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        Long accountId = parseAccountId(username);
        if (accountId == null) {
            return Set.of();
        }
        return credentials.findAll(accountId)
                .stream()
                .map(YubicoCredentialRepository::descriptor)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        Long accountId = parseAccountId(username);
        if (accountId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(credentials.findUserHandle(accountId)).map(ByteArray::new);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return Optional.ofNullable(credentials.findAccountId(userHandle.getBytes())).map(String::valueOf);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return Optional.ofNullable(
                credentials.findByCredentialIdAndUserHandle(
                        credentialId.getBytes(),
                        userHandle.getBytes()))
                .map(YubicoCredentialRepository::registeredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        PasskeyCredential credential = credentials.findByCredentialId(credentialId.getBytes());
        return credential == null ? Set.of() : Set.of(registeredCredential(credential));
    }

    /**
     * 把模块 Credential 转换为浏览器 assertion options 使用的 descriptor。
     */
    private static PublicKeyCredentialDescriptor descriptor(PasskeyCredential credential) {
        return PublicKeyCredentialDescriptor.builder()
                .id(new ByteArray(credential.credentialId()))
                .transports(transports(credential))
                .build();
    }

    /**
     * 把模块 Credential 转换为 Yubico 校验所需的只读记录。
     */
    private static RegisteredCredential registeredCredential(PasskeyCredential credential) {
        return RegisteredCredential.builder()
                .credentialId(new ByteArray(credential.credentialId()))
                .userHandle(new ByteArray(credential.userHandle()))
                .publicKeyCose(new ByteArray(credential.publicKeyCose()))
                .signatureCount(credential.signatureCount())
                .build();
    }

    /**
     * 返回 Yubico 能够识别的 authenticator transport 集合。
     */
    private static Set<AuthenticatorTransport> transports(PasskeyCredential credential) {
        return credential.transports()
                .stream()
                .map(AuthenticatorTransport::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 把 WebAuthn username 解析为内部 Account 标识；无效输入不执行数据库查询。
     */
    private static Long parseAccountId(String username) {
        try {
            long value = Long.parseLong(username);
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
