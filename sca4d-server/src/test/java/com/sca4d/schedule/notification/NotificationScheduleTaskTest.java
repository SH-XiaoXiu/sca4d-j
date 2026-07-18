package com.sca4d.schedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.resource.PrincipalReference;
import com.sca4d.iam.resource.SubjectReference;
import com.sca4d.notification.NotificationCategory;
import com.sca4d.notification.NotificationRecipient;
import com.sca4d.notification.NotificationService;
import com.sca4d.schedule.ScheduleTaskExecution;
import com.sca4d.schedule.ScheduleTaskResult;
import com.sca4d.schedule.TriggerSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** 验证通知 ScheduleTask 固定接收人并创建站内通知。 */
class NotificationScheduleTaskTest {

    @Test
    void shouldDeduplicateSnapshotAndSkipDeletedSubjectAtExecution() {
        IamRepository iamRepository = mock(IamRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationScheduleTask task = new NotificationScheduleTask(
                new ObjectMapper(), iamRepository, notificationService);
        NotificationSchedulePayload payload = new NotificationSchedulePayload();
        payload.setTitle("维护通知");
        payload.setContent("今晚执行维护");
        payload.setCategory(NotificationCategory.SYSTEM);
        payload.setRecipients(List.of(
                NotificationScheduleRecipient.defaultPrincipal(1L, 101L),
                NotificationScheduleRecipient.defaultPrincipal(1L, 101L),
                NotificationScheduleRecipient.defaultPrincipal(2L, 102L)));
        SubjectReference first = new SubjectReference(1L, "First", false);
        SubjectReference second = new SubjectReference(2L, "Second", false);
        SubjectReference deletedSecond = new SubjectReference(2L, "Second", true);
        when(iamRepository.findSubjectReferences(Set.of(1L, 2L)))
                .thenReturn(List.of(first, second), List.of(first, deletedSecond));
        when(iamRepository.findPrincipalReferences(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new PrincipalReference(101L, 1L, null, PrincipalType.DEFAULT, "First", false),
                        new PrincipalReference(102L, 2L, null, PrincipalType.DEFAULT, "Second", false)));

        task.validate(payload);
        ScheduleTaskResult result = task.execute(
                payload,
                new ScheduleTaskExecution(
                        10L,
                        20L,
                        "10:scheduled:1",
                        TriggerSource.SCHEDULED,
                        OffsetDateTime.parse("2026-07-14T10:00:00Z"),
                        OffsetDateTime.parse("2026-07-14T10:00:01Z")));

        assertThat(payload.getRecipients()).hasSize(2);
        assertThat(result.getSummary()).isEqualTo("recipientCount=1, skippedRecipientCount=1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationRecipient>> recipients = ArgumentCaptor.forClass(List.class);
        verify(notificationService).create(
                eq("维护通知"),
                eq("今晚执行维护"),
                eq(NotificationCategory.SYSTEM),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                any(),
                recipients.capture());
        assertThat(recipients.getValue()).singleElement().satisfies(recipient -> {
            assertThat(recipient.getSubjectId()).isEqualTo(1L);
            assertThat(recipient.getPrincipalId()).isEqualTo(101L);
        });
    }

    @Test
    void shouldRejectDeletedSnapshotRecipientAndUnknownPayloadVersion() {
        IamRepository iamRepository = mock(IamRepository.class);
        NotificationScheduleTask task = new NotificationScheduleTask(
                new ObjectMapper(), iamRepository, mock(NotificationService.class));
        NotificationSchedulePayload payload = new NotificationSchedulePayload();
        payload.setTitle("通知");
        payload.setContent("正文");
        payload.setCategory(NotificationCategory.SYSTEM);
        payload.setRecipients(List.of(NotificationScheduleRecipient.defaultPrincipal(3L, 103L)));
        when(iamRepository.findSubjectReferences(Set.of(3L)))
                .thenReturn(List.of(new SubjectReference(3L, "Deleted", true)));
        when(iamRepository.findPrincipalReferences(Set.of(103L)))
                .thenReturn(List.of(new PrincipalReference(103L, 3L, null, PrincipalType.DEFAULT, "Deleted", false)));

        assertThatThrownBy(() -> task.validate(payload)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> task.decode(2, "{}"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.schedule.payload-version-unknown"));
    }
}
