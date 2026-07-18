package com.sca4d.authentication.password.request;

import com.sca4d.common.auth.PasswordProof;
import com.sca4d.common.validation.SafeText;
import com.sca4d.iam.identifier.AccountIdentifierType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 向 Authentication 提交密码 Proof；初始密码登录额外使用账号标识定位 Account。 */
@Data
@NoArgsConstructor
public class PasswordProofRequest {

    private AccountIdentifierType identifierType;

    @SafeText(max = 128)
    private String identifier;

    @NotBlank
    private String password;

    /** @return 只在当前服务调用中使用的密码 Proof */
    public @NotNull PasswordProof toProof() {
        return new PasswordProof(password);
    }
}
