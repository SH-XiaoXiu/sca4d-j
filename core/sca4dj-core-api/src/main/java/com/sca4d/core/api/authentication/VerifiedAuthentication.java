package com.sca4d.core.api.authentication;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 保存一种认证方法成功验证的账号、认证记录以及完成动作前可以复核的非秘密引用。 */
@SuppressWarnings("ClassCanBeRecord")
public final class VerifiedAuthentication {

    private final Account account;

    private final Authentication authentication;

    private final String reference;

    private final Version version;

    private final Instant validUntil;

    /**
     * 创建认证方法完成一次成功验证后交给认证引擎的结果。
     *
     * @param account 已验证的 Account
     * @param authentication 可以用于动作判断或建立 Session 的认证记录
     * @param reference 认证方法能够重新定位 Credential 或外部身份的非秘密引用
     * @param version 验证成功时对应的 Credential 或外部身份版本
     * @param validUntil 认证事实自身的最晚有效时间；没有独立期限时为 {@code null}
     * @throws NullPointerException 任一参数为空时抛出
     * @throws IllegalArgumentException Account 与认证记录不一致，或引用为空白时抛出
     */
    public VerifiedAuthentication(
            @NotNull Account account,
            @NotNull Authentication authentication,
            @NotNull String reference,
            @NotNull Version version,
            @Nullable Instant validUntil) {
        this.account = Objects.requireNonNull(account, "认证 Account 不能为空");
        this.authentication = Objects.requireNonNull(authentication, "认证记录不能为空");
        this.reference = TextUtils.requireText(reference, "认证引用");
        this.version = Objects.requireNonNull(version, "认证版本不能为空");
        this.validUntil = validUntil;
        if (account.accountId() != authentication.accountId()) {
            throw new IllegalArgumentException("认证记录不属于指定 Account");
        }
        if (validUntil != null && !validUntil.isAfter(authentication.authenticatedAt())) {
            throw new IllegalArgumentException("认证最晚有效时间必须晚于认证完成时间");
        }
    }

    /**
     * 读取认证方法从 Proof 确认的账号。
     *
     * @return 已验证 Account
     */
    public @NotNull Account account() {
        return account;
    }

    /**
     * 读取满足要求后可以用于动作判断或建立 Session 的认证记录。
     *
     * @return Account 标识与认证账号一致的记录
     */
    public @NotNull Authentication authentication() {
        return authentication;
    }

    /**
     * 读取认证方法重新定位 Credential 或外部身份所需的值。
     *
     * @return 不包含密码、验证码、密钥或令牌的引用
     */
    public @NotNull String reference() {
        return reference;
    }

    /**
     * 读取认证成功时对应的 Credential 或外部身份版本。
     *
     * @return 完成认证前必须保持有效的版本
     */
    public @NotNull Version version() {
        return version;
    }

    /**
     * 读取认证方法确定的最晚有效时间。
     *
     * @return X.509 等限时 Credential 的失效时间；没有独立期限时为空
     */
    public @NotNull Optional<Instant> validUntil() {
        return Optional.ofNullable(validUntil);
    }
}
