package com.sca4d.iam.account.request;

import com.sca4d.common.auth.PasswordCredential;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/** 重置 Account PASSWORD Credential 的请求。Credential 版本由 If-Match 提供。 */
@Data
public class AccountPasswordCredentialReplaceRequest {

    @NotBlank
    @Size(max = 255)
    private String password;

    /** @return 交给 PASSWORD Credential Scheme 哈希和持久化的类型化材料 */
    public @NotNull PasswordCredential credential() {
        return new PasswordCredential(password);
    }
}
