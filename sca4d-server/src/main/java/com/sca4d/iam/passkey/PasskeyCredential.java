package com.sca4d.iam.passkey;

import org.jetbrains.annotations.NotNull;

/** 完成 WebAuthn registration 后可以写入 PASSKEY Credential 的公钥和认证器状态。 */
public final class PasskeyCredential {
    private final byte[] userHandle;
    private final byte[] passkeyCredentialId;
    private final byte[] publicKeyCose;
    private final long signatureCount;
    private final byte[] aaguid;
    private final boolean backupEligible;
    private final boolean backupState;
    private final String displayName;

    public PasskeyCredential(
            byte @NotNull [] userHandle,
            byte @NotNull [] passkeyCredentialId,
            byte @NotNull [] publicKeyCose,
            long signatureCount,
            byte @NotNull [] aaguid,
            boolean backupEligible,
            boolean backupState,
            @NotNull String displayName) {
        this.userHandle = userHandle.clone();
        this.passkeyCredentialId = passkeyCredentialId.clone();
        this.publicKeyCose = publicKeyCose.clone();
        this.signatureCount = signatureCount;
        this.aaguid = aaguid.clone();
        this.backupEligible = backupEligible;
        this.backupState = backupState;
        this.displayName = displayName;
    }

    public byte @NotNull [] getUserHandle() {
        return userHandle.clone();
    }

    public byte @NotNull [] getPasskeyCredentialId() {
        return passkeyCredentialId.clone();
    }

    public byte @NotNull [] getPublicKeyCose() {
        return publicKeyCose.clone();
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public byte @NotNull [] getAaguid() {
        return aaguid.clone();
    }

    public boolean isBackupEligible() {
        return backupEligible;
    }

    public boolean isBackupState() {
        return backupState;
    }

    public @NotNull String getDisplayName() {
        return displayName;
    }
}
