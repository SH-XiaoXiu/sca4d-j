package com.sca4d.app.server.platform.view;

import com.sca4d.auth.password.credential.PasswordCredential;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回不包含摘要的密码凭据状态和更新时间。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class PasswordView {

    private final @NotNull String status;

    private final @NotNull Instant updatedAt;

    private PasswordView(@NotNull String status, @NotNull Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    /**
     * 从 Password 模块凭据创建 HTTP 视图。
     *
     * @param credential Password 模块凭据
     * @return 不含摘要的 HTTP 视图
     */
    @CheckReturnValue
    public static @NotNull PasswordView from(@NotNull PasswordCredential credential) {
        PasswordCredential checked = Objects.requireNonNull(credential, "Password Credential 不能为空");
        return new PasswordView(checked.status().value(), checked.updatedAt());
    }

    /**
     * 返回凭据状态。
     *
     * @return 凭据状态
     */
    public @NotNull String getStatus() {
        return status;
    }

    /**
     * 返回最近设置密码的时间。
     *
     * @return 最近设置密码的时间
     */
    public @NotNull Instant getUpdatedAt() {
        return updatedAt;
    }

}
