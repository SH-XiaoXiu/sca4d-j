package com.sca4d.iam.totp;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.HmacHashFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 使用 googleauth 生成 TOTP 密钥并匹配当前时间前后一个 30 秒时间步。 */
@Component
public class TotpCodeVerifier {

    public static final int CODE_DIGITS = 6;
    public static final long STEP_MILLIS = 30_000L;
    private static final int WINDOW_STEPS = 1;
    private static final GoogleAuthenticator AUTHENTICATOR = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setCodeDigits(CODE_DIGITS)
                    .setTimeStepSizeInMillis(STEP_MILLIS)
                    .setWindowSize(WINDOW_STEPS * 2 + 1)
                    .setSecretBits(160)
                    .setNumberOfScratchCodes(0)
                    .setHmacHashFunction(HmacHashFunction.HmacSHA1)
                    .build());

    /** @return googleauth 使用安全随机数生成的 160 位 Base32 密钥 */
    public @NotNull String generateSecret() {
        return AUTHENTICATOR.createCredentials().getKey();
    }

    /**
     * 固定计算当前时间前后一个时间步，返回验证码实际命中的时间步。
     *
     * <p>无效格式和三个时间步均不匹配时返回 {@code null}。调用方只有在数据库原子推进成功后才能接受该验证码。
     */
    public @Nullable Long matchStep(
            @NotNull String secret, @NotNull String code, @NotNull Instant now) {
        boolean validFormat = code.matches("\\d{" + CODE_DIGITS + "}");
        int submitted = validFormat ? Integer.parseInt(code) : -1;
        long currentStep = now.toEpochMilli() / STEP_MILLIS;
        Long matched = null;
        for (long step = currentStep - WINDOW_STEPS; step <= currentStep + WINDOW_STEPS; step++) {
            int expected = AUTHENTICATOR.getTotpPassword(secret, step * STEP_MILLIS);
            if (validFormat && expected == submitted) {
                matched = step;
            }
        }
        return matched;
    }
}
