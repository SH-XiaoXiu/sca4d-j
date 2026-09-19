package com.sca4d.app.server.platform.view;

import com.sca4d.app.server.platform.PlatformAccountRecord;
import com.sca4d.iam.identity.IdentityStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 返回 Platform Account 的用户名、状态和 App 资料。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public final class PlatformAccountView {

    private final long id;

    private final @NotNull String username;

    private final @NotNull IdentityStatus status;

    private final @NotNull String displayName;

    private final @Nullable String email;

    private final @Nullable String phoneNumber;

    private final @Nullable Long avatarFileId;

    private final boolean protectedRoot;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    private PlatformAccountView(
            long id,
            @NotNull String username,
            @NotNull IdentityStatus status,
            @NotNull String displayName,
            @Nullable String email,
            @Nullable String phoneNumber,
            @Nullable Long avatarFileId,
            boolean protectedRoot,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.status = status;
        this.displayName = displayName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.avatarFileId = avatarFileId;
        this.protectedRoot = protectedRoot;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 从应用账号结果创建 HTTP 视图。
     *
     * @param record IAM 账号与 App 资料
     * @return HTTP 账号视图
     */
    @CheckReturnValue
    public static @NotNull PlatformAccountView from(@NotNull PlatformAccountRecord record) {
        PlatformAccountRecord checked = Objects.requireNonNull(record, "Platform Account 记录不能为空");
        return new PlatformAccountView(
                checked.account().accountId(),
                checked.account().username(),
                checked.account().status(),
                checked.profile().displayName(),
                checked.profile().email(),
                checked.profile().phoneNumber(),
                checked.profile().avatarFileId(),
                checked.account().protectedRoot(),
                checked.account().createdAt(),
                checked.account().updatedAt());
    }

    /**
     * 返回 Account 标识。
     *
     * @return Account 标识
     */
    public long getId() {
        return id;
    }

    /**
     * 返回登录用户名。
     *
     * @return 登录用户名
     */
    public @NotNull String getUsername() {
        return username;
    }

    /**
     * 返回 Account 状态。
     *
     * @return Account 状态
     */
    public @NotNull IdentityStatus getStatus() {
        return status;
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
     * 返回是否为受保护 ROOT Account。
     *
     * @return 受保护时为 {@code true}
     */
    public boolean isProtectedRoot() {
        return protectedRoot;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public @NotNull Instant getCreatedAt() {
        return createdAt;
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
