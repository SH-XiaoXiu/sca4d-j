package com.sca4d.iam.totp.view;

import com.sca4d.iam.totp.TotpCredentialChange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** TOTP Credential 创建或替换后的主键和版本响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TotpCredentialChangeView {
    private Long credentialId;
    private Long version;

    /** @return 不包含 TOTP 密钥和验证码的 Credential 变更响应 */
    public static @NotNull TotpCredentialChangeView from(@NotNull TotpCredentialChange change) {
        return new TotpCredentialChangeView(change.getCredentialId(), change.getVersion());
    }
}
