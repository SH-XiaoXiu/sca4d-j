package com.sca4d.app.server.platform.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收当前账号的新原始密码。 */
public final class ReplacePasswordRequest {

    @NotBlank
    @Size(min = 8, max = 128)
    private @Nullable String password;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public ReplacePasswordRequest() {
    }

    /**
     * 返回新原始密码。
     *
     * @return 新原始密码
     */
    public @NotNull String getPassword() {
        return Objects.requireNonNull(password, "新密码不能为空");
    }

    /**
     * 设置新原始密码。
     *
     * @param password 新原始密码
     */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }
}
