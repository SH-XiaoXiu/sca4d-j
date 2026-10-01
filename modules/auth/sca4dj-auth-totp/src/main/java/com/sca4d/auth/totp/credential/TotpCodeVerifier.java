package com.sca4d.auth.totp.credential;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.HmacHashFunction;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/** 使用 Google Authenticator 兼容参数生成密钥并匹配 TOTP 时间步。 */
public class TotpCodeVerifier {

    /** TOTP 验证码位数。 */
    public static final int CODE_DIGITS = 6;

    /** TOTP 时间步毫秒数。 */
    public static final long STEP_MILLIS = 30_000L;

    private static final int WINDOW_STEPS = 1;

    private final GoogleAuthenticator authenticator;

    /** 创建 SHA-1、6 位、30 秒、前后一个时间步窗口的验证器。 */
    public TotpCodeVerifier() {
        this.authenticator = new GoogleAuthenticator(
                new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                        .setCodeDigits(CODE_DIGITS)
                        .setTimeStepSizeInMillis(STEP_MILLIS)
                        .setWindowSize(WINDOW_STEPS * 2 + 1)
                        .setSecretBits(160)
                        .setNumberOfScratchCodes(0)
                        .setHmacHashFunction(HmacHashFunction.HmacSHA1)
                        .build());
    }

    /**
     * 生成新的 TOTP 共享密钥。
     *
     * @return Google Authenticator 库使用安全随机数生成的 160-bit Base32 密钥
     */
    public @NotNull String generateSecret() {
        return authenticator.createCredentials().getKey();
    }

    /**
     * 在允许的时间窗口内查找验证码命中的时间步。
     *
     * @param secret Base32 TOTP 密钥
     * @param code 六位数字验证码
     * @param now 校验时间
     * @return 验证码命中的 Unix 时间步；格式无效或未命中时为空
     */
    public @NotNull OptionalLong matchStep(
            @NotNull String secret,
            @NotNull String code,
            @NotNull Instant now) {
        Objects.requireNonNull(secret, "TOTP 密钥不能为空");
        String checkedCode = Objects.requireNonNull(code, "TOTP 验证码不能为空");
        Instant checkedNow = Objects.requireNonNull(now, "TOTP 校验时间不能为空");
        if (!checkedCode.matches("\\d{" + CODE_DIGITS + "}")) {
            return OptionalLong.empty();
        }
        int submitted = Integer.parseInt(checkedCode);
        long currentStep = checkedNow.toEpochMilli() / STEP_MILLIS;
        OptionalLong matched = OptionalLong.empty();
        // Google Authenticator 库负责 HOTP 计算；这里显式遍历当前时间步前后各一步，以便返回命中步数。
        // 极低概率下同一验证码可能命中多个时间步，保留最新步可让数据库时间步下限单调前进得更远。
        for (long step = currentStep - WINDOW_STEPS; step <= currentStep + WINDOW_STEPS; step++) {
            if (authenticator.getTotpPassword(secret, step * STEP_MILLIS) == submitted) {
                matched = OptionalLong.of(step);
            }
        }
        return matched;
    }
}
