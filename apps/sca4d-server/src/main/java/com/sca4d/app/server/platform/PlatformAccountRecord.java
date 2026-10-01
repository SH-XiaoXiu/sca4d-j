package com.sca4d.app.server.platform;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.iam.base.PlatformAccountEntry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 组合 IAM Platform Account 目录项与示例 App 用户资料。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PlatformAccountRecord {

    private final @NotNull PlatformAccountEntry account;

    private final @NotNull PlatformUserProfile profile;

    /**
     * 创建完整 Platform Account 应用结果。
     *
     * @param account IAM 账号目录项
     * @param profile App 用户资料
     */
    public PlatformAccountRecord(
            @NotNull PlatformAccountEntry account,
            @NotNull PlatformUserProfile profile) {
        this.account = Objects.requireNonNull(account, "Platform Account 不能为空");
        this.profile = Objects.requireNonNull(profile, "Platform User Profile 不能为空");
        if (!account.principalId().equals(profile.principalId())) {
            throw new IllegalArgumentException("Platform Account 与 Profile 必须属于同一 Principal");
        }
    }

    /**
     * 返回 IAM 账号目录项。
     *
     * @return IAM 账号目录项
     */
    @Contract(pure = true)
    public @NotNull PlatformAccountEntry account() {
        return account;
    }

    /**
     * 返回 App 用户资料。
     *
     * @return App 用户资料
     */
    @Contract(pure = true)
    public @NotNull PlatformUserProfile profile() {
        return profile;
    }
}
