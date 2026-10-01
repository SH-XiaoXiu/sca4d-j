package com.sca4d.iam.base;

import com.sca4d.iam.identity.IdentityStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Platform Account 目录返回的身份、用户名和状态。 */
@SuppressWarnings("ClassCanBeRecord")
public class PlatformAccountEntry {

    private final @NotNull IdentityStatus status;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    private final @NotNull Long accountId;

    private final @NotNull Long principalId;

    private final @NotNull Long subjectId;

    private final @NotNull String username;

    private final boolean protectedRoot;

    /**
     * 创建 Platform Account 目录项。
     *
     * @param accountId 账号标识
     * @param subjectId 账号所属 Subject 标识
     * @param principalId 账号唯一 PlatformPrincipal 标识
     * @param username 当前用户名标识的展示值
     * @param status 账号状态
     * @param createdAt 账号创建时间
     * @param updatedAt 账号最近更新时间
     * @param protectedRoot 是否为受保护 ROOT 账号
     */
    public PlatformAccountEntry(
            @NotNull Long accountId,
            @NotNull Long subjectId,
            @NotNull Long principalId,
            @NotNull String username,
            @NotNull IdentityStatus status,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            boolean protectedRoot) {
        this.accountId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "Subject 标识不能为空");
        this.principalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        this.username = Objects.requireNonNull(username, "用户名不能为空");
        this.status = Objects.requireNonNull(status, "Account 状态不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "Account 创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Account 更新时间不能为空");
        this.protectedRoot = protectedRoot;
    }

    /** @return Account 标识 */
    @Contract(pure = true)
    public @NotNull Long accountId() {
        return accountId;
    }

    /** @return Subject 标识 */
    @Contract(pure = true)
    public @NotNull Long subjectId() {
        return subjectId;
    }

    /** @return 唯一 PlatformPrincipal 标识 */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /** @return 用户名展示值 */
    @Contract(pure = true)
    public @NotNull String username() {
        return username;
    }

    /** @return Account 状态 */
    @Contract(pure = true)
    public @NotNull IdentityStatus status() {
        return status;
    }

    /** @return Account 创建时间 */
    @Contract(pure = true)
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /** @return Account 最近更新时间 */
    @Contract(pure = true)
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }

    /** @return {@code true} 表示该账号不能停用 */
    @Contract(pure = true)
    public boolean protectedRoot() {
        return protectedRoot;
    }
}
