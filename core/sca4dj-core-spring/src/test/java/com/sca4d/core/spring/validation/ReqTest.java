package com.sca4d.core.spring.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReqTest {

    @Test
    @DisplayName("Keeps runtime validation separate from JetBrains nullability")
    void keepsRuntimeValidationSeparateFromJetBrainsNullability() {
        assertThat(Req.NotNull.class.isAnnotationPresent(NotNull.class)).isTrue();
        assertThat(Req.NotBlank.class.isAnnotationPresent(NotBlank.class)).isTrue();
        assertThat(Req.NotEmpty.class.isAnnotationPresent(NotEmpty.class)).isTrue();
    }
}
