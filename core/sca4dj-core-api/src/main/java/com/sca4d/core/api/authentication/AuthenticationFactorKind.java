package com.sca4d.core.api.authentication;

/** 表示已经验证的认证因素类别。 */
public enum AuthenticationFactorKind {
    /** 由用户记忆并提交的秘密，例如密码或 PIN。 */
    KNOWLEDGE,

    /** 由用户持有设备或凭据产生的证明，例如 TOTP 或硬件密钥。 */
    POSSESSION,

    /** 由用户自身特征产生的证明，例如指纹或人脸。 */
    INHERENCE
}
