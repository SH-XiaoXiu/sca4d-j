package com.sca4d.mail.service;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.mail.MailClientStateTargets;
import com.sca4d.mail.MailErrorCode;
import com.sca4d.mail.MailSender;
import com.sca4d.mail.MailSenderService;
import com.sca4d.mail.MailSenderStatus;
import com.sca4d.mail.SmtpSecurity;
import com.sca4d.mail.repository.MailSenderRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** SMTP 配置管理默认实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultMailSenderService implements MailSenderService {

    private final MailSenderRepository repository;
    private final SecretKeyring keyring;
    private final IdGenerator idGenerator;

    @Override
    public @NotNull List<MailSender> senders() {
        return repository.findSenders();
    }

    @Override
    public @NotNull MailSender require(@NotNull Long senderId) {
        MailSender sender = repository.findSender(senderId);
        if (sender == null) {
            throw new BizException(MailErrorCode.SENDER_NOT_FOUND);
        }
        return sender;
    }

    @Override
    public @NotNull Long create(
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            @Nullable String password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo) {
        String checkedName = name.trim();
        String checkedHost = host.trim();
        String checkedUsername = StringUtils.hasText(username) ? username.trim() : null;
        String checkedFromAddress = fromAddress.trim();
        String checkedFromName = StringUtils.hasText(fromName) ? fromName.trim() : null;
        String checkedReplyTo = StringUtils.hasText(replyTo) ? replyTo.trim() : null;
        if (checkedName.isEmpty() || checkedHost.isEmpty() || port < 1 || port > 65535) {
            throw new BizException(MailErrorCode.SENDER_CONFIGURATION_INVALID);
        }
        if ((checkedUsername == null) != (password == null || password.isEmpty())) {
            throw new BizException(MailErrorCode.SENDER_AUTH_INVALID);
        }
        try {
            new InternetAddress(checkedFromAddress, true).validate();
            if (checkedReplyTo != null) {
                new InternetAddress(checkedReplyTo, true).validate();
            }
        } catch (AddressException exception) {
            throw new BizException(MailErrorCode.SENDER_ADDRESS_INVALID, exception);
        }
        Long senderId = idGenerator.nextId();
        EncryptedSecret encrypted = password == null
                ? null
                : keyring.encrypt("smtp.sender." + senderId + ".password", password);
        repository.createSender(
                senderId,
                checkedName,
                checkedHost,
                port,
                security,
                checkedUsername,
                encrypted,
                checkedFromAddress,
                checkedFromName,
                checkedReplyTo,
                MailSenderStatus.DISABLED);
        log.info(
                "邮件发送配置创建完成 | senderId={} status={} security={} host={} port={}",
                senderId,
                MailSenderStatus.DISABLED,
                security,
                checkedHost,
                port);
        return senderId;
    }

    @Override
    public void update(
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
            long version) {
        MailSender current = require(senderId);
        if (!current.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.SENDER_CHANGED, MailClientStateTargets.SENDERS);
        }
        String checkedName = name.trim();
        String checkedHost = host.trim();
        String checkedUsername = StringUtils.hasText(username) ? username.trim() : null;
        String checkedFromAddress = fromAddress.trim();
        String checkedFromName = StringUtils.hasText(fromName) ? fromName.trim() : null;
        String checkedReplyTo = StringUtils.hasText(replyTo) ? replyTo.trim() : null;
        if (checkedName.isEmpty() || checkedHost.isEmpty() || port < 1 || port > 65535) {
            throw new BizException(MailErrorCode.SENDER_CONFIGURATION_INVALID);
        }
        if (clearPassword && password != null) {
            throw new BizException(MailErrorCode.SENDER_AUTH_INVALID);
        }
        if (checkedUsername == null) {
            if (password != null) {
                throw new BizException(MailErrorCode.SENDER_AUTH_INVALID);
            }
            clearPassword = clearPassword || current.getPasswordConfigured();
        } else {
            if (clearPassword || (password == null && !current.getPasswordConfigured())) {
                throw new BizException(MailErrorCode.SENDER_AUTH_INVALID);
            }
        }
        if (password != null && password.isEmpty()) {
            throw new BizException(MailErrorCode.SENDER_AUTH_INVALID);
        }
        try {
            new InternetAddress(checkedFromAddress, true).validate();
            if (checkedReplyTo != null) {
                new InternetAddress(checkedReplyTo, true).validate();
            }
        } catch (AddressException exception) {
            throw new BizException(MailErrorCode.SENDER_ADDRESS_INVALID, exception);
        }
        EncryptedSecret encrypted = password == null
                ? null
                : keyring.encrypt("smtp.sender." + senderId + ".password", password);
        repository.updateSender(
                senderId,
                checkedName,
                checkedHost,
                port,
                security,
                checkedUsername,
                password != null || clearPassword,
                encrypted,
                checkedFromAddress,
                checkedFromName,
                checkedReplyTo,
                version);
        log.info(
                "邮件发送配置更新完成 | senderId={} version={} security={} host={} port={}",
                senderId,
                version + 1,
                security,
                checkedHost,
                port);
    }

    @Override
    public void changeStatus(
            @NotNull Long senderId, @NotNull MailSenderStatus status, long version) {
        MailSender sender = require(senderId);
        if (!sender.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.SENDER_CHANGED, MailClientStateTargets.SENDERS);
        }
        if (sender.getStatus() == status) {
            return;
        }
        repository.updateStatus(senderId, sender.getStatus(), status, version);
        log.info("邮件发送配置状态更新完成 | senderId={} status={} version={}",
                senderId, status, version + 1);
    }

    @Override
    public void delete(@NotNull Long senderId, long version) {
        MailSender sender = require(senderId);
        if (!sender.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.SENDER_CHANGED, MailClientStateTargets.SENDERS);
        }
        if (sender.getStatus() == MailSenderStatus.ACTIVE) {
            throw new BizException(MailErrorCode.SENDER_ACTIVE);
        }
        repository.deleteSender(senderId, version);
        log.info("邮件发送配置删除完成 | senderId={}", senderId);
    }
}
