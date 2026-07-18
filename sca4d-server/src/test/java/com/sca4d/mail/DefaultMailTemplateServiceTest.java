package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.mail.repository.MailTemplateRepository;
import com.sca4d.mail.service.DefaultMailTemplateService;
import org.junit.jupiter.api.Test;

import java.util.Map;

/** 验证模板标识规范化、启停约束、业务选用和测试发送。 */
class DefaultMailTemplateServiceTest {

    private final MailTemplateRepository repository = mock(MailTemplateRepository.class);
    private final MailService mailService = mock(MailService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final DefaultMailTemplateService service = new DefaultMailTemplateService(
            repository, new MailTemplateRenderer(), mailService, idGenerator);

    @Test
    void shouldNormalizeFamilyAndLocaleWhenCreating() {
        when(idGenerator.nextId()).thenReturn(123L);

        Long templateId = service.create(
                "  Auth.Email-Code ",
                "zh-cn",
                "验证码 {{code}}",
                "你的验证码是 {{code}}",
                "<strong>{{code}}</strong>");

        assertThat(templateId).isEqualTo(123L);
        verify(repository).createTemplate(
                123L,
                "auth.email-code",
                "zh-CN",
                "验证码 {{code}}",
                "你的验证码是 {{code}}",
                "<strong>{{code}}</strong>");
    }

    @Test
    void shouldRenderOnlyAnActiveExactFamilyAndLocaleTemplate() {
        when(repository.findActive("auth.email-code", "en-US")).thenReturn(template(MailTemplateStatus.ACTIVE));

        RenderedMail mail = service.render(
                "AUTH.EMAIL-CODE", "en-us", Map.of("code", "123456"));

        assertThat(mail.getSubject()).isEqualTo("Code 123456");
        assertThat(mail.getText()).isEqualTo("Your code is 123456");
        assertThat(mail.getHtml()).isEqualTo("<strong>123456</strong>");
        assertThatThrownBy(() -> service.render(
                "auth.unknown", "en-US", Map.of()))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.ACTIVE_TEMPLATE_NOT_FOUND));
    }

    @Test
    void shouldAllowTestingDisabledTemplateButNotDeletingActiveTemplate() {
        MailTemplate disabled = template(MailTemplateStatus.DISABLED);
        when(repository.findTemplate(123L)).thenReturn(disabled);

        service.sendTest(123L, 456L, "alice@example.com", Map.of("code", "654321"));

        verify(mailService).send(
                456L,
                "alice@example.com",
                "Code 654321",
                "Your code is 654321",
                "<strong>654321</strong>");

        when(repository.findTemplate(123L)).thenReturn(template(MailTemplateStatus.ACTIVE));
        assertThatThrownBy(() -> service.delete(123L, 7L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.TEMPLATE_ACTIVE));
        verify(repository, never()).deleteTemplate(123L, 7L);
    }

    @Test
    void shouldExposeVariablesInFirstAppearanceOrder() {
        assertThat(service.variables(template(MailTemplateStatus.DISABLED)))
                .containsExactly("code");
    }

    private MailTemplate template(MailTemplateStatus status) {
        return new MailTemplate(
                123L,
                "auth.email-code",
                "en-US",
                "Code {{code}}",
                "Your code is {{code}}",
                "<strong>{{code}}</strong>",
                status,
                7L);
    }
}
