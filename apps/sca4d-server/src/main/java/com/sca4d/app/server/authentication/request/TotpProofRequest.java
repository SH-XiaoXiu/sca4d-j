package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收只在当前请求中使用的六位 TOTP 验证码。 */
public class TotpProofRequest {

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private @Nullable String code;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public TotpProofRequest() {
    }

    /**
     * 返回本次提交的 TOTP 验证码。
     *
     * @return 六位 TOTP 验证码
     */
    public @NotNull String getCode() {
        return Objects.requireNonNull(code, "TOTP 验证码不能为空");
    }

    /**
     * 设置本次提交的 TOTP 验证码。
     *
     * @param code 六位 TOTP 验证码
     */
    public void setCode(@Nullable String code) {
        this.code = code;
    }

    @Override
    public @NotNull String toString() {
        return "TotpProofRequest[code=protected]";
    }
}
