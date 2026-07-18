package com.sca4d.iam.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.iam.totp.TotpCodeVerifier;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证 TOTP 只接受当前时间前后一个 30 秒时间步。 */
class TotpCodeVerifierTest {

    @Test
    void shouldMatchOnlyConfiguredTimeWindow() {
        TotpCodeVerifier verifier = new TotpCodeVerifier();
        String secret = verifier.generateSecret();
        Instant now = Instant.parse("2026-07-17T02:00:15Z");
        GoogleAuthenticator authenticator = new GoogleAuthenticator(
                new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                        .setCodeDigits(6)
                        .setTimeStepSizeInMillis(TotpCodeVerifier.STEP_MILLIS)
                        .setWindowSize(3)
                        .setSecretBits(160)
                        .setNumberOfScratchCodes(0)
                        .build());
        long currentStep = now.toEpochMilli() / TotpCodeVerifier.STEP_MILLIS;
        String previous = String.format(
                "%06d", authenticator.getTotpPassword(secret, (currentStep - 1) * TotpCodeVerifier.STEP_MILLIS));
        String expired = String.format(
                "%06d", authenticator.getTotpPassword(secret, (currentStep - 2) * TotpCodeVerifier.STEP_MILLIS));

        assertThat(verifier.matchStep(secret, previous, now)).isEqualTo(currentStep - 1);
        assertThat(verifier.matchStep(secret, expired, now)).isNull();
        assertThat(verifier.matchStep(secret, "12x456", now)).isNull();
    }
}
