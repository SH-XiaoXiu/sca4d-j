package com.sca4d.authentication.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.authentication.AuthMethodCatalog;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.email.EmailCodeAuthMethod;
import com.sca4d.authentication.email.EmailCodeChallengeService;
import com.sca4d.authentication.email.EmailCodeSecondFactorMethod;
import com.sca4d.iam.contact.ContactService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证认证方式目录只收集配置明确启用的可执行方式。 */
class AuthenticationMethodRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(EmailCodeChallengeService.class, () -> mock(EmailCodeChallengeService.class))
            .withBean(ContactService.class, () -> mock(ContactService.class))
            .withUserConfiguration(
                    AuthMethodCatalog.class,
                    EmailCodeAuthMethod.class,
                    EmailCodeSecondFactorMethod.class);

    @Test
    void shouldOmitEmailCodeWhenEmailCodeIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context.getBean(AuthMethodCatalog.class).methods()).isEmpty();
            assertThat(context).doesNotHaveBean(EmailCodeSecondFactorMethod.class);
        });
    }

    @Test
    void shouldRegisterEmailCodeWhenEmailCodeIsEnabled() {
        contextRunner
                .withPropertyValues("sca4dj.authentication.email-code.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(AuthMethodCatalog.class).methods())
                            .singleElement()
                            .satisfies(method -> assertThat(method.type())
                                    .isEqualTo(AuthMethodType.EMAIL_CODE));
                    assertThat(context).hasSingleBean(EmailCodeSecondFactorMethod.class);
                });
    }
}
