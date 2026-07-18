package com.sca4d.iam.passkey.view;

import com.sca4d.iam.passkey.PasskeyCredentialChange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** Passkey Credential 创建后的主键和 ETag 对应版本。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyCredentialChangeView {
    private Long credentialId;
    private Long version;

    /** @return 新建 Credential 的公共主键和初始版本 */
    public static @NotNull PasskeyCredentialChangeView from(
            @NotNull PasskeyCredentialChange change) {
        return new PasskeyCredentialChangeView(change.getCredentialId(), change.getVersion());
    }
}
