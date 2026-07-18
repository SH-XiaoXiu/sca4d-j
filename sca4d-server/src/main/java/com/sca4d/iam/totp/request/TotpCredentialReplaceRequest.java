package com.sca4d.iam.totp.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 使用短期 enrollment 替换指定版本 TOTP Credential 的请求。 */
public class TotpCredentialReplaceRequest {

    @NotBlank
    @Size(max = 64)
    private String enrollmentId;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /** @return 只在当前替换请求中使用的六位验证码 */
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return "TotpCredentialReplaceRequest[enrollmentId=" + enrollmentId + ", code=protected]";
    }
}
