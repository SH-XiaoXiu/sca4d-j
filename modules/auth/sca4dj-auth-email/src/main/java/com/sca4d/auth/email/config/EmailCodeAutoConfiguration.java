package com.sca4d.auth.email.config;

import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.email.EmailCodeNotice;
import com.sca4d.auth.email.challenge.EmailCodeChallengeService;
import com.sca4d.auth.email.challenge.RedisEmailCodeChallengeStore;
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

/** 自动装配 Email Code 设置、Redis Challenge 和认证方法，不注册 HTTP 路由。 */
@AutoConfiguration
@EnableConfigurationProperties(EmailCodeProperties.class)
public class EmailCodeAutoConfiguration {

    /** 创建本应用上下文的模块设置，来源由 Spring 显式注入。 */
    @Bean
    EmailCodeSettings emailCodeSettings(SettingReader<String> reader, SettingWriter<String> writer) {
        return new EmailCodeSettings(reader, writer);
    }

    public EmailCodeAutoConfiguration() {
    }

    /** @return Email Code 使用的类型化通知定义 */
    @Bean
    NotificationType<EmailCodeNotice> emailCodeNotificationType() {
        return EmailCodeNotice.TYPE;
    }

    /** @return Challenge 有效期设置 */
    @Bean
    SystemSetting<Duration> emailCodeChallengeTtlSetting(EmailCodeSettings settings) {
        return settings.CHALLENGE_TTL;
    }

    /** @return 重发间隔设置 */
    @Bean
    SystemSetting<Duration> emailCodeResendIntervalSetting(EmailCodeSettings settings) {
        return settings.RESEND_INTERVAL;
    }

    /** @return 错误验证码次数上限设置 */
    @Bean
    SystemSetting<Integer> emailCodeMaximumAttemptsSetting(EmailCodeSettings settings) {
        return settings.MAXIMUM_ATTEMPTS;
    }

    /** @return 使用 App 隔离名称隔离 Hash、TTL、冷却和 Lua 消费的 Challenge Store */
    @Bean
    RedisEmailCodeChallengeStore redisEmailCodeChallengeStore(
            @Sca4djRedis @NotNull RedisTemplate<Key, String> redis,
            @NotNull Clock clock) {
        return new RedisEmailCodeChallengeStore(redis, clock);
    }

    /** @return 签发匿名一致响应并可靠登记 Email 通知的 Challenge 服务 */
    @Bean
    EmailCodeChallengeService emailCodeChallengeService(
            @NotNull AccountDirectory accounts,
            @NotNull Notifications notifications,
            @NotNull RedisEmailCodeChallengeStore store,
            @NotNull SecretHasher hasher,
            @NotNull EmailCodeSettings settings,
            @NotNull Clock clock,
            @NotNull EmailCodeProperties properties) {
        return new EmailCodeChallengeService(
                accounts,
                new RealmKey(properties.getAccountRealm()),
                notifications,
                store,
                hasher,
                settings,
                clock);
    }

    /** @return 可由认证引擎发现并同时支持首因素和附加因素的 Email Code 方法 */
    @Bean
    EmailCodeAuthenticationMethod emailCodeAuthenticationMethod(
            @NotNull EmailCodeChallengeService challenges,
            @NotNull Clock clock) {
        return new EmailCodeAuthenticationMethod(challenges, clock);
    }
}
