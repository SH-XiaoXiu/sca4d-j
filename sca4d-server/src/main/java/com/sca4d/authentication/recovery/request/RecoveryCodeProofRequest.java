package com.sca4d.authentication.recovery.request;

import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import com.sca4d.iam.identifier.AccountIdentifierType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 向 RECOVERY 认证 提交 AccountIdentifier 和一次性恢复码的请求。 */
@Getter
@Setter
@NoArgsConstructor
public class RecoveryCodeProofRequest {

    @Required
    private AccountIdentifierType identifierType;

    @NotBlank
    @SafeText(max = 128)
    private String identifier;

    @NotBlank
    @SafeText(max = 32)
    private String recoveryCode;
}
