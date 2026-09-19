package com.sca4d.auth.totp.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.HmacHashFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证 Google Authenticator 参数和前后一个时间步窗口。 */
class TotpCodeVerifierTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    private final TotpCodeVerifier verifier = new TotpCodeVerifier();

    @Test
    @DisplayName("生成 Google Authenticator 兼容秘密并匹配三个时间步窗口")
    void generatesGoogleAuthenticatorCompatibleSecretAndMatchesThreeStepWindow() {
        String generated = verifier.generateSecret();
        Instant now = Instant.ofEpochSecond(1_700_000_010L);
        long currentStep = now.toEpochMilli() / TotpCodeVerifier.STEP_MILLIS;

        assertThat(generated).matches("[A-Z2-7]{32}");
        assertThat(verifier.matchStep(SECRET, code(currentStep - 1), now)).hasValue(currentStep - 1);
        assertThat(verifier.matchStep(SECRET, code(currentStep), now)).hasValue(currentStep);
        assertThat(verifier.matchStep(SECRET, code(currentStep + 1), now)).hasValue(currentStep + 1);
        assertThat(verifier.matchStep(SECRET, code(currentStep + 2), now)).isEmpty();
    }

    @Test
    @DisplayName("拒绝不是六位数字的验证码")
    void rejectsCodesThatAreNotExactlySixDigits() {
        Instant now = Instant.ofEpochSecond(1_700_000_010L);

        assertThat(verifier.matchStep(SECRET, "12345", now)).isEmpty();
        assertThat(verifier.matchStep(SECRET, "1234567", now)).isEmpty();
        assertThat(verifier.matchStep(SECRET, "12A456", now)).isEmpty();
    }

    private static String code(long step) {
        GoogleAuthenticator authenticator = new GoogleAuthenticator(
                new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                        .setCodeDigits(TotpCodeVerifier.CODE_DIGITS)
                        .setTimeStepSizeInMillis(TotpCodeVerifier.STEP_MILLIS)
                        .setWindowSize(3)
                        .setSecretBits(160)
                        .setNumberOfScratchCodes(0)
                        .setHmacHashFunction(HmacHashFunction.HmacSHA1)
                        .build());
        return String.format("%06d", authenticator.getTotpPassword(SECRET, step * TotpCodeVerifier.STEP_MILLIS));
    }
}
