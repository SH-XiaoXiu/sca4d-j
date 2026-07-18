package com.sca4d.tenant.request;

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

import java.util.ArrayList;
import java.util.List;

/** 在租户中创建一个新的成员登录账号请求。 */
@Data
public class TenantMemberCreateAccountRequest implements IdempotentRequest {
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

    private Long avatarFileId;

    @SafeText(max = 4096)
    private String avatarUploadGrant;

    private List<Long> roleIds = new ArrayList<>();
    private String idempotencyKey;

    /**
     * 构造密码认证材料，交由凭证域完成转换和校验。
     *
     * @return 密码认证材料
     */
    public @NotNull PasswordCredential credential() {
        return new PasswordCredential(password);
    }
}
