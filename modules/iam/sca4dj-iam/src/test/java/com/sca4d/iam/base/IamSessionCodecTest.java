package com.sca4d.iam.base;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IamSessionCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Round trips Platform Principal identifier")
    void roundTripsPlatformPrincipalIdentifier() {
        PlatformPrincipal principal = new PlatformPrincipal(33);
        PlatformPrincipal restoredPrincipal = roundTrip(new PlatformPrincipalCodec(objectMapper), principal);

        assertThat(restoredPrincipal.principalId()).isEqualTo(principal.principalId());
    }

    private static PlatformPrincipal roundTrip(PlatformPrincipalCodec codec, PlatformPrincipal value) {
        return codec.decode(codec.encode(value));
    }
}
