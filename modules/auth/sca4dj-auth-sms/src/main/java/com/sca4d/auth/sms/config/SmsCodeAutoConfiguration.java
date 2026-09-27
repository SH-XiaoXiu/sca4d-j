package com.sca4d.auth.sms.config;

import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.auth.sms.SmsCodeNotice;
import com.sca4d.auth.sms.challenge.RedisSmsCodeChallengeStore;
import com.sca4d.auth.sms.challenge.SmsCodeChallengeService;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.security.SecretHasher;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.definition.SystemSetting;
import com.sca4d.core.spring.redis.Sca4djRedis;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Duration;

/** 自动装配 SMS Code 设置、Redis Challenge 和通用认证方法。 */
@AutoConfiguration
@EnableConfigurationProperties(SmsCodeProperties.class)
public class SmsCodeAutoConfiguration {

    /** 创建本应用上下文的模块设置，来源由 Spring 显式注入。 */
    @Bean
    SmsCodeSettings smsCodeSettings(SettingReader<String> reader, SettingWriter<String> writer) {
        return new SmsCodeSettings(reader, writer);
    }

    @Bean
    NotificationType<SmsCodeNotice> smsCodeNotificationType() {
        return SmsCodeNotice.TYPE;
    }

    @Bean
    SystemSetting<Duration> smsCodeChallengeTtlSetting(SmsCodeSettings settings) {
        return settings.CHALLENGE_TTL;
    }

    @Bean
    SystemSetting<Duration> smsCodeResendIntervalSetting(SmsCodeSettings settings) {
        return settings.RESEND_INTERVAL;
    }

    @Bean
    SystemSetting<Integer> smsCodeMaximumAttemptsSetting(SmsCodeSettings settings) {
        return settings.MAXIMUM_ATTEMPTS;
    }

    @Bean
    RedisSmsCodeChallengeStore redisSmsCodeChallengeStore(
            @Sca4djRedis @NotNull RedisTemplate<Key, String> redis,
            @NotNull Clock clock) {
        return new RedisSmsCodeChallengeStore(redis, clock);
    }

    @Bean
    SmsCodeChallengeService smsCodeChallengeService(
            @NotNull AccountDirectory accounts,
            @NotNull Notifications notifications,
            @NotNull RedisSmsCodeChallengeStore store,
            @NotNull SecretHasher hasher,
            @NotNull SmsCodeSettings settings,
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
