package com.sca4d.notification.email.sender;

import com.sca4d.core.api.security.EncryptedSecret;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 组合安全 SMTP 配置和仅供建立连接使用的可选密码密文。 */
public final class EmailSenderConfiguration {

    private final @NotNull EmailSender sender;

    private final @Nullable EncryptedSecret password;

    /**
     * @param sender 不包含密文的 SMTP 配置
     * @param password 可选密码密文，必须与配置的认证状态一致
     */
    public EmailSenderConfiguration(
            @NotNull EmailSender sender,
            @Nullable EncryptedSecret password) {
        this.sender = Objects.requireNonNull(sender, "SMTP 配置不能为空");
        if (sender.passwordConfigured() != (password != null)) {
            throw new IllegalArgumentException("SMTP 密码密文与配置状态不一致");
        }
        this.password = password;
    }

    /** @return 不包含密文的 SMTP 配置 */
    @Contract(pure = true)
    public @NotNull EmailSender sender() {
        return sender;
    }

    /** @return 可选密码密文 */
    @Contract(pure = true)
    public @Nullable EncryptedSecret password() {
        return password;
    }
}
