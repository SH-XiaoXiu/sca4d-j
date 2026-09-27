package com.sca4d.auth.totp.config;

import com.sca4d.auth.totp.TotpAuthenticationMethod;
import com.sca4d.auth.totp.credential.TotpCodeVerifier;
import com.sca4d.auth.totp.credential.TotpCredentialRepository;
import com.sca4d.auth.totp.credential.mapper.TotpCredentialMapper;
import com.sca4d.auth.totp.enrollment.RedisTotpEnrollmentStore;
import com.sca4d.auth.totp.enrollment.TotpEnrollmentService;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.definition.SystemSetting;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.redis.Sca4djRedis;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Duration;

/** 自动装配 TOTP 方法、SQL 凭据和 Redis enrollment，不注册 HTTP 或二维码实现。 */
@AutoConfiguration
@MapperScan(basePackageClasses = TotpCredentialMapper.class)
public class TotpAutoConfiguration {

    /** 创建本应用上下文的模块设置，来源由 Spring 显式注入。 */
    @Bean
    TotpSettings totpSettings(SettingReader<String> reader, SettingWriter<String> writer) {
        return new TotpSettings(reader, writer);
    }

    /** 创建 TOTP 自动配置。 */
    public TotpAutoConfiguration() {
    }

    /** @return TOTP enrollment 有效期设置 */
    @Bean
    SystemSetting<Duration> totpEnrollmentTtlSetting(TotpSettings settings) {
        return settings.ENROLLMENT_TTL;
    }

    /** @return Google Authenticator 兼容验证码实现 */
    @Bean
    TotpCodeVerifier totpCodeVerifier() {
        return new TotpCodeVerifier();
    }

    /** @return TOTP SQL 凭据 Repository */
    @Bean
    TotpCredentialRepository totpCredentialRepository(TotpCredentialMapper mapper) {
        return new TotpCredentialRepository(mapper);
    }

    /** @return Redis enrollment store */
    @Bean
    RedisTotpEnrollmentStore redisTotpEnrollmentStore(
            @Sca4djRedis RedisTemplate<Key, String> redis,
            @Sca4djJson Sca4djMapper objectMapper,
            Clock clock) {
        return new RedisTotpEnrollmentStore(redis, objectMapper, clock);
    }

    /** @return TOTP enrollment 与凭据生命周期服务 */
    @Bean
    TotpEnrollmentService totpEnrollmentService(
            RedisTotpEnrollmentStore enrollments,
            TotpCredentialRepository credentials,
            TotpCodeVerifier verifier,
            SecretKeyring keyring,
            SessionControl sessions,
            TotpSettings settings,
            Clock clock) {
        return new TotpEnrollmentService(
                enrollments,
                credentials,
                verifier,
                keyring,
                sessions,
                settings,
                clock);
    }

    /** @return 可由认证引擎自动发现的 TOTP 方法 */
    @Bean
    TotpAuthenticationMethod totpAuthenticationMethod(
            TotpCredentialRepository credentials,
            RedisTotpEnrollmentStore enrollments,
            TotpCodeVerifier verifier,
            SecretKeyring keyring,
            Clock clock) {
        return new TotpAuthenticationMethod(credentials, enrollments, verifier, keyring, clock);
    }
}
