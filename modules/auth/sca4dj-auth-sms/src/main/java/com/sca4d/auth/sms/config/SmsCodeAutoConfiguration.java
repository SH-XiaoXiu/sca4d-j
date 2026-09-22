package com.sca4d.auth.sms.config;

import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.auth.sms.SmsCodeNotice;
import com.sca4d.auth.sms.challenge.RedisSmsCodeChallengeStore;
import com.sca4d.auth.sms.challenge.SmsCodeChallengeService;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.security.SecretHasher;
import com.sca4d.core.api.setting.Settings;
import com.sca4d.core.api.setting.SystemSetting;
import com.sca4d.core.spring.redis.Sca4djRedis;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

/** 自动装配 SMS Code 设置、Redis Challenge 和通用认证方法。 */
@AutoConfiguration
@EnableConfigurationProperties(SmsCodeProperties.class)
public class SmsCodeAutoConfiguration {
    @Bean
    NotificationType<SmsCodeNotice> smsCodeNotificationType() {
        return SmsCodeNotice.TYPE;
    }

    @Bean
    SystemSetting<Duration> smsCodeChallengeTtlSetting() {
        return SmsCodeSettings.CHALLENGE_TTL;
    }

    @Bean
    SystemSetting<Duration> smsCodeResendIntervalSetting() {
        return SmsCodeSettings.RESEND_INTERVAL;
    }

    @Bean
    SystemSetting<Integer> smsCodeMaximumAttemptsSetting() {
        return SmsCodeSettings.MAXIMUM_ATTEMPTS;
    }

    @Bean
    RedisSmsCodeChallengeStore redisSmsCodeChallengeStore(
            @Sca4djRedis @NotNull StringRedisTemplate redis,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace namespace) {
        return new RedisSmsCodeChallengeStore(redis, clock, namespace);
    }

    @Bean
    SmsCodeChallengeService smsCodeChallengeService(
            @NotNull AccountDirectory accounts,
            @NotNull Notifications notifications,
            @NotNull RedisSmsCodeChallengeStore store,
            @NotNull SecretHasher hasher,
            @NotNull Settings settings,
            @NotNull Clock clock,
            @NotNull SmsCodeProperties properties) {
        return new SmsCodeChallengeService(
                accounts,
                new RealmKey(properties.getAccountRealm()),
                notifications,
                store,
                hasher,
                settings,
                clock);
    }

    @Bean
    SmsCodeAuthenticationMethod smsCodeAuthenticationMethod(
            @NotNull SmsCodeChallengeService challenges,
            @NotNull Clock clock) {
        return new SmsCodeAuthenticationMethod(challenges, clock);
    }
}
