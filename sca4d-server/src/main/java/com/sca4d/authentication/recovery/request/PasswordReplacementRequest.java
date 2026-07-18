package com.sca4d.authentication.recovery.request;

import com.sca4d.common.auth.PasswordCredential;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/** 使用已验证 RECOVERY 认证 创建密码替换记录的请求。 */
@Getter
@Setter
@NoArgsConstructor
public class PasswordReplacementRequest {

    @NotBlank
    @Size(max = 255)
    private String password;

    /** @return 只在当前账号恢复请求中交给 PASSWORD CredentialScheme 的新密码 */
    public @NotNull PasswordCredential credential() {
        return new PasswordCredential(password);
    }
}
