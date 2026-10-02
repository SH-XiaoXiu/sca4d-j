package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.passkey.credential.PasskeyCredential;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 返回不包含公钥和 user handle 的 Passkey Credential 管理信息。
 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class PasskeyCredentialView {

    private final Instant createdAt;

    private final @Nullable Instant lastUsedAt;

    private final List<String> transports;

    private final String name;

    private final boolean backedUp;

    private final boolean backupEligible;

    private final long id;

    private final long version;

    private PasskeyCredentialView(PasskeyCredential credential) {
        this.id = credential.id();
        this.name = credential.name();
        this.transports = credential.transports().stream().sorted().toList();
        this.backupEligible = credential.backupEligible();
        this.backedUp = credential.backedUp();
        this.createdAt = credential.createdAt();
        this.lastUsedAt = credential.lastUsedAt();
        this.version = credential.version().value();
    }

    /**
     * 创建 Passkey Credential 的安全 HTTP 投影。
     */
    public static @NotNull PasskeyCredentialView from(@NotNull PasskeyCredential credential) {
        return new PasskeyCredentialView(Objects.requireNonNull(credential, "Passkey Credential 不能为空"));
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getTransports() {
        return List.copyOf(transports);
    }

    public boolean isBackupEligible() {
        return backupEligible;
    }

    public boolean isBackedUp() {
        return backedUp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public long getVersion() {
        return version;
    }
}
