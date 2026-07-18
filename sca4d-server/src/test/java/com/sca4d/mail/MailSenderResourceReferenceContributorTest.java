package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.entity.SysMailSenderEntity;
import com.sca4d.mail.repository.MailSenderRepository;
import com.sca4d.mail.resource.MailSenderResourceReferenceContributor;
import com.sca4d.system.SystemPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证 SMTP 配置名称批量解析、权限隐藏和非法历史 ID。 */
class MailSenderResourceReferenceContributorTest {

    @Test
    void shouldResolveNamesWithOneBatchQueryAndRegisteredFrontendPath() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        when(repository.findSenderReferences(Set.of(123L))).thenReturn(List.of(
                new SysMailSenderEntity().setId(123L).setName("Primary SMTP").setDeleted(0)));
        MailSenderResourceReferenceContributor contributor = new MailSenderResourceReferenceContributor(
                repository, LocalizedMessagesFixture.create());
        ResourceReferenceKey existing = new ResourceReferenceKey(MailResourceTypes.MAIL_SENDER, "123");
        ResourceReferenceKey invalid = new ResourceReferenceKey(MailResourceTypes.MAIL_SENDER, "not-a-number");

        var references = contributor.resolve(
                Set.of(existing, invalid),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM,
                        10L,
                        null,
                        Set.of(SystemPermissions.MAIL_SENDERS_READ)));

        assertThat(references.get(existing).getLabel()).isEqualTo("Primary SMTP");
        assertThat(references.get(existing).getNavigationState())
                .isEqualTo(ResourceNavigationState.NAVIGABLE);
        assertThat(references.get(existing).getPath()).isEqualTo("/system/mail-senders/123");
        assertThat(references.get(invalid).getNavigationState())
                .isEqualTo(ResourceNavigationState.DELETED);
        verify(repository).findSenderReferences(Set.of(123L));
    }

    @Test
    void shouldHideSenderNameWithoutPlatformReadPermission() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        when(repository.findSenderReferences(Set.of(123L))).thenReturn(List.of(
                new SysMailSenderEntity().setId(123L).setName("Primary SMTP").setDeleted(0)));
        MailSenderResourceReferenceContributor contributor = new MailSenderResourceReferenceContributor(
                repository, LocalizedMessagesFixture.create());
        ResourceReferenceKey key = new ResourceReferenceKey(MailResourceTypes.MAIL_SENDER, "123");

        var reference = contributor.resolve(
                Set.of(key),
                new ResourceReferenceContext(PrincipalType.PLATFORM, 10L, null, Set.of()))
                .get(key);

        assertThat(reference.getLabel()).isNull();
        assertThat(reference.getNavigationState()).isEqualTo(ResourceNavigationState.FORBIDDEN);
    }
}
