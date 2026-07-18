package com.sca4d.notification.service;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.NotificationDeliveryEntity;
import com.sca4d.entity.NotificationMessageEntity;
import com.sca4d.notification.NotificationCategory;
import com.sca4d.notification.NotificationDeliveryStatus;
import com.sca4d.notification.NotificationMessage;
import com.sca4d.notification.NotificationRecipient;
import com.sca4d.notification.NotificationService;
import com.sca4d.notification.codec.NotificationPayloadCodec;
import com.sca4d.notification.error.NotificationErrorCode;
import com.sca4d.notification.realtime.NotificationRealtimePublisher;
import com.sca4d.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 通知服务。
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;
    private final NotificationPayloadCodec payloadCodec;
    private final NotificationRealtimePublisher realtimePublisher;

    @Transactional
    @Override
    public @NotNull Long create(
            @NotNull String title,
            @NotNull String content,
            @NotNull NotificationCategory category,
            @Nullable PrincipalType senderPrincipalType,
            @Nullable Long senderPrincipalId,
            @Nullable Map<String, Object> payload,
            @NotNull List<NotificationRecipient> recipients) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_TITLE_REQUIRED);
        }
        if (!StringUtils.hasText(content)) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_CONTENT_REQUIRED);
        }
        if ((senderPrincipalType == null && senderPrincipalId != null)
                || (senderPrincipalType != null && senderPrincipalId == null)) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_SENDER_INCOMPLETE);
        }
        if (recipients.isEmpty()) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_RECIPIENTS_REQUIRED);
        }

        // 通知投递目标按 Subject + Principal 去重，避免同一接收人重复落库和重复推送。
        LinkedHashSet<NotificationRecipient> uniqueRecipients = new LinkedHashSet<>();
        for (NotificationRecipient recipient : recipients) {
            if (recipient == null) {
                throw new BizException(NotificationErrorCode.NOTIFICATION_RECIPIENT_REQUIRED);
            }
            boolean principalIncomplete = (recipient.getPrincipalType() == null) != (recipient.getPrincipalId() == null);
            if (principalIncomplete
                    || (recipient.getSubjectId() == null && recipient.getPrincipalId() == null)) {
                throw new BizException(NotificationErrorCode.NOTIFICATION_RECIPIENT_INCOMPLETE);
            }
            uniqueRecipients.add(recipient);
        }

        OffsetDateTime now = TimeSupport.now();
        // 通知消息只保存一次，面向不同接收人的已读状态由投递记录表达。
        NotificationMessageEntity message = new NotificationMessageEntity()
                .setTitle(title.trim())
                .setContent(content.trim())
                .setCategory(category)
                .setSenderPrincipalType(senderPrincipalType)
                .setSenderPrincipalId(senderPrincipalId)
                .setPayloadJson(payloadCodec.encode(payload))
                .setCreatedAt(now)
                .setDeleted(0);
        repository.createMessage(message, message.getPayloadJson());
        Long notificationId = message.getId();
        // 每个接收人独立生成投递记录，消息与全部投递作为一个存储聚合落库。
        List<NotificationDeliveryEntity> deliveries = uniqueRecipients.stream()
                .map(recipient -> new NotificationDeliveryEntity()
                        .setNotificationId(notificationId)
                        .setSubjectId(recipient.getSubjectId())
                        .setPrincipalType(recipient.getPrincipalType())
                        .setPrincipalId(recipient.getPrincipalId())
                        .setStatus(NotificationDeliveryStatus.UNREAD)
                        .setDeliveredAt(now)
                        .setCreatedAt(now)
                        .setUpdatedAt(now)
                        .setVersion(0L)
                        .setDeleted(0))
                .toList();
        repository.createDeliveries(deliveries);
        List<NotificationRecipient> recipientList = List.copyOf(uniqueRecipients);
        List<Long> unreadCounts = repository.countUnread(recipientList);

        // 公共实时发布器会在事务提交后发送；每个去重接收人固定发布 created 与 unreadChanged 两个不同事件。
        for (int index = 0; index < recipientList.size(); index++) {
            NotificationRecipient recipient = recipientList.get(index);
            realtimePublisher.created(notificationId, message.getTitle(), message.getContent(), recipient);
            realtimePublisher.unreadChanged(recipient, unreadCounts.get(index));
        }
        log.info(
                "通知及投递已写入事务，实时更新已登记 | notificationId={} category={} recipientCount={}",
                notificationId,
                category,
                recipientList.size());
        return notificationId;
    }

    @Override
    public @NotNull PageData<NotificationMessage> page(
            boolean unreadOnly,
            long page,
            long size,
            @NotNull Subject subject,
            @NotNull Principal principal) {
        Long principalId = principal.requirePrincipalId();
        PrincipalType principalType = principal.requirePrincipalType();
        return repository.pageCurrent(
                unreadOnly,
                page,
                size,
                subject.getSubjectId(),
                principalType,
                principalId);
    }

    @Override
    public long unreadCount(@NotNull Subject subject, @NotNull Principal principal) {
        Long principalId = principal.requirePrincipalId();
        PrincipalType principalType = principal.requirePrincipalType();
        return repository.countUnread(
                subject.getSubjectId(), principalType, principalId);
    }

    @Transactional
    @Override
    public void read(
            @NotNull Long notificationId, @NotNull Subject subject, @NotNull Principal principal) {
        Long principalId = principal.requirePrincipalId();
        PrincipalType principalType = principal.requirePrincipalType();
        NotificationDeliveryEntity delivery = repository
                .findCurrentDelivery(
                        notificationId,
                        subject.getSubjectId(),
                        principalType,
                        principalId)
                .orElseThrow(() -> new BizException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (delivery.getStatus() == NotificationDeliveryStatus.UNREAD) {
            if (repository.markDeliveryRead(delivery.getId(), TimeSupport.now())) {
                realtimePublisher.unreadChanged(
                        NotificationRecipient.of(
                                subject.getSubjectId(), principalType, principalId),
                        repository.countUnread(
                                subject.getSubjectId(), principalType, principalId));
                log.debug(
                        "通知已读状态已写入事务 | notificationId={} deliveryId={} principalId={}",
                        notificationId,
                        delivery.getId(),
                        principalId);
            }
        }
    }

    @Transactional
    @Override
    public void readAll(@NotNull Subject subject, @NotNull Principal principal) {
        Long principalId = principal.requirePrincipalId();
        PrincipalType principalType = principal.requirePrincipalType();
        boolean updated = repository.markAllRead(
                subject.getSubjectId(), principalType, principalId, TimeSupport.now());
        if (updated) {
            realtimePublisher.unreadChanged(
                    NotificationRecipient.of(
                            subject.getSubjectId(), principalType, principalId),
                    repository.countUnread(
                            subject.getSubjectId(), principalType, principalId));
            log.debug("全部通知已读状态已写入事务 | subjectId={} principalId={}",
                    subject.getSubjectId(), principalId);
        }
    }

}
