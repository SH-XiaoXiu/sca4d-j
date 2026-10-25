package com.sca4d.notification.email.sender.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.notification.email.sender.SmtpSecurity;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 保存 Email Sender Mapper 返回的完整数据库列。 */
@SuppressWarnings("ClassCanBeRecord") // 项目使用普通 class，不使用 Java record。
public class EmailSenderState {

    private final @NotNull SmtpSecurity security;

    private final @NotNull String fromAddress;

    private final @Nullable String fromName;

    private final @NotNull String host;

    private final @NotNull String name;

    private final @Nullable String passwordKeyId;

    private final @Nullable String replyTo;

    private final @Nullable String username;

    private final @NotNull Version version;

    private final boolean enabled;

    private final byte @Nullable [] passwordCiphertext;

    private final int port;

    private final long senderId;

    /**
     * @param senderId SMTP 配置标识
     * @param name 管理显示名称
     * @param host SMTP 主机
     * @param port SMTP 端口
     * @param security TLS 模式
     * @param username 可选认证用户名
     * @param passwordKeyId 可选密码密钥标识
     * @param passwordCiphertext 可选密码密文
     * @param fromAddress 发件邮箱
     * @param fromName 可选发件显示名
     * @param replyTo 可选回复邮箱
     * @param enabled 是否启用
     * @param version 当前版本
     */
    public EmailSenderState(
            long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            @Nullable String passwordKeyId,
            byte @Nullable [] passwordCiphertext,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo,
            boolean enabled,
            @NotNull Version version) {
        this.senderId = senderId;
        this.name = name;
        this.host = host;
        this.port = port;
        this.security = security;
        this.username = username;
        this.passwordKeyId = passwordKeyId;
        this.passwordCiphertext = passwordCiphertext == null ? null : passwordCiphertext.clone();
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.replyTo = replyTo;
        this.enabled = enabled;
        this.version = version;
    }

    /** @return SMTP 配置标识 */
    @Contract(pure = true)
    public long senderId() {
        return senderId;
    }

    /** @return 管理显示名称 */
    @Contract(pure = true)
    public @NotNull String name() {
        return name;
    }

    /** @return SMTP 主机 */
    @Contract(pure = true)
    public @NotNull String host() {
        return host;
    }

    /** @return SMTP 端口 */
    @Contract(pure = true)
    public int port() {
        return port;
    }

    /** @return TLS 模式 */
    @Contract(pure = true)
    public @NotNull SmtpSecurity security() {
        return security;
    }

    /** @return 可选认证用户名 */
    @Contract(pure = true)
    public @Nullable String username() {
        return username;
    }

    /** @return 可选密码密钥标识 */
    @Contract(pure = true)
    public @Nullable String passwordKeyId() {
        return passwordKeyId;
    }

    /** @return 可选密码密文副本 */
    @Contract(pure = true)
    public byte @Nullable [] passwordCiphertext() {
        return passwordCiphertext == null ? null : passwordCiphertext.clone();
    }

    /** @return 发件邮箱 */
    @Contract(pure = true)
    public @NotNull String fromAddress() {
        return fromAddress;
    }

    /** @return 可选发件显示名 */
    @Contract(pure = true)
    public @Nullable String fromName() {
        return fromName;
    }

    /** @return 可选回复邮箱 */
    @Contract(pure = true)
    public @Nullable String replyTo() {
        return replyTo;
    }

    /** @return 是否启用 */
    @Contract(pure = true)
    public boolean enabled() {
        return enabled;
    }

    /** @return 当前版本 */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
