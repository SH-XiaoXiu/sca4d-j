package com.sca4d.notification.email.sender;

import com.sca4d.notification.email.EmailAttachment;
import com.sca4d.notification.email.RenderedEmail;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 使用一项 SMTP 配置测试连接或发送已经渲染的 Email。 */
public interface EmailGateway {

    /**
     * @param configuration SMTP 地址、认证密文和发件资料
     * @throws RuntimeException 连接、TLS 或认证失败时抛出
     */
    void testConnection(@NotNull EmailSenderConfiguration configuration);

    /**
     * @param configuration SMTP 地址、认证密文和发件资料
     * @param recipient 已校验单个收件邮箱
     * @param email 已完成安全处理的 Email 内容
     * @throws RuntimeException SMTP 提交失败时抛出
     */
    void send(
            @NotNull EmailSenderConfiguration configuration,
            @NotNull String recipient,
            @NotNull RenderedEmail email);

    /**
     * 发送带附件的 Email。默认实现只接受空附件，支持附件的 Transport 必须覆盖本方法。
     */
    default void send(
            @NotNull EmailSenderConfiguration configuration,
            @NotNull String recipient,
            @NotNull RenderedEmail email,
            @NotNull List<EmailAttachment> attachments) {
        if (!attachments.isEmpty()) {
            throw new IllegalArgumentException("当前 Email Transport 不支持附件");
        }
        send(configuration, recipient, email);
    }
}
