package com.sca4d.app.server.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 SMS Provider 的类型、凭据引用和选择优先级。 */
@SuppressWarnings("unused")
public final class SmsProviderRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String code;

    @NotBlank
    @Size(max = 128)
    private @Nullable String type;

    @NotBlank
    @Size(max = 128)
    private @Nullable String credentialRef;

    private int priority = 100;

    public @NotNull String getCode() {
        return Objects.requireNonNull(code, "Provider 编码不能为空");
    }

    public void setCode(@Nullable String code) {
        this.code = code;
    }

    public @NotNull String getType() {
        return Objects.requireNonNull(type, "Provider 类型不能为空");
    }

    public void setType(@Nullable String type) {
        this.type = type;
    }

    public @NotNull String getCredentialRef() {
        return Objects.requireNonNull(credentialRef, "Provider 凭据引用不能为空");
    }

    public void setCredentialRef(@Nullable String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
