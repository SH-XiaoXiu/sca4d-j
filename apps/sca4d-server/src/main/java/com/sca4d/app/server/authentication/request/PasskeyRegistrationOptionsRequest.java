package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收浏览器账号选择器显示的 Passkey 账号名称。 */
public final class PasskeyRegistrationOptionsRequest {

    @NotBlank
    @Size(max = 100)
    private @Nullable String displayName;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public PasskeyRegistrationOptionsRequest() {
    }

    public @NotNull String getDisplayName() {
        return Objects.requireNonNull(displayName, "Passkey 显示名称不能为空");
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }
}
