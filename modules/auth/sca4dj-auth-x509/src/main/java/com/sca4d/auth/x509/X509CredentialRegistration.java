package com.sca4d.auth.x509;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存管理流程准备登记的 X.509 公钥证书，不接受或保存私钥。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class X509CredentialRegistration {

    private final byte @NotNull [] certificateDer;

    /** @param certificateDer 外部签发的 X.509 公钥证书 DER */
    public X509CredentialRegistration(byte @NotNull [] certificateDer) {
        this.certificateDer = X509Certificates.requireDer(
                Objects.requireNonNull(certificateDer, "X.509 certificate DER 不能为空"))
                .clone();
    }

    /** @return 公钥证书 DER 防御性副本 */
    public byte @NotNull [] certificateDer() {
        return certificateDer.clone();
    }
}
