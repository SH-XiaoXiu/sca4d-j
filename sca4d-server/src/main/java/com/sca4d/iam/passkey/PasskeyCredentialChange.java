package com.sca4d.iam.passkey;

import org.jetbrains.annotations.NotNull;

/** Passkey Credential 创建后的主键和公共版本。 */
public final class PasskeyCredentialChange {
    private final Long credentialId;
    private final Long version;

    public PasskeyCredentialChange(@NotNull Long credentialId, @NotNull Long version) {
        this.credentialId = credentialId;
        this.version = version;
    }

    public @NotNull Long getCredentialId() {
        return credentialId;
    }

    public @NotNull Long getVersion() {
        return version;
    }
}
