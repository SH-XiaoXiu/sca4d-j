package com.sca4d.config;

import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.iam.passkey.YubicoCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** 按精确 RP ID 和 Origin 构造 Yubico WebAuthn Server。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
public class PasskeyConfig {

    /**
     * 注册拒绝 Origin 子域、端口放宽并校验 signature counter 的 RelyingParty。
     *
     * @return Passkey 注册和 assertion 共用的协议验证入口
     */
    @Bean
    @SuppressWarnings("deprecation")
    public @NotNull RelyingParty passkeyRelyingParty(
            @NotNull Sca4djAuthenticationProperties properties,
            @NotNull YubicoCredentialRepository credentialRepository) {
        Sca4djAuthenticationProperties.Passkey passkey = properties.getPasskey();
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(passkey.getRpId())
                        .name(passkey.getRpName())
                        .build())
                .credentialRepository(credentialRepository)
                .origins(Set.copyOf(passkey.getOrigins()))
                .allowOriginPort(false)
                .allowOriginSubdomain(false)
                .allowUntrustedAttestation(true)
                .validateSignatureCounter(true)
                .build();
    }
}
