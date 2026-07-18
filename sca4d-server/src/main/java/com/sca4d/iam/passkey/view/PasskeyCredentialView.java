package com.sca4d.iam.passkey.view;

import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.passkey.PasskeySecurityDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** 账号安全接口返回的 Passkey 展示名称、备份状态、使用时间和公共 Credential 状态。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyCredentialView {

    private Long credentialId;
    private CredentialStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private String displayName;
    private boolean backupEligible;
    private boolean backupState;
    private OffsetDateTime lastUsedAt;

    /** @return 不包含 WebAuthn 公钥、credential ID 和 user handle 的 Passkey 响应 */
    public static @NotNull PasskeyCredentialView from(@NotNull PasskeySecurityDetails passkey) {
        return new PasskeyCredentialView(
                passkey.getCredentialId(),
                passkey.getStatus(),
                passkey.getCreatedAt(),
                passkey.getUpdatedAt(),
                passkey.getVersion(),
                passkey.getDisplayName(),
                passkey.isBackupEligible(),
                passkey.isBackupState(),
                passkey.getLastUsedAt());
    }
}
