package com.sca4d.common.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.entity.MessageOutboxEntity;
import com.sca4d.mapper.MessageOutboxMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

/** 验证 Outbox 消息保存、租约领取和发送状态更新。 */
class MessageOutboxRepositoryTest {

    @Test
    void shouldUpdateStatusToSentWhenPublished() {
        MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
        MessageOutboxRepository repository = new MessageOutboxRepository(mapper, new Sca4djMessagingProperties());
        OffsetDateTime now = OffsetDateTime.parse("2026-07-09T10:00:00+08:00");

        MessageOutboxEntity message = new MessageOutboxEntity().setId(1L).setVersion(4L);
        when(mapper.updateStatus(
                eq(1L),
                eq(4L),
                eq(MessageOutboxStatus.SENDING),
                eq(MessageOutboxStatus.SENT),
                eq(0),
                isNull(),
                isNull(),
                eq(now),
                eq(now)))
                .thenReturn(1);

        assertThat(repository.markPublished(message, now)).isTrue();

        verify(mapper).updateStatus(
                eq(1L),
                eq(4L),
                eq(MessageOutboxStatus.SENDING),
                eq(MessageOutboxStatus.SENT),
                eq(0),
                isNull(),
                isNull(),
                eq(now),
                eq(now));
    }

    @Test
    void shouldUpdateStatusToRetryWhenAttemptsRemain() {
        MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
        MessageOutboxRepository repository = new MessageOutboxRepository(mapper, new Sca4djMessagingProperties());
        OffsetDateTime now = OffsetDateTime.parse("2026-07-09T10:00:00+08:00");
        MessageOutboxEntity message = new MessageOutboxEntity()
                .setId(1L)
                .setAttempts(1)
                .setMaxAttempts(3)
                .setVersion(5L);

        repository.markRetryOrDead(message, new RuntimeException("boom"), now);

        verify(mapper).updateStatus(
                eq(1L),
                eq(5L),
                eq(MessageOutboxStatus.SENDING),
                eq(MessageOutboxStatus.FAILED),
                eq(1),
                any(OffsetDateTime.class),
                eq(RuntimeException.class.getName()),
                isNull(),
                eq(now));
    }

    @Test
    void shouldUpdateStatusToDeadWhenAttemptsExceeded() {
        MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
        MessageOutboxRepository repository = new MessageOutboxRepository(mapper, new Sca4djMessagingProperties());
        OffsetDateTime now = OffsetDateTime.parse("2026-07-09T10:00:00+08:00");
        MessageOutboxEntity message = new MessageOutboxEntity()
                .setId(1L)
                .setAttempts(9)
                .setMaxAttempts(10)
                .setVersion(6L);

        repository.markRetryOrDead(message, new RuntimeException("boom"), now);

        verify(mapper).updateStatus(
                eq(1L),
                eq(6L),
                eq(MessageOutboxStatus.SENDING),
                eq(MessageOutboxStatus.DEAD),
                eq(1),
                eq(now),
                eq(RuntimeException.class.getName()),
                isNull(),
                eq(now));
    }

    @Test
    void shouldReportLostLeaseWithoutOverwritingNewOwner() {
        MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
        MessageOutboxRepository repository = new MessageOutboxRepository(mapper, new Sca4djMessagingProperties());
        OffsetDateTime now = OffsetDateTime.parse("2026-07-09T10:00:00+08:00");
        MessageOutboxEntity message = new MessageOutboxEntity()
                .setId(1L)
                .setAttempts(1)
                .setMaxAttempts(3)
                .setVersion(7L);

        assertThat(repository.markRetryOrDead(message, new RuntimeException("boom"), now)).isEmpty();
    }
}
