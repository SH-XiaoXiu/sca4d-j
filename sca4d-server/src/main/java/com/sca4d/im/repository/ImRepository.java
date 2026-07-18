package com.sca4d.im.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.ImConversationEntity;
import com.sca4d.entity.ImConversationMemberEntity;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.im.ImConversation;
import com.sca4d.im.ImMessage;
import com.sca4d.im.ImMessageStatus;
import com.sca4d.mapper.ImMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * IM 仓储。
 */
@Repository
@RequiredArgsConstructor
public class ImRepository {

    private final ImMapper mapper;
    private final IdGenerator idGenerator;

    /**
     * 删除已失效 Principal 的全部活跃和历史会话成员关系。
     *
     * <p>输入为空时不执行 SQL；非空集合固定执行一条批量删除。会话和消息历史保持不变。
     *
     * @param principalIds 已隔离 TENANT Principal 主键
     */
    public void deleteMembersByPrincipalIds(@NotNull List<Long> principalIds) {
        if (!principalIds.isEmpty()) {
            mapper.deleteMembersByPrincipalIds(List.copyOf(new LinkedHashSet<>(principalIds)));
        }
    }

    /**
     * 创建会话并回填集群主键。
     *
     * @param conversation 会话实体
     */
    public void createConversation(@NotNull ImConversationEntity conversation) {
        if (conversation.getId() == null) {
            conversation.setId(idGenerator.nextId());
        }
        if (mapper.insertConversation(conversation) != 1) {
            throw new IllegalStateException("IM 会话写入行数异常");
        }
    }

    /**
     * 写入会话初始成员。
     *
     * @param members 已关联会话主键的初始成员
     */
    public void addConversationMembers(@NotNull List<ImConversationMemberEntity> members) {
        members.forEach(member -> {
            if (member.getId() == null) {
                member.setId(idGenerator.nextId());
            }
        });
        if (members.isEmpty() || mapper.insertMembers(members) != members.size()) {
            throw new IllegalStateException("IM 会话成员写入行数异常");
        }
    }

    /**
     * 查询会话。
     *
     * @param conversationId 会话主键
     * @return 会话实体
     */
    public @NotNull Optional<ImConversationEntity> findConversation(@NotNull Long conversationId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(ImConversationEntity.class)
                .where(ImConversationEntity::getId)
                .eq(conversationId)
                .and(ImConversationEntity::getDeleted)
                .eq(0);
        return Optional.ofNullable(mapper.selectOneByQueryAs(wrapper, ImConversationEntity.class));
    }

    /**
     * 批量查询仍存在的会话主键。
     *
     * <p>无论输入多少主键都只执行 1 条单表查询；空输入固定为 0 条 SQL。
     *
     * @param conversationIds 去重前的候选会话主键
     * @return 当前未删除会话的主键集合
     */
    public @NotNull Set<Long> findExistingConversationIds(@NotNull List<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Set.of();
        }
        QueryWrapper wrapper = QueryWrapper.create()
                .select(column(ImConversationEntity::getId))
                .from(ImConversationEntity.class)
                .where(ImConversationEntity::getId)
                .in(conversationIds)
                .and(ImConversationEntity::getDeleted)
                .eq(0);
        return new LinkedHashSet<>(mapper.selectObjectListByQueryAs(wrapper, Long.class));
    }

    /**
     * 查询会话成员。
     *
     * @param conversationId 会话主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 会话成员
     */
    public @NotNull Optional<ImConversationMemberEntity> findMember(
            @NotNull Long conversationId, @NotNull PrincipalType principalType, @NotNull Long principalId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(ImConversationMemberEntity.class)
                .where(ImConversationMemberEntity::getConversationId)
                .eq(conversationId)
                .and(ImConversationMemberEntity::getPrincipalType)
                .eq(principalType)
                .and(ImConversationMemberEntity::getPrincipalId)
                .eq(principalId)
                .and(ImConversationMemberEntity::getDeleted)
                .eq(0);
        return Optional.ofNullable(mapper.selectOneByQueryAs(wrapper, ImConversationMemberEntity.class));
    }

    /**
     * 批量查询当前 Principal 已加入的会话主键。
     *
     * <p>无论输入多少会话都只执行 1 条成员关系查询；空输入固定为 0 条 SQL。
     *
     * @param conversationIds 候选会话主键
     * @param principalType 当前 Principal 类型
     * @param principalId 当前 Principal 主键
     * @return 当前 Principal 仍是成员的会话主键集合
     */
    public @NotNull Set<Long> findMemberConversationIds(
            @NotNull List<Long> conversationIds,
            @NotNull PrincipalType principalType,
            @NotNull Long principalId) {
        if (conversationIds.isEmpty()) {
            return Set.of();
        }
        QueryWrapper wrapper = QueryWrapper.create()
                .select(column(ImConversationMemberEntity::getConversationId))
                .from(ImConversationMemberEntity.class)
                .where(ImConversationMemberEntity::getConversationId)
                .in(conversationIds)
                .and(ImConversationMemberEntity::getPrincipalType)
                .eq(principalType)
                .and(ImConversationMemberEntity::getPrincipalId)
                .eq(principalId)
                .and(ImConversationMemberEntity::getDeleted)
                .eq(0);
        return new LinkedHashSet<>(mapper.selectObjectListByQueryAs(wrapper, Long.class));
    }

    /**
     * 查询会话全部成员。
     *
     * @param conversationId 会话主键
     * @return 成员列表
     */
    public @NotNull List<ImConversationMemberEntity> findMembers(@NotNull Long conversationId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(ImConversationMemberEntity.class)
                .where(ImConversationMemberEntity::getConversationId)
                .eq(conversationId)
                .and(ImConversationMemberEntity::getDeleted)
                .eq(0)
                .orderBy(ImConversationMemberEntity::getId)
                .asc();
        return mapper.selectListByQueryAs(wrapper, ImConversationMemberEntity.class);
    }

    /**
     * 分页查询当前主体会话。
     *
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param page 页码
     * @param size 每页数量
     * @return 会话分页
     */
    public @NotNull PageData<ImConversation> pageConversations(
            @NotNull PrincipalType principalType, @NotNull Long principalId, long page, long size) {
        return PageData.from(mapper.selectConversationPage(
                new Page<>(page, size), principalType, principalId, ImMessageStatus.NORMAL));
    }

    /**
     * 分页查询会话消息。
     *
     * @param conversationId 会话主键
     * @param page 页码
     * @param size 每页记录数
     * @return 消息分页
     */
    public @NotNull PageData<ImMessage> pageMessages(
            @NotNull Long conversationId, long page, long size) {
        return PageData.from(mapper.selectMessagePage(new Page<>(page, size), conversationId));
    }

    /**
     * 查询消息。
     *
     * @param messageId 消息主键
     * @return 消息实体
     */
    public @NotNull Optional<ImMessageEntity> findMessage(@NotNull Long messageId) {
        return Optional.ofNullable(mapper.selectMessageById(messageId));
    }

    /**
     * 批量查询未删除消息。
     *
     * <p>无论输入多少消息主键都只执行 1 条单表查询；空输入固定为 0 条 SQL。
     *
     * @param messageIds 去重后的候选消息主键
     * @return 当前仍存在的消息实体
     */
    public @NotNull List<ImMessageEntity> findMessages(@NotNull List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper wrapper = QueryWrapper.create()
                .from(ImMessageEntity.class)
                .where(ImMessageEntity::getId)
                .in(messageIds)
                .and(ImMessageEntity::getDeleted)
                .eq(0);
        return mapper.selectListByQueryAs(wrapper, ImMessageEntity.class);
    }

    /**
     * 追加消息并更新会话消息投影。
     *
     * @param message 消息实体
     * @param payloadJson 扩展载荷 JSON
     * @param preview 消息预览
     * @param at 更新时间
     */
    public void appendMessage(
            @NotNull ImMessageEntity message,
            @NotNull String payloadJson,
            @NotNull String preview,
            @NotNull OffsetDateTime at) {
        if (message.getId() == null) {
            message.setId(idGenerator.nextId());
        }
        if (mapper.insertMessage(message, payloadJson) != 1) {
            throw new IllegalStateException("IM 消息写入行数异常");
        }
        if (mapper.updateConversationLastMessage(message.getConversationId(), message.getId(), preview, at) != 1) {
            throw new IllegalStateException("IM 会话消息投影更新行数异常");
        }
    }

    /**
     * 标记成员已读消息。
     *
     * @param conversationId 会话主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param messageId 消息主键
     * @param at 已读时间
     * @return 已读游标实际向前推进时返回 true
     */
    public boolean markMessageRead(
            @NotNull Long conversationId,
            @NotNull PrincipalType principalType,
            @NotNull Long principalId,
            @NotNull Long messageId,
            @NotNull OffsetDateTime at) {
        return mapper.updateMemberReadCursor(conversationId, principalType, principalId, messageId, at) == 1;
    }

    /**
     * 撤回本人发送的指定状态消息。
     *
     * @param messageId 消息主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @param currentStatus 当前可撤回状态
     * @param revokedStatus 撤回后状态
     * @param revokedAt 撤回时间
     * @return 消息仍由当前 Principal 发送且处于可撤回状态时返回 true
     */
    public boolean revokeOwnMessage(
            @NotNull Long messageId,
            @NotNull PrincipalType principalType,
            @NotNull Long principalId,
            @NotNull ImMessageStatus currentStatus,
            @NotNull ImMessageStatus revokedStatus,
            @NotNull OffsetDateTime revokedAt) {
        return mapper.updateMessageStatusBySenderAndStatus(
                messageId, principalType, principalId, currentStatus, revokedStatus, revokedAt) == 1;
    }
}
