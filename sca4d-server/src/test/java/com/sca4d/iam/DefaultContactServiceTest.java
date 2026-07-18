package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.contact.ContactStatus;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.contact.repository.ContactRepository;
import com.sca4d.iam.contact.service.DefaultContactService;
import com.sca4d.iam.error.IamErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;

/** 验证 Contact 替换时保留验证状态并规范化新值。 */
class DefaultContactServiceTest {

    @Test
    void shouldKeepVerifiedContactWhenNormalizedValueDoesNotChange() {
        ContactRepository repository = Mockito.mock(ContactRepository.class);
        when(repository.findCurrent(10L, ContactType.EMAIL)).thenReturn(new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.EMAIL)
                .setValue("Alice@Example.com").setVerifiedAt(null).setStatus(ContactStatus.ACTIVE).setVersion(0L));
        DefaultContactService service = new DefaultContactService(repository);

        service.replace(10L, ContactType.EMAIL, "  ALICE@example.com  ");

        verify(repository, never()).replace(10L, ContactType.EMAIL, "ALICE@example.com", "alice@example.com");
    }

    @Test
    void shouldReplaceChangedContactAsNormalizedUnverifiedValue() {
        ContactRepository repository = Mockito.mock(ContactRepository.class);
        when(repository.findCurrent(10L, ContactType.EMAIL)).thenReturn(new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.EMAIL)
                .setValue("old@example.com").setVerifiedAt(null).setStatus(ContactStatus.ACTIVE).setVersion(0L));
        DefaultContactService service = new DefaultContactService(repository);

        service.replace(10L, ContactType.EMAIL, "  New@Example.com  ");

        verify(repository).replace(10L, ContactType.EMAIL, "New@Example.com", "new@example.com");
    }

    @Test
    void shouldDeleteCurrentContactWhenValueIsBlank() {
        ContactRepository repository = Mockito.mock(ContactRepository.class);
        when(repository.findCurrent(10L, ContactType.PHONE)).thenReturn(new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.PHONE)
                .setValue("13800138000").setVerifiedAt(null).setStatus(ContactStatus.ACTIVE).setVersion(0L));
        DefaultContactService service = new DefaultContactService(repository);

        service.replace(10L, ContactType.PHONE, "   ");

        verify(repository).replace(10L, ContactType.PHONE, null, null);
    }

    @Test
    void shouldVerifyCurrentContactByTypeAndVersion() {
        ContactRepository repository = Mockito.mock(ContactRepository.class);
        IamContactEntity current = new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.PHONE).setValue("13800138000")
                .setVerifiedAt(null).setStatus(ContactStatus.ACTIVE).setVersion(3L);
        IamContactEntity verified = new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.PHONE).setValue("13800138000")
                .setVerifiedAt(OffsetDateTime.now()).setStatus(ContactStatus.ACTIVE).setVersion(4L);
        when(repository.findCurrent(10L, ContactType.PHONE)).thenReturn(current);
        when(repository.verify(20L, 10L, ContactType.PHONE, 3L)).thenReturn(verified);

        IamContactEntity result = new DefaultContactService(repository)
                .verify(10L, 20L, ContactType.PHONE, 3L);

        assertThat(result).isSameAs(verified);
        verify(repository).verify(20L, 10L, ContactType.PHONE, 3L);
    }

    @Test
    void shouldKeepExistingVerificationFactWithoutAnotherWrite() {
        ContactRepository repository = Mockito.mock(ContactRepository.class);
        IamContactEntity verified = new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.EMAIL).setValue("user@example.com")
                .setVerifiedAt(OffsetDateTime.now()).setStatus(ContactStatus.ACTIVE).setVersion(3L);
        when(repository.findCurrent(10L, ContactType.EMAIL)).thenReturn(verified);

        assertThat(new DefaultContactService(repository).verify(10L, 20L, ContactType.EMAIL, 3L))
                .isSameAs(verified);
        verify(repository, never()).verify(20L, 10L, ContactType.EMAIL, 3L);
    }

    @Test
    void shouldAcceptRetriedVerificationAfterFirstWriteCompleted() {
        ContactRepository repository = Mockito.mock(ContactRepository.class);
        IamContactEntity verified = new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.EMAIL).setValue("user@example.com")
                .setVerifiedAt(OffsetDateTime.now()).setStatus(ContactStatus.ACTIVE).setVersion(4L);
        when(repository.findCurrent(10L, ContactType.EMAIL)).thenReturn(verified);

        assertThat(new DefaultContactService(repository).verify(10L, 20L, ContactType.EMAIL, 3L))
                .isSameAs(verified);
        verify(repository, never()).verify(20L, 10L, ContactType.EMAIL, 3L);
    }

    @Test
    void shouldRejectChangedContactWhenRequiringVerification() {
        ContactRepository repository = Mockito.mock(ContactRepository.class);
        when(repository.findCurrent(10L, ContactType.PHONE))
                .thenReturn(new IamContactEntity().setId(20L).setSubjectId(10L).setContactType(ContactType.PHONE).setValue("13800138000")
                        .setVerifiedAt(OffsetDateTime.now()).setStatus(ContactStatus.ACTIVE).setVersion(4L));

        assertThatThrownBy(() -> new DefaultContactService(repository)
                .requireVerified(10L, 20L, ContactType.PHONE, 3L))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getErrorCode())
                        .isEqualTo(IamErrorCode.CONTACT_CHANGED));
    }
}
