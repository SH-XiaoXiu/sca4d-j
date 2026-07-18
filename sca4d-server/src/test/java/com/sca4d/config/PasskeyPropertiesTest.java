package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证 Passkey 启用时必须使用精确 RP ID、Origin 和分离的 Redis key。 */
class PasskeyPropertiesTest {

    @Test
    void shouldAcceptExactLocalOriginForLocalDevelopment() {
        Sca4djAuthenticationProperties.Passkey passkey = new Sca4djAuthenticationProperties.Passkey();
        passkey.setEnabled(true);
        passkey.setRpId("localhost");
        passkey.setOrigins(Set.of("http://localhost:5173"));

        assertThatCode(passkey::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInsecureRemoteOriginAndMismatchedRpId() {
        Sca4djAuthenticationProperties.Passkey passkey = new Sca4djAuthenticationProperties.Passkey();
        passkey.setEnabled(true);
        passkey.setRpId("example.com");
        passkey.setOrigins(Set.of("http://login.example.com"));

        assertThatThrownBy(passkey::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");

        passkey.setOrigins(Set.of("https://other.example.net"));
        assertThatThrownBy(passkey::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RP ID");
    }
}
