package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收已知 Account 的 STEP_UP 密码证明。 */
public class PasswordProofRequest {

    @NotBlank
    private @Nullable String password;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public PasswordProofRequest() {
    }

    /**
     * 返回本次提交的原始密码。
     *
     * @return 只交给 Password 方法的原始密码
     */
    public @NotNull String getPassword() {
        return Objects.requireNonNull(password, "密码不能为空");
    }

    /**
     * 设置本次提交的原始密码。
     *
     * @param password 原始密码
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    @Override
    public @NotNull String toString() {
        return "PasswordProofRequest[password=protected]";
    }
}
