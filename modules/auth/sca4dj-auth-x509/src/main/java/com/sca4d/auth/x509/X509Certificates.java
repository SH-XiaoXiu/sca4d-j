package com.sca4d.auth.x509;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

/** 提供 X.509 模块内部统一的 DER 解析和 SHA-256 指纹计算。 */
class X509Certificates {

    static final int MAX_DER_BYTES = 64 * 1024;

    private X509Certificates() {
    }

    /** 解析单张 DER X.509 公钥证书；证书格式无效时以 errorCode 抛出 BusinessException。 */
    static @NotNull X509Certificate parse(byte @NotNull [] der, @NotNull ErrorCode errorCode) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(
                            new ByteArrayInputStream(
                                    requireDer(der)));
        } catch (CertificateException exception) {
            throw new BusinessException(errorCode, exception);
        }
    }

    /** 返回 DER 的小写 SHA-256 十六进制指纹。 */
    static @NotNull String fingerprint(byte @NotNull [] der) {
        return DigestUtil.sha256Hex(requireDer(der));
    }

    /** 校验单张 X.509 公钥证书 DER 的模块输入上限。 */
    static byte @NotNull [] requireDer(byte @NotNull [] der) {
        byte[] checked = Objects.requireNonNull(der, "X.509 certificate DER 不能为空");
        if (checked.length == 0 || checked.length > MAX_DER_BYTES) {
            throw new IllegalArgumentException("X.509 certificate DER 长度必须在 1 到 65536 字节之间");
        }
        return checked;
    }
}
