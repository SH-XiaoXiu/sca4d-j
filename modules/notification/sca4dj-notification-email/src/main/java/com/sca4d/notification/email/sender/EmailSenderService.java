package com.sca4d.notification.email.sender;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.notification.email.EmailAddresses;
import com.sca4d.notification.email.EmailNotificationErrorCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 管理 SMTP 配置、加密密码、启用状态和连接测试。 */
public class EmailSenderService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailSenderService.class);

    private final @NotNull EmailSenderRepository senders;

    private final @NotNull EmailGateway transport;

    private final @NotNull SecretKeyring keyring;

    private final @NotNull IdGenerator ids;

    /** @param senders SMTP 配置持久化语义 @param transport SMTP 操作 @param keyring 密码加密密钥环 @param ids 全局标识生成器 */
    public EmailSenderService(
            @NotNull EmailSenderRepository senders,
            @NotNull EmailGateway transport,
            @NotNull SecretKeyring keyring,
            @NotNull IdGenerator ids) {
        this.senders = Objects.requireNonNull(senders, "EmailSenderRepository 不能为空");
        this.transport = Objects.requireNonNull(transport, "EmailGateway 不能为空");
        this.keyring = Objects.requireNonNull(keyring, "SecretKeyring 不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
    }

    /** @return 全部未删除 SMTP 配置，不包含密码密文 */
    @CheckReturnValue
    public @NotNull List<@NotNull EmailSender> findAll() {
        return senders.findAll();
    }

    /** @return 指定 SMTP 配置；不存在时抛出稳定错误 */
    @CheckReturnValue
    public @NotNull EmailSender loadById(long senderId) {
        return senders.find(senderId)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.SENDER_NOT_FOUND));
    }

    /** 创建默认停用的 SMTP 配置，认证用户名和密码必须同时提供。 */
    @Transactional
    @CheckReturnValue
    public @NotNull EmailSender create(
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            @Nullable String password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo) {
        long senderId = ids.nextId().value();
        CheckedSender checked = validate(
                name,
                host,
                port,
                security,
                username,
                password != null,
                fromAddress,
                fromName,
                replyTo);
        if ((checked.username != null) != (password != null && !password.isBlank())) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_INVALID);
        }
        EncryptedSecret encrypted = password == null ? null : encrypt(senderId, password);
        senders.add(
                senderId,
                checked.name,
                checked.host,
                port,
                security,
                checked.username,
                encrypted,
                checked.fromAddress,
                checked.fromName,
                checked.replyTo);
        LOG.info("SMTP 配置已创建 | senderId={} security={} enabled=false", senderId, security);
        return loadById(senderId);
    }

    /** 按根资源版本条件完整替换 SMTP 地址、认证和发件资料。 */
    @Transactional
    @CheckReturnValue
    public @NotNull EmailSender replace(
            long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            boolean passwordChanged,
            @Nullable String password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo) {
        EmailSender current = loadById(senderId);
        CheckedSender checked = validate(
                name,
                host,
                port,
                security,
                username,
                passwordChanged ? password != null : current.passwordConfigured(),
                fromAddress,
                fromName,
                replyTo);
        if (passwordChanged && ((checked.username != null) != (password != null && !password.isBlank()))) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_INVALID);
        }
        if (!passwordChanged && ((checked.username != null) != current.passwordConfigured())) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_INVALID);
        }
        EncryptedSecret encrypted = passwordChanged && password != null ? encrypt(senderId, password) : null;
        senders.replace(
                senderId,
                checked.name,
                checked.host,
                port,
                security,
                checked.username,
                passwordChanged,
                encrypted,
                checked.fromAddress,
                checked.fromName,
                checked.replyTo);
        LOG.info("SMTP 配置已修改 | senderId={} security={} passwordChanged={}", senderId, security, passwordChanged);
        return loadById(senderId);
    }

    /** 按根资源版本条件修改 SMTP 配置启用状态。 */
    @Transactional
    @CheckReturnValue
    public @NotNull EmailSender setEnabled(long senderId, boolean enabled) {
        EmailSender current = loadById(senderId);
        senders.setEnabled(senderId, enabled);
        LOG.info(
                "SMTP 配置状态已修改 | senderId={} previousEnabled={} enabled={}",
                current.senderId(),
                current.enabled(),
                enabled);
        return loadById(senderId);
    }

    /** 使用指定配置建立并关闭一次 SMTP 连接，不发送邮件。 */
    public void testConnection(long senderId) {
        EmailSenderConfiguration configuration = senders.findConfiguration(senderId)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.SENDER_NOT_FOUND));
        transport.testConnection(configuration);
    }

    /** 按根资源版本条件删除停用 SMTP 配置。 */
    @Transactional
    public void delete(long senderId) {
        EmailSender current = loadById(senderId);
        if (current.enabled()) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_CONFLICT);
        }
        senders.delete(senderId);
        LOG.info("SMTP 配置已删除 | senderId={}", senderId);
    }

    /** 校验并规范 SMTP 地址、认证状态和发件资料。 */
    private static @NotNull CheckedSender validate(
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            boolean passwordConfigured,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo) {
        try {
            String checkedName = requireText(name, 128, "SMTP 配置名称");
            String checkedHost = requireText(host, 253, "SMTP 主机");
            if (port < 1 || port > 65_535 || security == null) {
                throw new IllegalArgumentException("SMTP 端口或 TLS 模式无效");
            }
            String checkedUsername = optionalText(username, 320, "SMTP 用户名");
            if ((checkedUsername != null) != passwordConfigured) {
                throw new IllegalArgumentException("SMTP 用户名和密码必须同时配置");
            }
            return new CheckedSender(
                    checkedName,
                    checkedHost,
                    checkedUsername,
                    EmailAddresses.require(fromAddress),
                    optionalText(fromName, 256, "SMTP 发件显示名"),
                    replyTo == null ? null : EmailAddresses.require(replyTo));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_INVALID, exception);
        }
    }

    /** 加密 SMTP 密码并清除当前方法创建的明文字节。 */
    private @NotNull EncryptedSecret encrypt(long senderId, @NotNull String password) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        try {
            return keyring.activeCipher().encrypt(SmtpEmailGateway.passwordPurpose(senderId), bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /** 校验一项非空白且不超过上限的文本。 */
    private static @NotNull String requireText(@NotNull String value, int maximumLength, @NotNull String label) {
        String checked = Objects.requireNonNull(value, label + "不能为空").trim();
        if (checked.isEmpty() || checked.length() > maximumLength) {
            throw new IllegalArgumentException(label + "长度不正确");
        }
        return checked;
    }

    /** 校验可选文本；空白按未提供处理。 */
    private static @Nullable String optionalText(@Nullable String value, int maximumLength, @NotNull String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, maximumLength, label);
    }

    /** 保存已经规范化但尚未持久化的 SMTP 文本字段。 */
    private static final class CheckedSender {
        private final @NotNull String name;

        private final @NotNull String host;

        private final @Nullable String username;

        private final @NotNull String fromAddress;

        private final @Nullable String fromName;

        private final @Nullable String replyTo;

        /** @param name 名称 @param host 主机 @param username 用户名 @param fromAddress 发件邮箱 @param fromName 发件名 @param replyTo 回复邮箱 */
        private CheckedSender(
                @NotNull String name,
                @NotNull String host,
                @Nullable String username,
                @NotNull String fromAddress,
                @Nullable String fromName,
                @Nullable String replyTo) {
            this.name = name;
            this.host = host;
            this.username = username;
            this.fromAddress = fromAddress;
            this.fromName = fromName;
            this.replyTo = replyTo;
        }
    }
}
