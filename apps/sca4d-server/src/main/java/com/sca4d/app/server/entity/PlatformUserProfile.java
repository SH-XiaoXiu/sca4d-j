package com.sca4d.app.server.entity;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存示例 App 为 Platform 用户维护的展示名称和可选联系方式。 */
@SuppressWarnings("ClassCanBeRecord")
public final class PlatformUserProfile implements Resource {

    /** Platform 用户资料使用的稳定 Resource 类型。 */
    public static final @NotNull String RESOURCE_TYPE_VALUE = "PLATFORM:USERS:PROFILES";

    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofColon(RESOURCE_TYPE_VALUE);

    private final @NotNull Long principalId;

    private final @NotNull String displayName;

    private final @Nullable String email;

    private final @Nullable String phoneNumber;

    private final @Nullable Long avatarFileId;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    /**
     * 创建 Platform 用户资料。
     *
     * @param principalId IAM Principal 标识
     * @param displayName 展示名称
     * @param email 可选邮箱
     * @param phoneNumber 可选规范 E.164 手机号
     * @param avatarFileId 可选头像文件标识
     * @param createdAt 创建时间
     * @param updatedAt 最近更新时间
     */
    public PlatformUserProfile(
            @NotNull Long principalId,
            @NotNull String displayName,
            @Nullable String email,
            @Nullable String phoneNumber,
            @Nullable Long avatarFileId,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt) {
        this.principalId = Objects.requireNonNull(principalId, "Profile Principal 标识不能为空");
        this.displayName = Objects.requireNonNull(displayName, "Profile 展示名称不能为空");
        this.email = email;
        this.phoneNumber = phoneNumber;
        if (avatarFileId != null && avatarFileId <= 0) {
            throw new IllegalArgumentException("头像文件标识必须为正数");
        }
        this.avatarFileId = avatarFileId;
        this.createdAt = Objects.requireNonNull(createdAt, "Profile 创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Profile 更新时间不能为空");
    }

    /**
     * 返回 IAM Principal 标识。
     *
     * @return IAM Principal 标识
     */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public @NotNull TreeCode type() {
        return RESOURCE_TYPE;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public @NotNull String id() {
        return principalId.toString();
    }

    /**
     * 返回展示名称。
     *
     * @return 展示名称
     */
    @Contract(pure = true)
    public @NotNull String displayName() {
        return displayName;
    }

    /**
     * 返回可选邮箱。
     *
     * @return 可选邮箱
     */
    @Contract(pure = true)
    public @Nullable String email() {
        return email;
    }

    /**
     * 返回可选的规范 E.164 手机号。
     *
     * @return 可选规范 E.164 手机号
     */
    @Contract(pure = true)
    public @Nullable String phoneNumber() {
        return phoneNumber;
    }

    /**
     * 返回可选头像文件标识。
     *
     * @return 可选头像文件标识
     */
    @Contract(pure = true)
    public @Nullable Long avatarFileId() {
        return avatarFileId;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    @Contract(pure = true)
    @SuppressWarnings("unused") // 保留完整持久化模型，供后续应用流程读取创建时间。
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /**
     * 返回最近更新时间。
     *
     * @return 最近更新时间
     */
    @Contract(pure = true)
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }

}
