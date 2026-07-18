package com.sca4d.im.signature;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.signature.FileSignatureItem;
import com.sca4d.file.signature.FileSignaturePolicy;
import com.sca4d.im.ImMessageStatus;
import com.sca4d.im.error.ImErrorCode;
import com.sca4d.im.repository.ImRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** IM 附件签名判定。 */
@Component
@ConditionalOnProperty(prefix = "sca4dj.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ImFileSignaturePolicy implements FileSignaturePolicy {

    /** 即时消息附件上传和读取使用的文件签名场景。 */
    public static final String MESSAGE_ATTACHMENT = "im.message.attachment";

    private final ImRepository repository;

    @Override
    public @NotNull Set<String> scenes() {
        return Set.of(MESSAGE_ATTACHMENT);
    }

    @Override
    public void authorize(@NotNull CurrentRequest auth, @NotNull List<FileSignatureItem> items) {
        Principal principal = auth.getPrincipal();
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        Long principalId = principal.requirePrincipalId();
        Set<Long> readMessageIds = new LinkedHashSet<>();
        for (FileSignatureItem item : items) {
            Long resourceId = resourceId(item.getResourceId());
            if (item.getOperation() == FileSignatureOperation.READ) {
                readMessageIds.add(resourceId);
            }
        }

        // 全部 READ 消息固定 1 条 SQL；随后只在内存中校验文件归属并汇总会话主键。
        Map<Long, ImMessageEntity> messages = new LinkedHashMap<>();
        for (ImMessageEntity message : repository.findMessages(List.copyOf(readMessageIds))) {
            messages.put(message.getId(), message);
        }
        Set<Long> conversationIds = new LinkedHashSet<>();
        for (FileSignatureItem item : items) {
            Long resourceId = resourceId(item.getResourceId());
            if (item.getOperation() == FileSignatureOperation.UPLOAD) {
                conversationIds.add(resourceId);
                continue;
            }
            ImMessageEntity message = messages.get(resourceId);
            if (message == null
                    || message.getStatus() != ImMessageStatus.NORMAL
                    || message.getAttachmentFileId() == null
                    || !message.getAttachmentFileId().equals(item.getFileId())) {
                throw new BizException(ImErrorCode.MESSAGE_NOT_FOUND);
            }
            conversationIds.add(message.getConversationId());
        }

        // 整批固定再执行 1 条会话存在性和 1 条成员关系 SQL，总查询数最多为 3，不随 items 数量增长。
        List<Long> conversationIdList = List.copyOf(conversationIds);
        Set<Long> existingConversationIds = repository.findExistingConversationIds(conversationIdList);
        Set<Long> memberConversationIds = repository.findMemberConversationIds(
                conversationIdList, principal.getPrincipalType(), principalId);
        for (FileSignatureItem item : items) {
            Long resourceId = resourceId(item.getResourceId());
            Long conversationId = item.getOperation() == FileSignatureOperation.UPLOAD
                    ? resourceId
                    : messages.get(resourceId).getConversationId();
            if (!existingConversationIds.contains(conversationId)) {
                throw new BizException(ImErrorCode.CONVERSATION_NOT_FOUND);
            }
            if (!memberConversationIds.contains(conversationId)) {
                throw new BizException(ImErrorCode.NOT_CONVERSATION_MEMBER);
            }
        }
    }

    /**
     * 将业务签名项中的资源标识解析为 IM 主键。
     *
     * @param value 会话或消息资源标识
     * @return 可用于批量查询的 IM 主键
     * @throws BizException 标识不是有效长整型时抛出
     */
    private @NotNull Long resourceId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException exception) {
            throw new BizException(ImErrorCode.FILE_RESOURCE_ID_INVALID);
        }
    }
}
