package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.security.SecretKeyring;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证外部配置能够在 Spring 启动期建立 keyring。 */
class SecretKeyringConfigTest {

    @Test
    void shouldBindActiveKeyAndNamedKeys() {
        new ApplicationContextRunner()
                .withUserConfiguration(SecretKeyringConfig.class)
                .withPropertyValues(
                        "sca4dj.secret-keyring.active-key-id=primary",
                        "sca4dj.secret-keyring.keys.primary=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
                .run(context -> {
                    assertThat(context).hasSingleBean(SecretKeyring.class);
                    SecretKeyring keyring = context.getBean(SecretKeyring.class);
                    assertThat(keyring.decrypt(
                            "smtp.sender.123.password",
                            keyring.encrypt("smtp.sender.123.password", "secret-password")))
                            .isEqualTo("secret-password");
                });
    }

    @Test
    void shouldRejectBootWhenActiveKeyIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(SecretKeyringConfig.class)
                .withPropertyValues(
                        "sca4dj.secret-keyring.active-key-id=missing",
                        "sca4dj.secret-keyring.keys.old=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
                .run(context -> assertThat(context).hasFailed());
    }
}
