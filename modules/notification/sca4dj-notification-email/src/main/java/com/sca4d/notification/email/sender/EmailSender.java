package com.sca4d.notification.email.sender;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 保存可安全返回给管理调用方的 SMTP 地址、发件资料、状态和版本。
 */
public class EmailSender {

    /**
     * 供 Java 注解参数使用的资源类型文本。
     */
    public static final @NotNull String RESOURCE_TYPE_VALUE = "NOTIFICATION/EMAIL/SENDERS";

    /**
     * Email Sender 写入审计记录时使用的资源类型。
     */
    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofSlash("NOTIFICATION/EMAIL/SENDERS");

    private final @NotNull SmtpSecurity security;

    private final @NotNull String fromAddress;

    private final @Nullable String fromName;

    private final @NotNull String host;

    private final @NotNull String name;

    private final @Nullable String replyTo;

    private final @Nullable String username;

    private final @NotNull Version version;

    private final boolean enabled;

    private final boolean passwordConfigured;

    private final int port;

    private final long senderId;

    /**
     * @param senderId           SMTP 配置标识
     * @param name               管理显示名称
     * @param host               SMTP 主机
     * @param port               SMTP 端口
     * @param security           TLS 模式
     * @param username           可选认证用户名
     * @param passwordConfigured 是否已经保存配对密码
     * @param fromAddress        发件邮箱
     * @param fromName           可选发件显示名
     * @param replyTo            可选回复邮箱
     * @param enabled            是否参与实际投递
     * @param version            数据库中的记录版本，用于乐观锁比较
     */
    public EmailSender(
            long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            boolean passwordConfigured,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo,
            boolean enabled,
            @NotNull Version version) {
        if (senderId <= 0 || port < 1 || port > 65_535) {
            throw new IllegalArgumentException("SMTP 配置标识和端口必须有效");
        }
        if ((username == null) == passwordConfigured) {
            throw new IllegalArgumentException("SMTP 用户名和密码必须同时配置");
        }
        this.senderId = senderId;
        this.name = Objects.requireNonNull(name, "SMTP 配置名称不能为空");
        this.host = Objects.requireNonNull(host, "SMTP 主机不能为空");
        this.port = port;
        this.security = Objects.requireNonNull(security, "SMTP TLS 模式不能为空");
        this.username = username;
        this.passwordConfigured = passwordConfigured;
        this.fromAddress = Objects.requireNonNull(fromAddress, "SMTP 发件邮箱不能为空");
        this.fromName = fromName;
        this.replyTo = replyTo;
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "SMTP 配置版本不能为空");
    }

    /**
     * @return SMTP 配置标识
     */
    @Contract(pure = true)
    public long senderId() {
        return senderId;
    }

    /**
     * @return 管理显示名称
     */
    @Contract(pure = true)
    public @NotNull String name() {
        return name;
    }

    /**
     * @return SMTP 主机
     */
    @Contract(pure = true)
    public @NotNull String host() {
        return host;
    }

    /**
     * @return SMTP 端口
     */
    @Contract(pure = true)
    public int port() {
        return port;
    }

    /**
     * @return TLS 模式
     */
    @Contract(pure = true)
    public @NotNull SmtpSecurity security() {
        return security;
    }

    /**
     * @return 可选 SMTP 用户名
     */
    @Contract(pure = true)
    public @Nullable String username() {
        return username;
    }

    /**
     * @return 是否已经保存配对密码
     */
    @Contract(pure = true)
    public boolean passwordConfigured() {
        return passwordConfigured;
    }

    /**
     * @return 发件邮箱
     */
    @Contract(pure = true)
    public @NotNull String fromAddress() {
        return fromAddress;
    }

    /**
     * @return 可选发件显示名
     */
    @Contract(pure = true)
    public @Nullable String fromName() {
        return fromName;
    }

    /**
     * @return 可选回复邮箱
     */
    @Contract(pure = true)
    public @Nullable String replyTo() {
        return replyTo;
    }

    /**
     * @return 是否参与实际投递
     */
    @Contract(pure = true)
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return 当前版本
     */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
