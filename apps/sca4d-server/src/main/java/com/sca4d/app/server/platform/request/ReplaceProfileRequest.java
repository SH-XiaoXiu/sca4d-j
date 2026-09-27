package com.sca4d.app.server.platform.request;

import com.sca4d.core.spring.validation.DisplayName;
import com.sca4d.core.spring.validation.PhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收当前账号完整替换的 App 用户资料。 */
public class ReplaceProfileRequest {

    @NotBlank
    @DisplayName
    private @Nullable String displayName;

    @Email
    @Size(max = 320)
    private @Nullable String email;

    @PhoneNumber
    private @Nullable String phoneNumber;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public ReplaceProfileRequest() {
    }

    /**
     * 返回展示名称。
     *
     * @return 展示名称
     */
    public @NotNull String getDisplayName() {
        return Objects.requireNonNull(displayName, "展示名称不能为空");
    }

    /**
     * 设置展示名称。
     *
     * @param displayName 展示名称
     */
    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    /**
     * 返回可选邮箱。
     *
     * @return 可选邮箱
     */
    public @Nullable String getEmail() {
        return email;
    }

    /**
     * 设置可选邮箱。
     *
     * @param email 可选邮箱
     */
    public void setEmail(@Nullable String email) {
        this.email = email;
    }

    /**
     * 返回可选的规范 E.164 手机号。
     *
     * @return 可选规范 E.164 手机号
     */
    public @Nullable String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * 设置可选的规范 E.164 手机号。
     *
     * @param phoneNumber 可选规范 E.164 手机号
     */
    public void setPhoneNumber(@Nullable String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
