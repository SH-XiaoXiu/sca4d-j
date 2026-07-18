package com.sca4d.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybatisflex.core.paginate.Page;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.NotificationDeliveryEntity;
import com.sca4d.entity.NotificationMessageEntity;
import com.sca4d.mapper.NotificationMapper;
import com.sca4d.notification.repository.NotificationRepository;
import com.sca4d.notification.repository.NotificationUnreadCountResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.OffsetDateTime;
import java.util.List;

/** 验证通知消息、投递记录、已读状态和未读数量查询。 */
class NotificationRepositoryTest {

    @Test
    void shouldPersistMessageAndDeliveriesAsOneAggregate() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1L, 2L, 3L);
        NotificationRepository repository = new NotificationRepository(mapper, idGenerator);
        NotificationMessageEntity message = new NotificationMessageEntity();
        NotificationDeliveryEntity first = new NotificationDeliveryEntity().setNotificationId(1L);
        NotificationDeliveryEntity second = new NotificationDeliveryEntity().setNotificationId(1L);
        when(mapper.insertMessage(message, "{}")).thenReturn(1);
        when(mapper.insertDeliveries(List.of(first, second))).thenReturn(2);

        repository.createMessage(message, "{}");
        repository.createDeliveries(List.of(first, second));

        assertThat(message.getId()).isEqualTo(1L);
        assertThat(first.getId()).isEqualTo(2L);
        assertThat(second.getId()).isEqualTo(3L);
        verify(mapper).insertDeliveries(List.of(first, second));
        when(mapper.insertDeliveries(List.of(first, second))).thenReturn(1);
        assertThatThrownBy(() -> repository.createDeliveries(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("批量写入行数异常");
    }

    @Test
    void shouldRestoreBatchUnreadCountsByRecipientIndex() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationRepository repository = new NotificationRepository(mapper, mock(IdGenerator.class));
        List<NotificationRecipient> recipients = List.of(
                NotificationRecipient.subject(11L),
                NotificationRecipient.principal(PrincipalType.TENANT, 22L));
        NotificationUnreadCountResult second = new NotificationUnreadCountResult();
        second.setTargetIndex(1);
        second.setUnreadCount(7L);
        NotificationUnreadCountResult first = new NotificationUnreadCountResult();
        first.setTargetIndex(0);
        first.setUnreadCount(3L);
        when(mapper.countUnreadByRecipients(recipients, NotificationDeliveryStatus.UNREAD))
                .thenReturn(List.of(second, first));

        assertThat(repository.countUnread(recipients)).containsExactly(3L, 7L);
    }

    @Test
    void shouldHideDeliveryUpdateAffectedRows() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationRepository repository = new NotificationRepository(mapper, mock(IdGenerator.class));
        OffsetDateTime readAt = OffsetDateTime.now();
        when(mapper.updateDeliveryStatus(
                1L, NotificationDeliveryStatus.UNREAD, NotificationDeliveryStatus.READ, readAt))
                .thenReturn(1);
        when(mapper.updateAllStatus(
                2L,
                null,
                null,
                NotificationDeliveryStatus.UNREAD,
                NotificationDeliveryStatus.READ,
                readAt))
                .thenReturn(0);

        assertThat(repository.markDeliveryRead(1L, readAt)).isTrue();
        assertThat(repository.markAllRead(2L, null, null, readAt)).isFalse();
    }

    @Test
    void shouldPassExplicitCurrentNotificationPageCriteria() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationRepository repository = new NotificationRepository(mapper, mock(IdGenerator.class));
        when(mapper.selectCurrentPage(
                ArgumentMatchers.<Page<NotificationMessage>>any(),
                ArgumentMatchers.eq(true),
                ArgumentMatchers.eq(11L),
                ArgumentMatchers.eq(PrincipalType.TENANT),
                ArgumentMatchers.eq(22L),
                ArgumentMatchers.eq(NotificationDeliveryStatus.UNREAD)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        repository.pageCurrent(true, 2, 15, 11L, PrincipalType.TENANT, 22L);

        verify(mapper).selectCurrentPage(
                ArgumentMatchers.argThat(page -> page.getPageNumber() == 2 && page.getPageSize() == 15),
                ArgumentMatchers.eq(true),
                ArgumentMatchers.eq(11L),
                ArgumentMatchers.eq(PrincipalType.TENANT),
                ArgumentMatchers.eq(22L),
                ArgumentMatchers.eq(NotificationDeliveryStatus.UNREAD));
    }
}
