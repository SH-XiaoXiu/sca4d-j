package com.sca4d.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.ImConversationEntity;
import com.sca4d.entity.ImConversationMemberEntity;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.file.FileService;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.im.codec.ImPayloadCodec;
import com.sca4d.im.realtime.ImRealtimePublisher;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.im.service.ImServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

/** 验证即时消息服务只允许会话成员读取和发送消息。 */
class ImServiceMemberBoundaryTest {

    @Test
    void shouldCreateMembersAfterRepositoryReturnsConversationId() {
        ImRepository repository = mock(ImRepository.class);
        PrincipalService principalService = mock(PrincipalService.class);
        ImRealtimePublisher realtimePublisher = mock(ImRealtimePublisher.class);
        IamPrincipalEntity creator = new IamPrincipalEntity().setId(10L).setSubjectId(100L).setTenantId(1L).setMembershipId(101L)
                .setPrincipalType(PrincipalType.TENANT).setDisplayName("Creator").setStatus(PrincipalStatus.ACTIVE).setVersion(0L);
        IamPrincipalEntity member = new IamPrincipalEntity().setId(20L).setSubjectId(200L).setTenantId(1L).setMembershipId(201L)
                .setPrincipalType(PrincipalType.TENANT).setDisplayName("Member").setStatus(PrincipalStatus.ACTIVE).setVersion(0L);
        when(principalService.require(10L)).thenReturn(creator);
        when(principalService.require(20L)).thenReturn(member);
        doAnswer(invocation -> {
            invocation.<ImConversationEntity>getArgument(0).setId(30L);
            return null;
        })
                .when(repository)
                .createConversation(any());
        ImServiceImpl service = new ImServiceImpl(
                repository,
                mock(ImPayloadCodec.class),
                realtimePublisher,
                mock(FileService.class),
                principalService);
        ImMemberReference reference = new ImMemberReference();
        reference.setPrincipalType(PrincipalType.TENANT);
        reference.setPrincipalId(20L);

        ImConversation conversation = service.createConversation(
                null,
                "会话",
                List.of(reference),
                new Subject(100L, "Creator"),
                Principal.withDisplayName(10L, PrincipalType.TENANT, 100L, 1L, "Creator"));

        ArgumentCaptor<List<ImConversationMemberEntity>> membersCaptor = ArgumentCaptor.captor();
        verify(repository).addConversationMembers(membersCaptor.capture());
        assertThat(conversation.getConversationId()).isEqualTo(30L);
        assertThat(conversation.getTitle()).isEqualTo("会话");
        assertThat(conversation.getUnreadCount()).isZero();
        assertThat(membersCaptor.getValue())
                .extracting(ImConversationMemberEntity::getConversationId)
                .containsOnly(30L);
        assertThat(membersCaptor.getValue())
                .extracting(ImConversationMemberEntity::getId)
                .containsOnlyNulls();
    }

    @Test
    void shouldRejectMemberOutsideCurrentTenantByDefaultPolicy() {
        PrincipalService principalService = mock(PrincipalService.class);
        when(principalService.require(20L)).thenReturn(new IamPrincipalEntity().setId(20L).setSubjectId(200L).setTenantId(2L).setMembershipId(201L)
                .setPrincipalType(PrincipalType.TENANT).setDisplayName("Member").setStatus(PrincipalStatus.ACTIVE).setVersion(0L));
        ImServiceImpl service = new ImServiceImpl(
                mock(ImRepository.class),
                mock(ImPayloadCodec.class),
                mock(ImRealtimePublisher.class),
                mock(FileService.class),
                principalService);
        ImMemberReference member = new ImMemberReference();
        member.setPrincipalType(PrincipalType.TENANT);
        member.setPrincipalId(20L);

        assertThatThrownBy(() -> service.createConversation(
                null,
                null,
                List.of(member),
                new Subject(100L, "Creator"),
                Principal.withDisplayName(10L, PrincipalType.TENANT, 100L, 1L, "Creator")))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.im.member-workspace-mismatch"));
    }

    @Test
    void memberRequestShouldOnlyExposePrincipalReference() {
        assertThat(ImMemberReference.class.getDeclaredFields())
                .extracting("name")
                .containsExactlyInAnyOrder("principalType", "principalId");
    }

    @Test
    void shouldKeepRepeatedOwnMessageRevocationIdempotent() {
        ImRepository repository = mock(ImRepository.class);
        ImMessageEntity message = new ImMessageEntity()
                .setId(3L)
                .setConversationId(1L)
                .setSenderPrincipalType(PrincipalType.TENANT)
                .setSenderPrincipalId(2L)
                .setStatus(ImMessageStatus.REVOKED);
        when(repository.findMessage(3L)).thenReturn(Optional.of(message));
        when(repository.findConversation(1L)).thenReturn(Optional.of(new ImConversationEntity().setId(1L)));
        when(repository.findMember(1L, PrincipalType.TENANT, 2L))
                .thenReturn(Optional.of(new ImConversationMemberEntity()));
        ImServiceImpl service = new ImServiceImpl(
                repository,
                mock(ImPayloadCodec.class),
                mock(ImRealtimePublisher.class),
                mock(FileService.class),
                mock(PrincipalService.class));

        service.revoke(
                3L,
                Principal.withDisplayName(2L, PrincipalType.TENANT, 100L, 1L, "Sender"));

        verify(repository, never()).revokeOwnMessage(any(), any(), any(), any(), any(), any());
    }
}
