package com.sca4d.core.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.security.SecretHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证默认摘要实现生成可自描述且不可直接还原的 Argon2id 摘要。 */
class Argon2SecretHasherTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecretHasherAutoConfiguration.class));

    @Test
    @DisplayName("Hashes And Verifies Secret Without Exposing Plain Text")
    void hashesAndVerifiesSecretWithoutExposingPlainText() {
        SecretHasher hasher = new Argon2SecretHasher();

        String secretHash = hasher.hash("correct-secret");

        assertThat(secretHash).isNotEqualTo("correct-secret");
        assertThat(hasher.matches("correct-secret", secretHash)).isTrue();
        assertThat(hasher.matches("wrong-secret", secretHash)).isFalse();
    }

    @Test
    @DisplayName("Registers Default Hasher And Keeps Application Replacement")
    void registersDefaultHasherAndKeepsApplicationReplacement() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SecretHasher.class);
            assertThat(context.getBean(SecretHasher.class)).isInstanceOf(Argon2SecretHasher.class);
        });

        SecretHasher replacement = new SecretHasher() {
            @Override
            public String hash(String secret) {
                return secret;
            }

            @Override
            public boolean matches(String secret, String secretHash) {
                return secret.equals(secretHash);
            }
        };
        contextRunner
                .withBean(SecretHasher.class, () -> replacement)
                .run(context -> assertThat(context.getBean(SecretHasher.class)).isSameAs(replacement));
    }
}
