package com.sca4d.authentication.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.email.EmailCodeSecondFactorMethod;
import com.sca4d.authentication.totp.TotpSecondFactorMethod;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactStatus;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.totp.repository.TotpRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

/** 验证邮件验证码和 TOTP 只按各自当前认证资料判断第二因素可用性。 */
class AccountSecondFactorMethodTest {

    @Test
    void shouldRequireVerifiedEmailForEmailCode() {
        ContactService contacts = mock(ContactService.class);
        IamAccountEntity account = account();
        when(contacts.current(10L, ContactType.EMAIL)).thenReturn(new IamContactEntity().setId(30L).setSubjectId(10L).setContactType(ContactType.EMAIL)
                .setValue("user@example.com").setVerifiedAt(OffsetDateTime.now()).setStatus(ContactStatus.ACTIVE).setVersion(0L));

        assertThat(new EmailCodeSecondFactorMethod(contacts).available(account, null))
                .isTrue();
    }

    @Test
    void shouldRejectUnverifiedEmailForEmailCode() {
        ContactService contacts = mock(ContactService.class);
        IamAccountEntity account = account();
        when(contacts.current(10L, ContactType.EMAIL)).thenReturn(new IamContactEntity().setId(30L).setSubjectId(10L).setContactType(ContactType.EMAIL)
                .setValue("user@example.com").setVerifiedAt(null).setStatus(ContactStatus.ACTIVE).setVersion(0L));

        assertThat(new EmailCodeSecondFactorMethod(contacts).available(account, null))
                .isFalse();
    }

    @Test
    void shouldRequireActiveTotpCredential() {
        TotpRepository repository = mock(TotpRepository.class);
        IamAccountEntity account = account();
        when(repository.hasActiveCredential(20L, null)).thenReturn(true);

        assertThat(new TotpSecondFactorMethod(repository).available(account, null)).isTrue();
    }

    @Test
    void shouldRejectMissingTotpCredential() {
        TotpRepository repository = mock(TotpRepository.class);

        assertThat(new TotpSecondFactorMethod(repository).available(account(), null)).isFalse();
    }

    @Test
    void shouldExcludeTotpCredentialBeingRemoved() {
        TotpRepository repository = mock(TotpRepository.class);
        when(repository.hasActiveCredential(20L, 40L)).thenReturn(false);

        assertThat(new TotpSecondFactorMethod(repository).available(account(), 40L)).isFalse();
    }

    /** @return 当前 Subject 名下未启用第二因素的 GLOBAL Account 测试对象 */
    private IamAccountEntity account() {
        return new IamAccountEntity().setId(20L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null).setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false).setVersion(3L);
    }
}
