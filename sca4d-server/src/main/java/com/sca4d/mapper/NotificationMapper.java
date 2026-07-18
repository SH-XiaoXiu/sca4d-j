package com.sca4d.mapper;

import com.mybatisflex.core.paginate.Page;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.entity.NotificationDeliveryEntity;
import com.sca4d.entity.NotificationMessageEntity;
import com.sca4d.notification.NotificationDeliveryStatus;
import com.sca4d.notification.NotificationMessage;
import com.sca4d.notification.NotificationRecipient;
import com.sca4d.notification.repository.NotificationUnreadCountResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 通知模块 MyBatis 查询 Mapper。
 */
@Mapper
public interface NotificationMapper {

    /**
     * 写入通知消息。
     *
     * @param entity 通知消息实体
     * @param payloadJson 扩展载荷 JSON
     * @return 写入行数
     */
    int insertMessage(
            @Param("entity") @NotNull NotificationMessageEntity entity, @Param("payloadJson") @NotNull String payloadJson);

    /**
     * 写入通知投递记录。
     *
     * @param entity 通知投递实体
     * @return 写入行数
     */
    int insertDeliveries(@Param("deliveries") @NotNull List<NotificationDeliveryEntity> deliveries);

    /**
     * 一次性统计多个接收目标的未读通知数量。
     *
     * @param recipients 去重后的接收目标
     * @param status 未读状态
     * @return 与输入下标一一对应的聚合结果
     */
    @NotNull
    List<NotificationUnreadCountResult> countUnreadByRecipients(
            @Param("recipients") @NotNull List<NotificationRecipient> recipients,
            @Param("status") @NotNull NotificationDeliveryStatus status);

    /**
     * 分页查询当前用户通知。
     *
     * @param page MyBatis-Flex 分页容器
     * @param unreadOnly 是否只返回未读投递
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param unreadStatus 未读筛选使用的投递状态
     * @return 填充记录和总数的分页容器
     */
    @NotNull
    Page<NotificationMessage> selectCurrentPage(
            @NotNull Page<NotificationMessage> page,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("subjectId") @Nullable Long subjectId,
            @Param("principalType") @Nullable PrincipalType principalType,
            @Param("principalId") @Nullable Long principalId,
            @Param("unreadStatus") @NotNull NotificationDeliveryStatus unreadStatus);

    /**
     * 统计当前用户未读数。
     *
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 未读数量
     */
    long countByStatus(
            @Param("subjectId") @Nullable Long subjectId,
            @Param("principalType") @Nullable PrincipalType principalType,
            @Param("principalId") @Nullable Long principalId,
            @Param("status") @NotNull NotificationDeliveryStatus status);

    /**
     * 查询当前用户指定通知投递记录。
     *
     * @param notificationId 通知主键
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 通知投递实体
     */
    @Nullable
    NotificationDeliveryEntity findCurrentDelivery(
            @Param("notificationId") @NotNull Long notificationId,
            @Param("subjectId") @Nullable Long subjectId,
            @Param("principalType") @Nullable PrincipalType principalType,
            @Param("principalId") @Nullable Long principalId);

    /**
     * 标记单条通知已读。
     *
     * @param deliveryId 投递主键
     * @param readAt 阅读时间
     * @return 更新行数
     */
    int updateDeliveryStatus(
            @Param("deliveryId") @NotNull Long deliveryId,
            @Param("currentStatus") @NotNull NotificationDeliveryStatus currentStatus,
            @Param("nextStatus") @NotNull NotificationDeliveryStatus nextStatus,
            @Param("readAt") @NotNull OffsetDateTime readAt);

    /**
     * 标记当前用户所有通知已读。
     *
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param readAt 阅读时间
     * @return 更新行数
     */
    int updateAllStatus(
            @Param("subjectId") @Nullable Long subjectId,
            @Param("principalType") @Nullable PrincipalType principalType,
            @Param("principalId") @Nullable Long principalId,
            @Param("currentStatus") @NotNull NotificationDeliveryStatus currentStatus,
            @Param("nextStatus") @NotNull NotificationDeliveryStatus nextStatus,
            @Param("readAt") @NotNull OffsetDateTime readAt);
}
