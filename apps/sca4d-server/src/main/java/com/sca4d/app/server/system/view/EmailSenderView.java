package com.sca4d.app.server.system.view;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.notification.email.sender.EmailSender;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 返回不包含 SMTP 密码或密文的 Email Sender 配置。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class EmailSenderView {

    private final long senderId;

    private final @NotNull String name;

    private final @NotNull String host;

    private final int port;

    private final @NotNull String security;

    private final @Nullable String username;

    private final boolean passwordConfigured;

    private final @NotNull String fromAddress;

    private final @Nullable String fromName;

    private final @Nullable String replyTo;

    private final boolean enabled;

    private final @NotNull Version revision;

    private EmailSenderView(@NotNull EmailSender sender) {
        this.senderId = sender.senderId();
        this.name = sender.name();
        this.host = sender.host();
        this.port = sender.port();
        this.security = sender.security().name();
        this.username = sender.username();
        this.passwordConfigured = sender.passwordConfigured();
        this.fromAddress = sender.fromAddress();
        this.fromName = sender.fromName();
        this.replyTo = sender.replyTo();
        this.enabled = sender.enabled();
        this.revision = sender.version();
    }

    /** @return SMTP 配置的安全 HTTP 投影 */
    @CheckReturnValue
    public static @NotNull EmailSenderView from(@NotNull EmailSender sender) {
        return new EmailSenderView(Objects.requireNonNull(sender, "SMTP 配置不能为空"));
    }

    /** @return SMTP 配置标识 */
    public long getSenderId() {
        return senderId;
    }

    /** @return 管理名称 */
    public @NotNull String getName() {
        return name;
    }

    /** @return SMTP 主机 */
    public @NotNull String getHost() {
        return host;
    }

    /** @return SMTP 端口 */
    public int getPort() {
        return port;
    }

    /** @return SMTP 安全模式 */
    public @NotNull String getSecurity() {
        return security;
    }

    /** @return 可选 SMTP 用户名 */
    public @Nullable String getUsername() {
        return username;
    }

    /** @return 是否已保存配对密码 */
    public boolean isPasswordConfigured() {
        return passwordConfigured;
    }

    /** @return 发件邮箱 */
    public @NotNull String getFromAddress() {
        return fromAddress;
    }

    /** @return 可选发件显示名 */
    public @Nullable String getFromName() {
        return fromName;
    }

    /** @return 可选回复邮箱 */
    public @Nullable String getReplyTo() {
        return replyTo;
    }

    /** @return 是否参与投递 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return SMTP 配置当前版本 */
    public long getVersion() {
        return revision.value();
    }

    /** @return 生成当前完整 SMTP 配置表示的版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
