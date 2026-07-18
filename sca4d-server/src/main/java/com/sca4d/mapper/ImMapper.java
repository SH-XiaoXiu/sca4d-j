package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.entity.ImConversationEntity;
import com.sca4d.entity.ImConversationMemberEntity;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.im.ImConversation;
import com.sca4d.im.ImMessage;
import com.sca4d.im.ImMessageStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * IM 模块 MyBatis 查询 Mapper。
 */
@Mapper
public interface ImMapper extends BaseMapper<ImConversationEntity> {

    /**
     * 物理删除一组已失效 Principal 的会话成员关系。
     *
     * @param principalIds 已去重且有界的 Principal 主键
     * @return 实际删除成员数量
     */
    int deleteMembersByPrincipalIds(@Param("principalIds") @NotNull List<Long> principalIds);

    /**
     * 写入会话。
     *
     * @param entity 会话实体
     * @return 写入行数
     */
    int insertConversation(@Param("entity") @NotNull ImConversationEntity entity);

    /**
     * 批量写入会话成员。
     *
     * @param entities 会话成员实体
     * @return 写入行数
     */
    int insertMembers(@Param("entities") @NotNull List<ImConversationMemberEntity> entities);

    /**
     * 写入消息。
     *
     * @param entity 消息实体
     * @param payloadJson 扩展载荷 JSON
     * @return 写入行数
     */
    int insertMessage(@Param("entity") @NotNull ImMessageEntity entity, @Param("payloadJson") @NotNull String payloadJson);

    /**
     * 分页查询当前主体会话。
     *
     * @param page MyBatis-Flex 分页容器
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param normalStatus 正常消息状态
     * @return 填充记录和总数的分页容器
     */
    @NotNull
    Page<ImConversation> selectConversationPage(
            @NotNull Page<ImConversation> page,
            @Param("principalType") @NotNull PrincipalType principalType,
            @Param("principalId") @NotNull Long principalId,
            @Param("normalStatus") @NotNull ImMessageStatus normalStatus);

    /**
     * 分页查询会话消息。
     *
     * @param page MyBatis-Flex 分页容器
     * @param conversationId 会话主键
     * @return 填充记录和总数的分页容器
     */
    @NotNull
    Page<ImMessage> selectMessagePage(
            @NotNull Page<ImMessage> page,
            @Param("conversationId") @NotNull Long conversationId);

    /**
     * 按主键查询消息。
     *
     * @param messageId 消息主键
     * @return 消息实体
     */
    @Nullable
    ImMessageEntity selectMessageById(@Param("messageId") @NotNull Long messageId);

    /**
     * 更新会话最后消息摘要。
     *
     * @param conversationId 会话主键
     * @param messageId 消息主键
     * @param preview 消息预览
     * @param at 更新时间
     * @return 更新行数
     */
    int updateConversationLastMessage(
            @Param("conversationId") @NotNull Long conversationId,
            @Param("messageId") @NotNull Long messageId,
            @Param("preview") @NotNull String preview,
            @Param("at") @NotNull OffsetDateTime at);

    /**
     * 更新成员最后已读游标。
     *
     * @param conversationId 会话主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param messageId 消息主键
     * @param at 更新时间
     * @return 更新行数
     */
    int updateMemberReadCursor(
            @Param("conversationId") @NotNull Long conversationId,
            @Param("principalType") @NotNull PrincipalType principalType,
            @Param("principalId") @NotNull Long principalId,
            @Param("messageId") @NotNull Long messageId,
            @Param("at") @NotNull OffsetDateTime at);

    /**
     * 按发送人和当前状态更新消息状态。
     *
     * @param messageId 消息主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param currentStatus 当前状态
     * @param nextStatus 目标状态
     * @param revokedAt 撤回时间
     * @return 更新行数
     */
    int updateMessageStatusBySenderAndStatus(
            @Param("messageId") @NotNull Long messageId,
            @Param("principalType") @NotNull PrincipalType principalType,
            @Param("principalId") @NotNull Long principalId,
            @Param("currentStatus") @NotNull ImMessageStatus currentStatus,
            @Param("nextStatus") @NotNull ImMessageStatus nextStatus,
            @Param("revokedAt") @NotNull OffsetDateTime revokedAt);
}
