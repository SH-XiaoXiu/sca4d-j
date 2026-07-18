package com.sca4d.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

/** 验证 Principal 的类型、租户归属和选择状态。 */
class PrincipalTest {

    @Test
    void shouldRequireSelectedPrincipalType() {
        Principal principal = new Principal();

        assertThatThrownBy(principal::requirePrincipalType)
                .isInstanceOfSatisfying(MissingAuthException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.AUTHENTICATION_CONTEXT_INCOMPLETE);
                    assertThat(exception.getMessageKey()).isEqualTo("error.authentication.context-incomplete");
                });

        principal.setPrincipalType(PrincipalType.TENANT);
        assertThat(principal.requirePrincipalType()).isEqualTo(PrincipalType.TENANT);
    }
}
