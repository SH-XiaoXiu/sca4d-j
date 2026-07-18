package com.sca4d.iam.account.view;

import com.sca4d.common.auth.CredentialType;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.iam.credential.CredentialStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** 当前用户可见且不包含摘要、密钥或协议秘密的 Credential。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountCredentialView {

    private Long credentialId;
    private CredentialType credentialType;
    private CredentialStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    /** @return 当前 Credential 的公共状态和时间字段 */
    public static @NotNull AccountCredentialView from(@NotNull IamCredentialEntity credential) {
        return new AccountCredentialView(
                credential.getId(),
                credential.getCredentialType(),
                credential.getStatus(),
                credential.getCreatedAt(),
                credential.getUpdatedAt(),
                credential.getVersion());
    }
}
