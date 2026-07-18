package com.sca4d.iam.account.request;

import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.PhoneNumber;
import com.sca4d.common.validation.SafeText;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/** 创建 GLOBAL Account 时提交的首个 Identifier、Credential 和 Subject 默认资料。 */
@Data
public class GlobalAccountCreateRequest implements IdempotentRequest {
    @NotBlank
    @SafeText(max = 128)
    private String identifier;
    @NotBlank
    @Size(max = 255)
    private String password;
    @NotBlank
    @DisplayName
    private String displayName;
    @PhoneNumber
    private String phone;
    @Email
    @SafeText(max = 128)
    private String email;
    private String idempotencyKey;

    /** 将密码输入转换为 Credential。 */
    public @NotNull PasswordCredential credential() {
        return new PasswordCredential(password);
    }
}
