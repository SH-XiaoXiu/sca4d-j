package com.sca4d.app.server.system.request;

import com.sca4d.core.spring.validation.Req;
import com.sca4d.notification.email.sender.SmtpSecurity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收完整 SMTP 配置，并明确本次是否替换或清除已保存密码。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public class ReplaceEmailSenderRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String name;

    @NotBlank
    @Size(max = 253)
    private @Nullable String host;

    @Min(1)
    @Max(65535)
    private int port;

    @Req.NotNull
    private @Nullable SmtpSecurity security;

    @Size(max = 320)
    private @Nullable String username;

    private boolean passwordChanged;

    @Size(max = 4096)
    private @Nullable String password;

    @NotBlank
    @Size(max = 320)
    private @Nullable String fromAddress;

    @Size(max = 256)
    private @Nullable String fromName;

    @Size(max = 320)
    private @Nullable String replyTo;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public ReplaceEmailSenderRequest() {
    }

    /** @return SMTP 配置管理名称 */
    public @NotNull String getName() {
        return Objects.requireNonNull(name, "SMTP 配置名称不能为空");
    }

    /** @param name SMTP 配置管理名称 */
    public void setName(@Nullable String name) {
        this.name = name;
    }

    /** @return SMTP 主机 */
    public @NotNull String getHost() {
        return Objects.requireNonNull(host, "SMTP 主机不能为空");
    }

    /** @param host SMTP 主机 */
    public void setHost(@Nullable String host) {
        this.host = host;
    }

    /** @return SMTP 端口 */
    public int getPort() {
        return port;
    }

    /** @param port SMTP 端口 */
    public void setPort(int port) {
        this.port = port;
    }

    /** @return SMTP 连接安全模式 */
    public @NotNull SmtpSecurity getSecurity() {
        return Objects.requireNonNull(security, "SMTP 安全模式不能为空");
    }

    /** @param security SMTP 连接安全模式 */
    public void setSecurity(@Nullable SmtpSecurity security) {
        this.security = security;
    }

    /** @return 可选 SMTP 认证用户名 */
    public @Nullable String getUsername() {
        return username;
    }

    /** @param username 可选 SMTP 认证用户名 */
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    /** @return 是否用请求中的密码替换或清除已保存密码 */
    public boolean isPasswordChanged() {
        return passwordChanged;
    }

    /** @param passwordChanged 是否修改密码 */
    public void setPasswordChanged(boolean passwordChanged) {
        this.passwordChanged = passwordChanged;
    }

    /** @return 修改后的原始密码；清除密码或不修改时为空 */
    public @Nullable String getPassword() {
        return password;
    }

    /** @param password 修改后的原始密码 */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    /** @return 发件邮箱 */
    public @NotNull String getFromAddress() {
        return Objects.requireNonNull(fromAddress, "发件邮箱不能为空");
    }

    /** @param fromAddress 发件邮箱 */
    public void setFromAddress(@Nullable String fromAddress) {
        this.fromAddress = fromAddress;
    }

    /** @return 可选发件显示名 */
    public @Nullable String getFromName() {
        return fromName;
    }

    /** @param fromName 可选发件显示名 */
    public void setFromName(@Nullable String fromName) {
        this.fromName = fromName;
    }

    /** @return 可选回复邮箱 */
    public @Nullable String getReplyTo() {
        return replyTo;
    }

    /** @param replyTo 可选回复邮箱 */
    public void setReplyTo(@Nullable String replyTo) {
        this.replyTo = replyTo;
    }

    @Override
    public @NotNull String toString() {
        return "ReplaceEmailSenderRequest[name=" + name + ", host=" + host
                + ", passwordChanged=" + passwordChanged + ", password=protected]";
    }
}
