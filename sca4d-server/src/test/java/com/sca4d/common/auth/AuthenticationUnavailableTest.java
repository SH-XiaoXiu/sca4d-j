package com.sca4d.common.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证缺少认证实现时，安全状态变更不会被静默忽略。 */
class AuthenticationUnavailableTest {

    private final Authentication authentication = new AuthenticationUnavailable();

    @Test
    void shouldRejectEndingAllAccountSessions() {
        assertThatThrownBy(() -> authentication.endSessions(1L))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("error.authentication.implementation-unavailable");
    }

    @Test
    void shouldRejectEndingSingleAccountSession() {
        assertThatThrownBy(() -> authentication.endSession(1L, "session-1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("error.authentication.implementation-unavailable");
    }
}
