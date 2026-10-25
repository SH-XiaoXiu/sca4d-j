package com.sca4d.notification.email.sender;

import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.notification.email.EmailNotificationErrorCode;
import com.sca4d.notification.email.sender.mapper.EmailSenderMapper;
import com.sca4d.notification.email.sender.mapper.EmailSenderState;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 查询 SMTP 配置、读取加密密码列，并按版本号更新或删除配置记录。 */
public class EmailSenderRepository {

    private final @NotNull EmailSenderMapper mapper;

    private final @NotNull VersionConditions versionConditions;

    /**
     * @param mapper SMTP 配置 Mapper
     * @param versionConditions 根资源写入版本条件
     */
    public EmailSenderRepository(
            @NotNull EmailSenderMapper mapper,
            @NotNull VersionConditions versionConditions) {
        this.mapper = Objects.requireNonNull(mapper, "EmailSenderMapper 不能为空");
        this.versionConditions = Objects.requireNonNull(versionConditions, "版本条件不能为空");
    }

    /** @return 全部未删除 SMTP 配置，不包含密码密文 */
    @CheckReturnValue
    public @NotNull List<@NotNull EmailSender> findAll() {
        return mapper.select(null, null).stream().map(this::sender).toList();
    }

    /**
     * @param senderId SMTP 配置标识
     * @return SMTP 配置；不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<EmailSender> find(long senderId) {
        return mapper.select(senderId, null).stream().findFirst().map(this::sender);
    }

    /** @return 当前唯一启用配置及密码密文；不存在时为空 */
    @CheckReturnValue
    public @NotNull Optional<EmailSenderConfiguration> findEnabledConfiguration() {
        return mapper.select(null, true).stream().findFirst().map(this::configuration);
    }

    /**
     * @param senderId SMTP 配置标识
     * @return 指定配置及密码密文；不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<EmailSenderConfiguration> findConfiguration(long senderId) {
        return mapper.select(senderId, null).stream().findFirst().map(this::configuration);
    }

    /** 新增一项默认停用的 SMTP 配置。 */
    @AffectedRows(target = "新增 Email Sender")
    public void add(
            long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            @Nullable EncryptedSecret password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo) {
        try {
            mapper.insert(
                    senderId,
                    name,
                    host,
                    port,
                    security,
                    username,
                    password == null ? null : password.keyId(),
                    password == null ? null : password.ciphertext(),
                    fromAddress,
                    fromName,
                    replyTo);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_CONFLICT, exception);
        }
    }

    /** 按请求版本条件或内部调用读取的当前版本原子替换 SMTP 配置。 */
    public void replace(
            long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            boolean passwordChanged,
            @Nullable EncryptedSecret password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo) {
        EmailSender current = find(senderId)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.SENDER_NOT_FOUND));
        VersionCondition condition = versionConditions.resolve(current.version());
        try {
            AffectedRowCounts.requireSingle(
                    mapper.update(
                            senderId,
                            Patch.set(name),
                            Patch.set(host),
                            Patch.set(port),
                            Patch.set(security),
                            username == null ? Patch.clear() : Patch.set(username),
                            !passwordChanged
                                    ? Patch.ignore()
                                    : password == null ? Patch.clear() : Patch.set(password.keyId()),
                            !passwordChanged
                                    ? Patch.ignore()
                                    : password == null ? Patch.clear() : Patch.set(password.ciphertext()),
                            Patch.set(fromAddress),
                            fromName == null ? Patch.clear() : Patch.set(fromName),
                            replyTo == null ? Patch.clear() : Patch.set(replyTo),
                            Patch.ignore(),
                            condition.expected()),
                    condition.whenZero(),
                    "Email Sender");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_CONFLICT, exception);
        }
    }

    /** 按请求版本条件或内部调用读取的当前版本原子修改启用状态。 */
    public void setEnabled(long senderId, boolean enabled) {
        EmailSender current = find(senderId)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.SENDER_NOT_FOUND));
        VersionCondition condition = versionConditions.resolve(current.version());
        try {
            AffectedRowCounts.requireSingle(
                    mapper.update(
                            senderId,
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.ignore(),
                            Patch.set(enabled),
                            condition.expected()),
                    condition.whenZero(),
                    "Email Sender");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(EmailNotificationErrorCode.SENDER_CONFLICT, exception);
        }
    }

    /** 按请求版本条件或内部调用读取的当前版本逻辑删除停用配置。 */
    public void delete(long senderId) {
        EmailSender current = find(senderId)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.SENDER_NOT_FOUND));
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.markDeleted(senderId, condition.expected()),
                condition.whenZero(),
                "Email Sender");
    }

    /** 把完整持久化状态转换为不包含密文的 SMTP 配置。 */
    private @NotNull EmailSender sender(@NotNull EmailSenderState state) {
        boolean hasKey = state.passwordKeyId() != null;
        boolean hasCiphertext = state.passwordCiphertext() != null;
        if (hasKey != hasCiphertext
                || (hasKey && state.username() == null)
                || (!hasKey && state.username() != null)) {
            throw new IllegalStateException("SMTP 配置认证列不完整: " + state.senderId());
        }
        return new EmailSender(
                state.senderId(),
                state.name(),
                state.host(),
                state.port(),
                state.security(),
                state.username(),
                hasKey,
                state.fromAddress(),
                state.fromName(),
                state.replyTo(),
                state.enabled(),
                state.version());
    }

    /** 把完整持久化状态转换为仅供 SMTP 客户端使用的配置。 */
    private @NotNull EmailSenderConfiguration configuration(@NotNull EmailSenderState state) {
        EmailSender value = sender(state);
        EncryptedSecret password = state.passwordKeyId() == null
                ? null
                : new EncryptedSecret(state.passwordKeyId(), Objects.requireNonNull(state.passwordCiphertext()));
        return new EmailSenderConfiguration(value, password);
    }
}
