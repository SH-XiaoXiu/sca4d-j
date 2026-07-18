package com.sca4d.mail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 使用数据库中的 SMTP 配置测试连接或发送一封邮件。 */
public interface MailService {

    /**
     * 使用当前配置建立并关闭 SMTP 连接；停用配置也允许测试。
     *
     * @throws com.sca4d.common.exception.BizException 配置不存在或服务器拒绝连接时抛出
     */
    void testConnection(@NotNull Long senderId);

    /**
     * 使用启用的配置发送一封文本或 HTML 邮件。
     *
     * @param recipient 单个收件邮箱
     * @param subject 已完成模板渲染的邮件主题
     * @param text 文本内容
     * @param html HTML 内容；为空时只发送文本
     * @throws com.sca4d.common.exception.BizException 配置停用、地址无效或 SMTP 拒绝邮件时抛出
     */
    void send(
            @NotNull Long senderId,
            @NotNull String recipient,
            @NotNull String subject,
            @NotNull String text,
            @Nullable String html);
}
