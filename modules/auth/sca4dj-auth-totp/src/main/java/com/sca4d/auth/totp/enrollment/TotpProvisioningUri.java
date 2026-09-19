package com.sca4d.auth.totp.enrollment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 构造交给具体 App 生成二维码的标准 otpauth URI。 */
final class TotpProvisioningUri {

    private TotpProvisioningUri() {
    }

    static String create(String issuer, String accountLabel, String secret) {
        String checkedIssuer = requireText(issuer, "TOTP issuer");
        String checkedLabel = requireText(accountLabel, "TOTP account label");
        // label 的 issuer 与 account 分段分别编码，保留中间结构冒号；查询参数使用同一 issuer 值。
        // algorithm、digits、period 必须与 TotpCodeVerifier 的 Google Authenticator 配置完全一致。
        String encodedIssuer = encode(checkedIssuer);
        return "otpauth://totp/"
                + encodedIssuer
                + ":"
                + encode(checkedLabel)
                + "?secret="
                + requireText(secret, "TOTP secret")
                + "&issuer="
                + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String requireText(String value, String name) {
        String checked = Objects.requireNonNull(value, name + " 不能为空");
        if (checked.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return checked;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
