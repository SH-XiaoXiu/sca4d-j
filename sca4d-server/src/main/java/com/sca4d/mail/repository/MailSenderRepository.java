package com.sca4d.mail.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.entity.SysMailSenderEntity;
import com.sca4d.mail.MailClientStateTargets;
import com.sca4d.mail.MailErrorCode;
import com.sca4d.mail.MailSender;
import com.sca4d.mail.MailSenderStatus;
import com.sca4d.mail.SmtpSecurity;
import com.sca4d.mail.security.MailSenderConfiguration;
import com.sca4d.mapper.MailSenderMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/** 读写 SMTP 地址、加密密码、发件资料、状态和乐观锁版本。 */
@Repository
@RequiredArgsConstructor
public class MailSenderRepository {

    private final MailSenderMapper mapper;

    /**
     * 读取全部未删除邮件发送配置，只读取 key-id 判断密码是否存在，不查询 nonce 或密文列。
     *
     * <p>固定执行一条单表 SQL，并按名称和主键排列。
     *
     * @return 可以直接进入管理 Service 的安全配置
     */
    public @NotNull List<MailSender> findSenders() {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysMailSenderEntity::getId),
                        column(SysMailSenderEntity::getName),
                        column(SysMailSenderEntity::getHost),
                        column(SysMailSenderEntity::getPort),
                        column(SysMailSenderEntity::getSecurity),
                        column(SysMailSenderEntity::getUsername),
                        column(SysMailSenderEntity::getPasswordKeyId),
                        column(SysMailSenderEntity::getFromAddress),
                        column(SysMailSenderEntity::getFromName),
                        column(SysMailSenderEntity::getReplyTo),
                        column(SysMailSenderEntity::getStatus),
                        column(SysMailSenderEntity::getVersion))
                .where(SysMailSenderEntity::getDeleted)
                .eq(0)
                .orderBy(SysMailSenderEntity::getName)
                .asc()
                .orderBy(SysMailSenderEntity::getId)
                .asc();
        return mapper.selectListByQuery(query).stream()
                .map(entity -> new MailSender(
                        entity.getId(),
                        entity.getName(),
                        entity.getHost(),
                        entity.getPort(),
                        entity.getSecurity(),
                        entity.getUsername(),
                        entity.getPasswordKeyId() != null,
                        entity.getFromAddress(),
                        entity.getFromName(),
                        entity.getReplyTo(),
                        entity.getStatus(),
                        entity.getVersion()))
                .toList();
    }

    /**
     * 按主键读取一项安全配置，只读取 key-id 判断密码是否存在，不查询 nonce 或密文列。
     *
     * @return 未删除配置；不存在时返回 {@code null}
     */
    public @Nullable MailSender findSender(@NotNull Long senderId) {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysMailSenderEntity::getId),
                        column(SysMailSenderEntity::getName),
                        column(SysMailSenderEntity::getHost),
                        column(SysMailSenderEntity::getPort),
                        column(SysMailSenderEntity::getSecurity),
                        column(SysMailSenderEntity::getUsername),
                        column(SysMailSenderEntity::getPasswordKeyId),
                        column(SysMailSenderEntity::getFromAddress),
                        column(SysMailSenderEntity::getFromName),
                        column(SysMailSenderEntity::getReplyTo),
                        column(SysMailSenderEntity::getStatus),
                        column(SysMailSenderEntity::getVersion))
                .where(SysMailSenderEntity::getId)
                .eq(senderId)
                .and(SysMailSenderEntity::getDeleted)
                .eq(0);
        SysMailSenderEntity entity = mapper.selectOneByQuery(query);
        return entity == null
                ? null
                : new MailSender(
                        entity.getId(),
                        entity.getName(),
                        entity.getHost(),
                        entity.getPort(),
                        entity.getSecurity(),
                        entity.getUsername(),
                        entity.getPasswordKeyId() != null,
                        entity.getFromAddress(),
                        entity.getFromName(),
                        entity.getReplyTo(),
                        entity.getStatus(),
                        entity.getVersion());
    }

    /**
     * 读取建立 SMTP 连接需要的配置和密码密文。
     *
     * <p>三个密码列必须同时存在或同时为空；部分缺失表示数据库内容损坏并拒绝继续连接。
     *
     * @return 未删除配置；不存在时返回 {@code null}
     */
    public @Nullable MailSenderConfiguration findConfiguration(@NotNull Long senderId) {
        QueryWrapper query = QueryWrapper.create()
                .where(SysMailSenderEntity::getId)
                .eq(senderId)
                .and(SysMailSenderEntity::getDeleted)
                .eq(0);
        SysMailSenderEntity entity = mapper.selectOneByQuery(query);
        if (entity == null) {
            return null;
        }
        boolean hasKey = entity.getPasswordKeyId() != null;
        boolean hasNonce = entity.getPasswordNonce() != null;
        boolean hasCiphertext = entity.getPasswordCiphertext() != null;
        if ((hasKey || hasNonce || hasCiphertext) && !(hasKey && hasNonce && hasCiphertext)) {
            throw new IllegalStateException("SMTP 密码密文列不完整: " + senderId);
        }
        MailSender sender = new MailSender(
                entity.getId(),
                entity.getName(),
                entity.getHost(),
                entity.getPort(),
                entity.getSecurity(),
                entity.getUsername(),
                hasCiphertext,
                entity.getFromAddress(),
                entity.getFromName(),
                entity.getReplyTo(),
                entity.getStatus(),
                entity.getVersion());
        EncryptedSecret password = hasCiphertext
                ? new EncryptedSecret(
                        entity.getPasswordKeyId(),
                        entity.getPasswordNonce(),
                        entity.getPasswordCiphertext())
                : null;
        return new MailSenderConfiguration(sender, password);
    }

    /**
     * 批量读取操作日志显示需要的名称和删除状态。
     *
     * <p>固定执行一条单表 SQL并保留删除记录，不查询 SMTP 地址、用户名或密码密文。
     *
     * @param senderIds 去重后的邮件发送配置主键
     * @return 名称和删除状态投影
     */
    public @NotNull List<SysMailSenderEntity> findSenderReferences(
            @NotNull Set<Long> senderIds) {
        if (senderIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysMailSenderEntity::getId),
                        column(SysMailSenderEntity::getName),
                        column(SysMailSenderEntity::getDeleted))
                .where(SysMailSenderEntity::getId)
                .in(senderIds);
        return mapper.selectListByQuery(query);
    }

    /**
     * 写入已经分配主键并完成字段和认证组合校验的邮件发送配置。
     *
     * @param password 可选 SMTP 密码密文；匿名连接时为空
     */
    public void createSender(
            @NotNull Long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            @Nullable EncryptedSecret password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo,
            @NotNull MailSenderStatus status) {
        SysMailSenderEntity entity = new SysMailSenderEntity()
                .setId(senderId)
                .setName(name)
                .setHost(host)
                .setPort(port)
                .setSecurity(security)
                .setUsername(username)
                .setPasswordKeyId(password == null ? null : password.getKeyId())
                .setPasswordNonce(password == null ? null : password.getNonce())
                .setPasswordCiphertext(password == null ? null : password.getCiphertext())
                .setFromAddress(fromAddress)
                .setFromName(fromName)
                .setReplyTo(replyTo)
                .setStatus(status)
                .setDeleted(0);
        if (mapper.insertSender(entity) != 1) {
            throw new IllegalStateException("邮件发送配置写入行数异常");
        }
    }

    /**
     * 原子修改 SMTP 地址、发件资料和可选密码密文。
     *
     * @param passwordChanged false 时保留数据库现有密码三列
     * @param password 新密码密文；passwordChanged 为 true 且该值为空时清空密码
     */
    public void updateSender(
            @NotNull Long senderId,
            @NotNull String name,
            @NotNull String host,
            int port,
            @NotNull SmtpSecurity security,
            @Nullable String username,
            boolean passwordChanged,
            @Nullable EncryptedSecret password,
            @NotNull String fromAddress,
            @Nullable String fromName,
            @Nullable String replyTo,
            long version) {
        if (mapper.updateSender(
                senderId,
                name,
                host,
                port,
                security,
                username,
                password == null ? null : password.getKeyId(),
                password == null ? null : password.getNonce(),
                password == null ? null : password.getCiphertext(),
                passwordChanged,
                fromAddress,
                fromName,
                replyTo,
                version) != 1) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.SENDER_CHANGED, MailClientStateTargets.SENDERS);
        }
    }

    /** 按当前状态和版本原子推进邮件发送配置状态。 */
    public void updateStatus(
            @NotNull Long senderId,
            @NotNull MailSenderStatus expectedStatus,
            @NotNull MailSenderStatus nextStatus,
            long version) {
        if (mapper.updateStatus(senderId, expectedStatus, nextStatus, version) != 1) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.SENDER_CHANGED, MailClientStateTargets.SENDERS);
        }
    }

    /** 在版本匹配时把停用配置推进到逻辑删除状态。 */
    public void deleteSender(@NotNull Long senderId, long version) {
        if (mapper.markDeleted(senderId, version) != 1) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.SENDER_CHANGED, MailClientStateTargets.SENDERS);
        }
    }
}
