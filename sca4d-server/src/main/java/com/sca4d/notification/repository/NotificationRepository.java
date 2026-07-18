package com.sca4d.notification.repository;

import com.mybatisflex.core.paginate.Page;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.NotificationDeliveryEntity;
import com.sca4d.entity.NotificationMessageEntity;
import com.sca4d.mapper.NotificationMapper;
import com.sca4d.notification.NotificationDeliveryStatus;
import com.sca4d.notification.NotificationMessage;
import com.sca4d.notification.NotificationRecipient;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 通知仓储。
 */
@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final NotificationMapper mapper;
    private final IdGenerator idGenerator;

    /**
     * 保存通知消息并回填集群主键。
     *
     * @param message 通知消息实体
     * @param payloadJson 已编码的扩展载荷 JSON
     */
    public void createMessage(@NotNull NotificationMessageEntity message, @NotNull String payloadJson) {
        if (message.getId() == null) {
            message.setId(idGenerator.nextId());
        }
        if (mapper.insertMessage(message, payloadJson) != 1) {
            throw new IllegalStateException("通知消息写入行数异常");
        }
    }

    /**
     * 批量保存通知投递记录。
     *
     * @param deliveries 已关联通知主键的去重投递记录
     */
    public void createDeliveries(@NotNull List<NotificationDeliveryEntity> deliveries) {
        deliveries.forEach(delivery -> {
            if (delivery.getId() == null) {
                delivery.setId(idGenerator.nextId());
            }
        });
        if (!deliveries.isEmpty() && mapper.insertDeliveries(deliveries) != deliveries.size()) {
            throw new IllegalStateException("通知投递批量写入行数异常");
        }
    }

    /**
     * 一次性统计多个接收目标的未读数量。
     *
     * @param recipients 去重且顺序固定的接收目标
     * @return 与输入列表下标严格对应的未读数量
     */
    public @NotNull List<Long> countUnread(@NotNull List<NotificationRecipient> recipients) {
        if (recipients.isEmpty()) {
            return List.of();
        }
        List<Long> counts = new ArrayList<>(Collections.nCopies(recipients.size(), 0L));
        for (NotificationUnreadCountResult result : mapper.countUnreadByRecipients(
                recipients, NotificationDeliveryStatus.UNREAD)) {
            if (result.getTargetIndex() < 0 || result.getTargetIndex() >= counts.size()) {
                throw new IllegalStateException("通知未读聚合返回了非法目标下标");
            }
            counts.set(result.getTargetIndex(), result.getUnreadCount());
        }
        return List.copyOf(counts);
    }

    /**
     * 分页查询当前用户通知。
     *
     * @param unreadOnly 是否只返回未读投递
     * @param page 页码
     * @param size 每页记录数
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 通知分页
     */
    public @NotNull PageData<NotificationMessage> pageCurrent(
            boolean unreadOnly,
            long page,
            long size,
            @Nullable Long subjectId,
            @Nullable PrincipalType principalType,
            @Nullable Long principalId) {
        return PageData.from(mapper.selectCurrentPage(
                new Page<>(page, size),
                unreadOnly,
                subjectId,
                principalType,
                principalId,
                NotificationDeliveryStatus.UNREAD));
    }

    /**
     * 统计未读通知数量。
     *
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 未读通知数量
     */
    public long countUnread(@Nullable Long subjectId, @Nullable PrincipalType principalType, @Nullable Long principalId) {
        return mapper.countByStatus(subjectId, principalType, principalId, NotificationDeliveryStatus.UNREAD);
    }

    /**
     * 查询当前用户指定通知投递记录。
     *
     * @param notificationId 通知主键
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 通知投递记录
     */
    public @NotNull Optional<NotificationDeliveryEntity> findCurrentDelivery(
            @NotNull Long notificationId,
            @Nullable Long subjectId,
            @Nullable PrincipalType principalType,
            @Nullable Long principalId) {
        return Optional.ofNullable(mapper.findCurrentDelivery(notificationId, subjectId, principalType, principalId));
    }

    /**
     * 标记单条通知已读。
     *
     * @param deliveryId 投递主键
     * @param readAt 阅读时间
     * @return 未读状态成功迁移为已读时返回 true
     */
    public boolean markDeliveryRead(@NotNull Long deliveryId, @NotNull OffsetDateTime readAt) {
        return mapper.updateDeliveryStatus(
                deliveryId, NotificationDeliveryStatus.UNREAD, NotificationDeliveryStatus.READ, readAt) == 1;
    }

    /**
     * 标记当前用户所有通知已读。
     *
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param readAt 阅读时间
     * @return 至少一条未读投递成功迁移为已读时返回 true
     */
    public boolean markAllRead(
            @Nullable Long subjectId,
            @Nullable PrincipalType principalType,
            @Nullable Long principalId,
            @NotNull OffsetDateTime readAt) {
        return mapper.updateAllStatus(
                subjectId,
                principalType,
                principalId,
                NotificationDeliveryStatus.UNREAD,
                NotificationDeliveryStatus.READ,
                readAt) > 0;
    }
}
