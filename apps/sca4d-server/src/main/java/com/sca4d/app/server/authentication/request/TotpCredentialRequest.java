package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 使用短期 enrollment 激活或替换 TOTP Credential。 */
public class TotpCredentialRequest {

    @NotBlank
    @Size(max = 64)
    private @Nullable String enrollmentId;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private @Nullable String code;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public TotpCredentialRequest() {
    }

    /**
     * 返回短期 enrollment 标识。
     *
     * @return 短期 enrollment 标识
     */
    public @NotNull String getEnrollmentId() {
        return Objects.requireNonNull(enrollmentId, "TOTP enrollment 标识不能为空");
    }

    /**
     * 设置短期 enrollment 标识。
     *
     * @param enrollmentId 短期 enrollment 标识
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
    public void setEnrollmentId(@Nullable String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * 返回首次六位验证码。
     *
     * @return 只在当前请求使用的六位验证码
     */
    public @NotNull String getCode() {
        return Objects.requireNonNull(code, "TOTP 验证码不能为空");
    }

    /**
     * 设置首次六位验证码。
     *
     * @param code 六位验证码
     */
    public void setCode(@Nullable String code) {
        this.code = code;
    }

    @Override
    public @NotNull String toString() {
        return "TotpCredentialRequest[enrollmentId=" + enrollmentId + ", code=protected]";
    }
}
