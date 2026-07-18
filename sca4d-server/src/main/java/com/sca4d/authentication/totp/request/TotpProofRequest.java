package com.sca4d.authentication.totp.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 向 Authentication 提交的 TOTP 六位验证码。 */
public class TotpProofRequest {

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;

    /** @return 只在当前请求中交给 TOTP AuthMethod 的验证码 */
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return "TotpProofRequest[code=protected]";
    }
}
