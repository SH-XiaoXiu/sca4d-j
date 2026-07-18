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
import com.sca4d.entity.SysMailTemplateEntity;
import com.sca4d.mail.repository.MailTemplateRepository;
import com.sca4d.mail.resource.MailTemplateResourceReferenceContributor;
import com.sca4d.system.SystemPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证邮件模板名称批量解析、权限隐藏和详情页地址。 */
class MailTemplateResourceReferenceContributorTest {

    @Test
    void shouldResolveTemplateLabelWithOneBatchQuery() {
        MailTemplateRepository repository = mock(MailTemplateRepository.class);
        when(repository.findTemplateReferences(Set.of(123L))).thenReturn(List.of(
                new SysMailTemplateEntity()
                        .setId(123L)
                        .setFamily("auth.email-code")
                        .setLocale("zh-CN")
                        .setDeleted(0)));
        MailTemplateResourceReferenceContributor contributor = new MailTemplateResourceReferenceContributor(
                repository, LocalizedMessagesFixture.create());
        ResourceReferenceKey key = new ResourceReferenceKey(MailResourceTypes.MAIL_TEMPLATE, "123");

        var reference = contributor.resolve(
                Set.of(key),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM,
                        10L,
                        null,
                        Set.of(SystemPermissions.MAIL_TEMPLATES_READ)))
                .get(key);

        assertThat(reference.getLabel()).isEqualTo("auth.email-code / zh-CN");
        assertThat(reference.getNavigationState()).isEqualTo(ResourceNavigationState.NAVIGABLE);
        assertThat(reference.getPath()).isEqualTo("/system/mail-templates/123");
        verify(repository).findTemplateReferences(Set.of(123L));
    }

    @Test
    void shouldHideTemplateLabelWithoutReadPermission() {
        MailTemplateRepository repository = mock(MailTemplateRepository.class);
        when(repository.findTemplateReferences(Set.of(123L))).thenReturn(List.of(
                new SysMailTemplateEntity()
                        .setId(123L)
                        .setFamily("auth.email-code")
                        .setLocale("zh-CN")
                        .setDeleted(0)));
        MailTemplateResourceReferenceContributor contributor = new MailTemplateResourceReferenceContributor(
                repository, LocalizedMessagesFixture.create());
        ResourceReferenceKey key = new ResourceReferenceKey(MailResourceTypes.MAIL_TEMPLATE, "123");

        var reference = contributor.resolve(
                Set.of(key),
                new ResourceReferenceContext(PrincipalType.PLATFORM, 10L, null, Set.of()))
                .get(key);

        assertThat(reference.getLabel()).isNull();
        assertThat(reference.getNavigationState()).isEqualTo(ResourceNavigationState.FORBIDDEN);
    }
}
