package com.sca4d.im;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.signature.FileSignatureItem;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.im.signature.ImFileSignaturePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证会话附件的上传和读取授权只允许会话成员使用。 */
class ImFileSignaturePolicyTest {

    @Test
    void shouldAllowConversationMemberToUploadAttachment() {
        ImRepository repository = mock(ImRepository.class);
        when(repository.findExistingConversationIds(List.of(100L))).thenReturn(Set.of(100L));
        when(repository.findMemberConversationIds(List.of(100L), PrincipalType.DEFAULT, 1L))
                .thenReturn(Set.of(100L));
        ImFileSignaturePolicy policy = new ImFileSignaturePolicy(repository);

        assertThatCode(() -> policy.authorize(auth(), List.of(upload(100L), upload(100L))))
                .doesNotThrowAnyException();
        verify(repository).findExistingConversationIds(List.of(100L));
        verify(repository).findMemberConversationIds(List.of(100L), PrincipalType.DEFAULT, 1L);
    }

    @Test
    void shouldRejectFileIdThatDoesNotBelongToMessage() {
        ImRepository repository = mock(ImRepository.class);
        ImMessageEntity message = new ImMessageEntity()
                .setId(200L)
                .setConversationId(100L)
                .setAttachmentFileId(10L)
                .setStatus(ImMessageStatus.NORMAL);
        when(repository.findMessages(List.of(200L))).thenReturn(List.of(message));
        ImFileSignaturePolicy policy = new ImFileSignaturePolicy(repository);

        assertThatThrownBy(() -> policy.authorize(auth(), List.of(read(200L, 11L))))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldAuthorizeMixedBatchWithThreeRepositoryQueries() {
        ImRepository repository = mock(ImRepository.class);
        ImMessageEntity first = new ImMessageEntity()
                .setId(200L)
                .setConversationId(100L)
                .setAttachmentFileId(10L)
                .setStatus(ImMessageStatus.NORMAL);
        ImMessageEntity second = new ImMessageEntity()
                .setId(201L)
                .setConversationId(101L)
                .setAttachmentFileId(11L)
                .setStatus(ImMessageStatus.NORMAL);
        when(repository.findMessages(List.of(200L, 201L))).thenReturn(List.of(first, second));
        when(repository.findExistingConversationIds(List.of(100L, 101L, 102L)))
                .thenReturn(Set.of(100L, 101L, 102L));
        when(repository.findMemberConversationIds(
                List.of(100L, 101L, 102L), PrincipalType.DEFAULT, 1L))
                .thenReturn(Set.of(100L, 101L, 102L));
        ImFileSignaturePolicy policy = new ImFileSignaturePolicy(repository);

        assertThatCode(() -> policy.authorize(
                auth(), List.of(read(200L, 10L), read(201L, 11L), upload(102L))))
                .doesNotThrowAnyException();
        verify(repository).findMessages(List.of(200L, 201L));
        verify(repository).findExistingConversationIds(List.of(100L, 101L, 102L));
        verify(repository).findMemberConversationIds(
                List.of(100L, 101L, 102L), PrincipalType.DEFAULT, 1L);
    }

    /** @return 使用 DEFAULT Principal 1 的当前请求 */
    private CurrentRequest auth() {
        CurrentRequest auth = new CurrentRequest();
        auth.setPrincipal(Principal.of(1L, PrincipalType.DEFAULT, 1L, null));
        return auth;
    }

    /** @return 绑定指定会话主键的附件上传签名项目 */
    private FileSignatureItem upload(Long conversationId) {
        FileSignatureItem item = new FileSignatureItem();
        item.setOperation(FileSignatureOperation.UPLOAD);
        item.setResourceId(conversationId.toString());
        return item;
    }

    /** @return 绑定指定消息和文件主键的附件读取签名项目 */
    private FileSignatureItem read(Long messageId, Long fileId) {
        FileSignatureItem item = new FileSignatureItem();
        item.setOperation(FileSignatureOperation.READ);
        item.setResourceId(messageId.toString());
        item.setFileId(fileId);
        return item;
    }
}
