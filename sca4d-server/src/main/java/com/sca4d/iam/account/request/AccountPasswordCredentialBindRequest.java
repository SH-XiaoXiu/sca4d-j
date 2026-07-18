package com.sca4d.iam.account.request;

import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/** 为 Account 绑定 PASSWORD Credential 的请求。 */
@Data
public class AccountPasswordCredentialBindRequest implements IdempotentRequest {

    @NotBlank
    @SafeText(max = 128)
    private String identifier;

    @NotBlank
    @Size(max = 255)
    private String password;

    private String idempotencyKey;

    /** @return 交给 PASSWORD Credential Scheme 哈希和持久化的类型化材料 */
    public @NotNull PasswordCredential credential() {
        return new PasswordCredential(password);
    }
}
