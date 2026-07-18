package com.sca4d.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.ImConversationEntity;
import com.sca4d.entity.ImConversationMemberEntity;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.mapper.ImMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

/** 验证即时会话、成员、消息和附件的数据库读写。 */
class ImRepositoryTest {

    @Test
    void shouldBuildTypedConversationMemberQuery() {
        ImMapper mapper = mock(ImMapper.class);
        ImConversationMemberEntity member = new ImConversationMemberEntity()
                .setId(3L)
                .setConversationId(1L)
                .setPrincipalType(PrincipalType.TENANT)
                .setPrincipalId(2L);
        when(mapper.selectOneByQueryAs(any(QueryWrapper.class), eq(ImConversationMemberEntity.class)))
                .thenReturn(member);

        assertThat(new ImRepository(mapper, mock(IdGenerator.class)).findMember(1L, PrincipalType.TENANT, 2L))
                .containsSame(member);

        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectOneByQueryAs(wrapperCaptor.capture(), eq(ImConversationMemberEntity.class));
    }

    @Test
    void shouldRejectIncompleteConversationAggregateWrite() {
        ImMapper mapper = mock(ImMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        ImRepository repository = new ImRepository(mapper, idGenerator);
        ImConversationEntity conversation = new ImConversationEntity();
        ImConversationMemberEntity member = new ImConversationMemberEntity().setConversationId(1L);
        when(mapper.insertConversation(conversation)).thenReturn(1);
        when(mapper.insertMembers(List.of(member))).thenReturn(0);

        repository.createConversation(conversation);
        assertThat(conversation.getId()).isEqualTo(1L);
        assertThatThrownBy(() -> repository.addConversationMembers(List.of(member)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("会话成员写入行数异常");
        assertThat(member.getId()).isEqualTo(2L);
    }

    @Test
    void shouldBuildThreeBatchAccessQueries() {
        ImMapper mapper = mock(ImMapper.class);
        ImRepository repository = new ImRepository(mapper, mock(IdGenerator.class));

        assertThat(repository.findMessages(List.of(10L, 11L))).isEmpty();
        assertThat(repository.findExistingConversationIds(List.of(20L, 21L))).isEmpty();
        assertThat(repository.findMemberConversationIds(
                List.of(20L, 21L), PrincipalType.DEFAULT, 1L))
                .isEmpty();

        ArgumentCaptor<QueryWrapper> objectQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper, times(2)).selectObjectListByQueryAs(objectQueryCaptor.capture(), eq(Long.class));
        ArgumentCaptor<QueryWrapper> messageQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectListByQueryAs(messageQueryCaptor.capture(), eq(ImMessageEntity.class));
    }

    @Test
    void shouldHideReadAndRevokeAffectedRows() {
        ImMapper mapper = mock(ImMapper.class);
        ImRepository repository = new ImRepository(mapper, mock(IdGenerator.class));
        OffsetDateTime at = OffsetDateTime.now();
        when(mapper.updateMemberReadCursor(1L, PrincipalType.TENANT, 2L, 3L, at)).thenReturn(1);
        when(mapper.updateMessageStatusBySenderAndStatus(
                3L,
                PrincipalType.TENANT,
                2L,
                ImMessageStatus.NORMAL,
                ImMessageStatus.REVOKED,
                at))
                .thenReturn(0);

        assertThat(repository.markMessageRead(1L, PrincipalType.TENANT, 2L, 3L, at)).isTrue();
        assertThat(repository.revokeOwnMessage(
                3L,
                PrincipalType.TENANT,
                2L,
                ImMessageStatus.NORMAL,
                ImMessageStatus.REVOKED,
                at))
                .isFalse();
    }
}
