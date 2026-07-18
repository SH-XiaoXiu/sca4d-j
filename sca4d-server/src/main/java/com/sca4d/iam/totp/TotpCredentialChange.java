package com.sca4d.iam.totp;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** TOTP Credential 创建或替换后的主键和新版本。 */
@Getter
@AllArgsConstructor
public class TotpCredentialChange {
    private Long credentialId;
    private Long version;
}
