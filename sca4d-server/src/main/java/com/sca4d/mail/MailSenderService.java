package com.sca4d.mail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 管理多项 SMTP 地址、认证信息、默认发件资料和启停状态。 */
public interface MailSenderService {

    /** @return 全部未删除配置，不包含密码、key-id、nonce 或密文 */
    @NotNull
    List<MailSender> senders();

    /**
     * @return 指定安全配置，不包含密码、key-id、nonce 或密文
     * @throws com.sca4d.common.exception.BizException 配置不存在时抛出
     */
    @NotNull
    MailSender require(@NotNull Long senderId);

    /**
     * 创建一项默认停用的 SMTP 配置。
     *
     * @param username SMTP 登录用户名；匿名连接时为空
     * @param password SMTP 登录密码；匿名连接时为空
     * @return 新配置主键
     */
    @NotNull
    Long create(
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            @Nullable String password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo);

    /**
     * 修改 SMTP 地址、认证信息和默认发件资料。
     *
     * @param password 新密码；为空时保留当前密码
     * @param clearPassword true 时清空当前密码，且 username 必须同时为空
     * @param version 当前乐观锁版本
     */
    void update(
            @NotNull Long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            @Nullable String password,
            boolean clearPassword,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo,
            long version);

    /** 按当前版本启用或停用邮件发送配置。 */
    void changeStatus(
            @NotNull Long senderId, @NotNull MailSenderStatus status, long version);

    /**
     * 删除已经停用的邮件发送配置。
     *
     * @throws com.sca4d.common.exception.BizException 配置仍启用时抛出
     */
    void delete(@NotNull Long senderId, long version);
}
