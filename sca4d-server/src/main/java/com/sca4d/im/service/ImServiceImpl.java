package com.sca4d.im.service;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.ImConversationEntity;
import com.sca4d.entity.ImConversationMemberEntity;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.file.FileService;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.im.ImConversation;
import com.sca4d.im.ImConversationType;
import com.sca4d.im.ImMemberReference;
import com.sca4d.im.ImMemberRole;
import com.sca4d.im.ImMessage;
import com.sca4d.im.ImMessageContentType;
import com.sca4d.im.ImMessageStatus;
import com.sca4d.im.ImService;
import com.sca4d.im.codec.ImPayloadCodec;
import com.sca4d.im.error.ImErrorCode;
import com.sca4d.im.realtime.ImRealtimePublisher;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.im.signature.ImFileSignaturePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * IM 服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImServiceImpl implements ImService {

    private final @NotNull ImRepository repository;
    private final @NotNull ImPayloadCodec payloadCodec;
    private final @NotNull ImRealtimePublisher realtimePublisher;
    private final @NotNull FileService fileService;
    private final @NotNull PrincipalService principalService;

    @Override
    public @NotNull PageData<ImConversation> conversations(
            long page, long size, @NotNull Principal principal) {
        return repository.pageConversations(
                principal.requirePrincipalType(), principal.requirePrincipalId(), page, size);
    }

    @Transactional
    @Override
    public @NotNull ImConversation createConversation(
            @Nullable ImConversationType requestedConversationType,
            @Nullable String title,
            @Nullable List<ImMemberReference> requestedMembers,
            @NotNull Subject subject,
            @NotNull Principal principal) {
        Long currentPrincipalId = principal.requirePrincipalId();
        // 客户端只声明 Principal，Subject、展示名称、状态和租户范围全部从 IAM 真值解析。
        Map<Long, IamPrincipalEntity> members = new LinkedHashMap<>();
        if (requestedMembers != null) {
            for (ImMemberReference member : requestedMembers) {
                if (member == null || member.getPrincipalType() == null || member.getPrincipalId() == null) {
                    throw new BizException(ImErrorCode.MEMBER_PRINCIPAL_REQUIRED);
                }
                IamPrincipalEntity target = principalService.require(member.getPrincipalId());
                requireInitialMemberAllowed(member.getPrincipalType(), target, principal);
                members.putIfAbsent(target.getId(), target);
            }
        }

        // 创建人必须自动成为会话成员，避免调用方漏传自己导致后续无权访问。
        IamPrincipalEntity current = principalService.require(currentPrincipalId);
        requireInitialMemberAllowed(principal.requirePrincipalType(), current, principal);
        if (!subject.getSubjectId().equals(current.getSubjectId())) {
            throw new BizException(ImErrorCode.PRINCIPAL_SUBJECT_MISMATCH);
        }
        members.putIfAbsent(current.getId(), current);
        if (members.size() < 2) {
            throw new BizException(ImErrorCode.MEMBERS_TOO_FEW, 2);
        }

        // 未显式声明会话类型时按成员数量推断；显式声明时仍要校验成员数量是否匹配。
        ImConversationType conversationType = ImConversationType.resolve(requestedConversationType, members.size());
        if (!conversationType.supportsMemberSize(members.size())) {
            throw new BizException(ImErrorCode.MEMBER_COUNT_MISMATCH);
        }

        OffsetDateTime now = TimeSupport.now();
        ImConversationEntity conversation = new ImConversationEntity()
                .setConversationType(conversationType)
                .setTitle(StringUtils.hasText(title) ? title.trim() : null)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L)
                .setDeleted(0);
        repository.createConversation(conversation);
        Long conversationId = conversation.getId();
        List<ImConversationMemberEntity> memberEntities = members.values().stream().map(member -> {
            // 初始角色由会话类型和创建人身份决定，具体规则集中在业务模块枚举里。
            boolean creator = member.getPrincipalType() == principal.requirePrincipalType()
                    && member.getId().equals(currentPrincipalId);
            return new ImConversationMemberEntity()
                    .setConversationId(conversationId)
                    .setPrincipalType(member.getPrincipalType())
                    .setPrincipalId(member.getId())
                    .setSubjectId(member.getSubjectId())
                    .setDisplayName(member.getDisplayName())
                    .setMemberRole(ImMemberRole.initialRole(conversationType, creator))
                    .setJoinedAt(now)
                    .setCreatedAt(now)
                    .setUpdatedAt(now)
                    .setVersion(0L)
                    .setDeleted(0);
        }).toList();

        // 会话和初始成员必须同事务落库；公共实时发布器只会在事务提交后发送事件。
        repository.addConversationMembers(memberEntities);
        realtimePublisher.conversationUpdated(
                memberEntities.stream().map(ImConversationMemberEntity::getSubjectId).toList(), conversationId);
        log.info(
                "即时会话创建已写入事务，实时更新已登记 | conversationId={} conversationType={} memberCount={}",
                conversationId,
                conversationType,
                memberEntities.size());
        return new ImConversation()
                .setConversationId(conversationId)
                .setConversationType(conversationType)
                .setTitle(conversation.getTitle())
                .setUnreadCount(0L)
                .setUpdatedAt(now);
    }

    @Override
    public @NotNull PageData<ImMessage> messages(
            @NotNull Long conversationId, long page, long size, @NotNull Principal principal) {
        requireMember(conversationId, principal);
        return repository.pageMessages(conversationId, page, size);
    }

    @Transactional
    @Override
    public @NotNull ImMessage send(
            @Nullable Long conversationId,
            @Nullable ImMessageContentType requestedContentType,
            @Nullable String content,
            @Nullable Long attachmentFileId,
            @Nullable String attachmentUploadGrant,
            @Nullable Map<String, Object> payload,
            @NotNull Subject subject,
            @NotNull Principal principal) {
        Long principalId = principal.requirePrincipalId();
        if (conversationId == null) {
            throw new BizException(ImErrorCode.CONVERSATION_ID_REQUIRED);
        }
        ImMessageContentType contentType = ImMessageContentType.resolve(requestedContentType);
        if (contentType.requiresTextContent() && !StringUtils.hasText(content)) {
            throw new BizException(ImErrorCode.MESSAGE_CONTENT_REQUIRED);
        }
        if (contentType.requiresAttachmentFile() && attachmentFileId == null) {
            throw new BizException(ImErrorCode.IMAGE_ATTACHMENT_REQUIRED);
        }
        requireMember(conversationId, principal);
        if (contentType.requiresAttachmentFile()) {
            if (!StringUtils.hasText(attachmentUploadGrant)) {
                throw new BizException(ImErrorCode.IMAGE_UPLOAD_GRANT_REQUIRED);
            }
            fileService.consumeFile(
                    attachmentFileId,
                    attachmentUploadGrant,
                    ImFileSignaturePolicy.MESSAGE_ATTACHMENT,
                    conversationId.toString(),
                    principalId);
        }

        OffsetDateTime now = TimeSupport.now();
        ImMessageEntity message = new ImMessageEntity()
                .setConversationId(conversationId)
                .setSenderPrincipalType(principal.requirePrincipalType())
                .setSenderPrincipalId(principalId)
                .setSenderSubjectId(subject.getSubjectId())
                .setContentType(contentType)
                .setContent(contentType.normalizeContent(content))
                .setAttachmentFileId(attachmentFileId)
                .setPayloadJson(payloadCodec.encode(payload))
                .setStatus(ImMessageStatus.NORMAL)
                .setCreatedAt(now)
                .setVersion(0L)
                .setDeleted(0);

        repository.appendMessage(message, message.getPayloadJson(), contentType.preview(message.getContent()), now);
        realtimePublisher.messageCreated(
                repository.findMembers(message.getConversationId()).stream()
                        .map(ImConversationMemberEntity::getSubjectId)
                        .toList(),
                message.getConversationId(),
                message.getId());
        log.debug(
                "即时消息已写入事务，实时更新已登记 | messageId={} conversationId={} status={} contentType={} attachmentFileId={}",
                message.getId(),
                message.getConversationId(),
                message.getStatus(),
                message.getContentType(),
                message.getAttachmentFileId());
        return new ImMessage()
                .setMessageId(message.getId())
                .setConversationId(message.getConversationId())
                .setSenderPrincipalType(message.getSenderPrincipalType())
                .setSenderPrincipalId(message.getSenderPrincipalId())
                .setSenderSubjectId(message.getSenderSubjectId())
                .setContentType(message.getContentType())
                .setContent(message.getContent())
                .setAttachmentFileId(message.getAttachmentFileId())
                .setPayloadJson(message.getPayloadJson())
                .setStatus(message.getStatus())
                .setCreatedAt(message.getCreatedAt())
                .setRevokedAt(message.getRevokedAt());
    }

    @Transactional
    @Override
    public void read(@Nullable Long messageId, @NotNull Principal principal) {
        Long principalId = principal.requirePrincipalId();
        if (messageId == null) {
            throw new BizException(ImErrorCode.MESSAGE_ID_REQUIRED);
        }
        ImMessageEntity message = repository
                .findMessage(messageId)
                .orElseThrow(() -> new BizException(ImErrorCode.MESSAGE_NOT_FOUND));
        requireMember(message.getConversationId(), principal);

        OffsetDateTime now = TimeSupport.now();
        boolean advanced = repository.markMessageRead(
                message.getConversationId(),
                principal.requirePrincipalType(),
                principalId,
                message.getId(),
                now);
        if (advanced) {
            realtimePublisher.readUpdated(
                    repository.findMembers(message.getConversationId()).stream()
                            .map(ImConversationMemberEntity::getSubjectId)
                            .toList(),
                    message.getConversationId(),
                    message.getId());
            log.trace(
                    "即时会话阅读位置已写入事务 | messageId={} conversationId={} principalId={}",
                    message.getId(),
                    message.getConversationId(),
                    principalId);
        }
    }

    @Transactional
    @Override
    public void revoke(@NotNull Long messageId, @NotNull Principal principal) {
        Long principalId = principal.requirePrincipalId();
        PrincipalType principalType = principal.requirePrincipalType();
        ImMessageEntity message = repository
                .findMessage(messageId)
                .orElseThrow(() -> new BizException(ImErrorCode.MESSAGE_NOT_FOUND));
        requireMember(message.getConversationId(), principal);
        if (message.getStatus() == ImMessageStatus.REVOKED) {
            if (message.getSenderPrincipalType() == principalType
                    && message.getSenderPrincipalId().equals(principalId)) {
                return;
            }
            throw new BizException(ImErrorCode.MESSAGE_REVOKE_FORBIDDEN);
        }
        boolean revoked = repository.revokeOwnMessage(
                messageId,
                principalType,
                principalId,
                ImMessageStatus.NORMAL,
                ImMessageStatus.REVOKED,
                TimeSupport.now());
        if (!revoked) {
            throw new BizException(ImErrorCode.MESSAGE_REVOKE_FORBIDDEN);
        }
        if (message.getAttachmentFileId() != null) {
            fileService.requestDelete(message.getAttachmentFileId());
        }
        realtimePublisher.messageRevoked(
                repository.findMembers(message.getConversationId()).stream()
                        .map(ImConversationMemberEntity::getSubjectId)
                        .toList(),
                message.getConversationId(),
                messageId);
        log.info(
                "即时消息撤回已写入事务，实时更新已登记 | messageId={} conversationId={} attachmentFileId={}",
                messageId,
                message.getConversationId(),
                message.getAttachmentFileId());
    }

    /**
     * 校验当前主体是否为指定会话成员。
     *
     * @param conversationId 会话主键
     * @param principal 当前执行主体
     * @return 当前主体对应的会话成员
     */
    private @NotNull ImConversationMemberEntity requireMember(
            @NotNull Long conversationId, @NotNull Principal principal) {
        // 先区分会话不存在，再判断当前 Principal 是否为成员。
        repository.findConversation(conversationId).orElseThrow(() -> new BizException(ImErrorCode.CONVERSATION_NOT_FOUND));
        return repository
                .findMember(conversationId, principal.requirePrincipalType(), principal.requirePrincipalId())
                .orElseThrow(() -> new BizException(ImErrorCode.NOT_CONVERSATION_MEMBER));
    }

    /**
     * 校验候选成员来自 IAM 中的可用 Principal，并符合当前模板的初始成员准入策略。
     *
     * <p>同 Principal 类型、同租户是模板的安全默认值，不是 IM 存储对象的架构不变量。
     *
     * @param requestedType 客户端声明的 Principal 类型
     * @param target IAM 中读取的候选成员
     * @param creator 当前创建会话的 Principal
     */
    private void requireInitialMemberAllowed(
            @NotNull PrincipalType requestedType,
            @NotNull IamPrincipalEntity target,
            @NotNull Principal creator) {
        // TODO 当业务明确需要跨租户或跨 Principal 类型协作时，引入会话成员准入策略，并同步定义数据访问、审计、成员搜索和实时目标规则；IAM Principal 真值校验仍必须保留。
        if (target.getStatus() != PrincipalStatus.ACTIVE
                || target.getPrincipalType() != requestedType
                || target.getPrincipalType() != creator.getPrincipalType()
                || !Objects.equals(target.getTenantId(), creator.getTenantId())) {
            throw new BizException(ImErrorCode.MEMBER_WORKSPACE_MISMATCH);
        }
    }
}
