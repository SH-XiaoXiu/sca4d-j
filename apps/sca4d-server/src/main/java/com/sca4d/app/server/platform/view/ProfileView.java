package com.sca4d.app.server.platform.view;

import com.sca4d.app.server.entity.PlatformUserProfile;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 返回当前账号的示例 App 用户资料。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class ProfileView {

    private final @NotNull Instant updatedAt;

    private final @Nullable Long avatarFileId;

    private final @NotNull String displayName;

    private final @Nullable String email;

    private final @Nullable String phoneNumber;

    private final long principalId;

    private ProfileView(
            long principalId,
            @NotNull String displayName,
            @Nullable String email,
            @Nullable String phoneNumber,
            @Nullable Long avatarFileId,
            @NotNull Instant updatedAt) {
        this.principalId = principalId;
        this.displayName = displayName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.avatarFileId = avatarFileId;
        this.updatedAt = updatedAt;
    }

    /**
     * 从 App 用户资料创建 HTTP 视图。
     *
     * @param profile App 用户资料
     * @return HTTP 资料视图
     */
    @CheckReturnValue
    public static @NotNull ProfileView from(@NotNull PlatformUserProfile profile) {
        PlatformUserProfile checked = Objects.requireNonNull(profile, "Platform User Profile 不能为空");
        return new ProfileView(
                checked.principalId(),
                checked.displayName(),
                checked.email(),
                checked.phoneNumber(),
                checked.avatarFileId(),
                checked.updatedAt());
    }

    /**
     * 返回资料所属 Principal 标识。
     *
     * @return Principal 标识
     */
    public long getPrincipalId() {
        return principalId;
    }

    /**
     * 返回展示名称。
     *
     * @return 展示名称
     */
    public @NotNull String getDisplayName() {
        return displayName;
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
     * 返回可选的规范 E.164 手机号。
     *
     * @return 可选规范 E.164 手机号
     */
    public @Nullable String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * 返回可选头像文件标识。
     *
     * @return 可选头像文件标识
     */
    public @Nullable Long getAvatarFileId() {
        return avatarFileId;
    }

    /**
     * 返回最近更新时间。
     *
     * @return 最近更新时间
     */
    public @NotNull Instant getUpdatedAt() {
        return updatedAt;
    }

}
