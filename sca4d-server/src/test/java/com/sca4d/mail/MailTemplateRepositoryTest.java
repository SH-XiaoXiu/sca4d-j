package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.mail.repository.MailTemplateRepository;
import com.sca4d.mapper.MailTemplateMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

/** 验证邮件模板唯一冲突只按 PostgreSQL 约束元数据转换。 */
class MailTemplateRepositoryTest {

    private final MailTemplateMapper mapper = mock(MailTemplateMapper.class);
    private final MailTemplateRepository repository = new MailTemplateRepository(mapper);

    @Test
    void shouldMapFamilyLocaleUniqueConstraint() {
        PSQLException sqlException = mock(PSQLException.class);
        ServerErrorMessage serverError = mock(ServerErrorMessage.class);
        when(sqlException.getSQLState()).thenReturn("23505");
        when(sqlException.getServerErrorMessage()).thenReturn(serverError);
        when(serverError.getConstraint()).thenReturn("uk_sys_mail_template_family_locale");
        when(mapper.insertTemplate(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate", sqlException));

        assertThatThrownBy(() -> repository.createTemplate(1L, "welcome", "zh-CN", "subject", "text", null))
                .isInstanceOfSatisfying(BizException.class, exception -> Assertions.assertThat(exception.getErrorCode())
                        .isEqualTo(MailErrorCode.TEMPLATE_EXISTS));
    }

    @Test
    void shouldNotInferConstraintFromExceptionMessage() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("uk_sys_mail_template_family_locale");
        when(mapper.insertTemplate(any())).thenThrow(exception);

        assertThatThrownBy(() -> repository.createTemplate(1L, "welcome", "zh-CN", "subject", "text", null))
                .isSameAs(exception);
    }
}
