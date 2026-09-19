package com.sca4d.auth.x509;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存传输层完成证书链、有效期和客户端证书用途验证后的 X.509 公钥证书。 */
public final class X509TransportProof {

    private final byte @NotNull [] certificateDer;

    private X509TransportProof(byte @NotNull [] certificateDer) {
        this.certificateDer = X509Certificates.requireDer(
                Objects.requireNonNull(certificateDer, "X.509 certificate DER 不能为空"))
                .clone();
    }

    /**
     * 创建传输层已经验证的证书 Proof。
     *
     * <p>调用方必须先完成受信任根证书链、证书有效期、客户端认证用途和握手签名验证。原始 PEM、
     * 指纹或只完成格式解析的证书不能调用该方法。
     *
     * @param certificateDer TLS 对端实际出示的 X.509 公钥证书 DER
     * @return 只能交给 `X509AuthenticationMethod` 的传输验证结果
     */
    public static @NotNull X509TransportProof verified(byte @NotNull [] certificateDer) {
        return new X509TransportProof(certificateDer);
    }

    /** @return TLS 对端实际出示的公钥证书 DER 防御性副本 */
    public byte @NotNull [] certificateDer() {
        return certificateDer.clone();
    }

    @Override
    public @NotNull String toString() {
        return "X509TransportProof[certificateDer=protected]";
    }
}
