package com.sca4d.auth.passkey.config;

import com.sca4d.auth.passkey.PasskeyAuthenticationMethod;
import com.sca4d.auth.passkey.ceremony.PasskeyAssertionService;
import com.sca4d.auth.passkey.ceremony.PasskeyCeremonyStore;
import com.sca4d.auth.passkey.credential.PasskeyCredentialRepository;
import com.sca4d.auth.passkey.credential.YubicoCredentialRepository;
import com.sca4d.auth.passkey.credential.mapper.PasskeyCredentialMapper;
import com.sca4d.auth.passkey.registration.PasskeyRegistrationService;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.definition.SystemSetting;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.redis.Sca4djRedis;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

/**
 * 自动装配 Passkey Credential、Redis ceremony 和 Yubico WebAuthn 校验。
 */
@AutoConfiguration
@MapperScan(basePackageClasses = PasskeyCredentialMapper.class)
@EnableConfigurationProperties(PasskeyProperties.class)
public class PasskeyAutoConfiguration {

    /** 创建本应用上下文的模块设置，来源由 Spring 显式注入。 */
    @Bean
    PasskeySettings passkeySettings(SettingReader<String> reader, SettingWriter<String> writer) {
        return new PasskeySettings(reader, writer);
    }

    /**
     * 创建 Passkey 自动配置。
     */
    public PasskeyAutoConfiguration() {
    }

    /** @return Passkey ceremony 有效期设置 */
    @Bean
    SystemSetting<Duration> passkeyCeremonyTtlSetting(PasskeySettings settings) {
        return settings.CEREMONY_TTL;
    }

    /** @return 每个账号的 Passkey Credential 数量上限设置 */
    @Bean
    SystemSetting<Integer> passkeyMaximumCredentialsSetting(PasskeySettings settings) {
        return settings.MAXIMUM_CREDENTIALS;
    }

    /**
     * @return Passkey 模块 Credential Repository
     */
    @Bean
    PasskeyCredentialRepository passkeyCredentialRepository(PasskeyCredentialMapper mapper) {
        return new PasskeyCredentialRepository(mapper);
    }

    /**
     * @return Yubico 校验器使用的 CredentialRepository 适配器
     */
    @Bean
    YubicoCredentialRepository yubicoCredentialRepository(PasskeyCredentialRepository credentials) {
        return new YubicoCredentialRepository(credentials);
    }

    /**
     * @return 固定 attestation none、精确 Origin 和签名计数器校验的 RelyingParty
     */
    @Bean
    @ConditionalOnMissingBean
    RelyingParty passkeyRelyingParty(
            YubicoCredentialRepository credentials,
            PasskeyProperties properties,
            Clock clock) {
        return RelyingParty.builder()
                .identity(
                        RelyingPartyIdentity.builder()
                                .id(properties.getRelyingPartyId())
                                .name(properties.getRelyingPartyName())
                                .build())
                .credentialRepository(credentials)
                .origins(properties.getOrigins())
                .attestationConveyancePreference(AttestationConveyancePreference.NONE)
                .allowUntrustedAttestation(true)
                .validateSignatureCounter(true)
                .clock(clock)
                .build();
    }

    /**
     * @return 使用 Redis GETDEL 原子消费的 WebAuthn ceremony 存储
     */
    @Bean
    PasskeyCeremonyStore passkeyCeremonyStore(
            @Sca4djRedis RedisTemplate<Key, String> redis,
            @Sca4djJson Sca4djMapper objectMapper,
            Clock clock,
            PasskeySettings settings) {
        return new PasskeyCeremonyStore(
                redis,
                objectMapper,
                clock,
                settings);
    }

    /**
     * @return discoverable Passkey assertion 服务
     */
    @Bean
    PasskeyAssertionService passkeyAssertionService(
            RelyingParty relyingParty,
            PasskeyCeremonyStore ceremonies,
            PasskeyCredentialRepository credentials,
            Clock clock) {
        return new PasskeyAssertionService(relyingParty, ceremonies, credentials, clock);
    }

    /**
     * @return 强制 resident key 与 UV 的 Passkey registration 服务
     */
    @Bean
    PasskeyRegistrationService passkeyRegistrationService(
            RelyingParty relyingParty,
            PasskeyCeremonyStore ceremonies,
            PasskeyCredentialRepository credentials,
            PasskeySettings settings,
            Clock clock) {
        return new PasskeyRegistrationService(
                relyingParty,
                ceremonies,
                credentials,
                settings,
                clock,
                new SecureRandom());
    }

    /**
     * @return 只贡献 POSSESSION 因素的 Passkey 认证方法
     */
    @Bean
    PasskeyAuthenticationMethod passkeyAuthenticationMethod(
            PasskeyAssertionService assertions,
            PasskeyCredentialRepository credentials,
            AccountDirectory accounts,
            Clock clock) {
        return new PasskeyAuthenticationMethod(assertions, credentials, accounts, clock);
    }
}
