package com.sca4d.auth.apple;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.federation.FederatedClientAuthentication;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.auth.federation.FederatedProviderProtocol;

import java.util.List;
import java.util.Map;

/** 提供 Apple 测试使用的固定 P-256 密钥对和 Provider 配置；密钥对只用于测试，不对应任何 Apple 账号。 */
final class AppleTestMaterial {

    static final String CLIENT_ID = "com.example.web";

    static final String ISSUER = "https://appleid.apple.com";

    static final String KEY_ID = "KEY1234567";

    static final String PRIVATE_KEY_BODY = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgcp9dKRoil8a1cVcL"
            + "dTySGKBMaviHeuyoalsbnNMptYuhRANCAAS7W0SVhKE/ggyHac2jcNEOdYSzHhXg"
            + "PPqkrthwX0KrU6Gdwymo1msf9qkOQV6U/l8Dr6oKcDUGOeM6YIZFdRes";

    static final String PUBLIC_KEY_BODY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEu1tElYShP4IMh2nNo3DRDnWEsx4V"
            + "4Dz6pK7YcF9Cq1OhncMpqNZrH/apDkFelP5fA6+qCnA1BjnjOmCGRXUXrA==";

    static final String TEAM_ID = "TEAM123456";

    static final String PRIVATE_KEY_PEM = "-----BEGIN PRIVATE KEY-----\n"
            + PRIVATE_KEY_BODY.substring(0, 64) + "\n"
            + PRIVATE_KEY_BODY.substring(64, 128) + "\n"
            + PRIVATE_KEY_BODY.substring(128) + "\n"
            + "-----END PRIVATE KEY-----\n";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AppleTestMaterial() {
    }

    /** 创建 Provider 配置中保存的签名材料 JSON。 */
    static String material(String teamId, String keyId, String privateKey) {
        try {
            return MAPPER.writeValueAsString(Map.of("teamId", teamId, "keyId", keyId, "privateKey", privateKey));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 创建使用 {@code CLIENT_SECRET_SIGNED_JWT} 的 Apple Provider 协议配置。 */
    static FederatedProviderConfiguration configuration(String clientSecret) {
        return new FederatedProviderConfiguration(
                FederatedProviderProtocol.OIDC,
                ISSUER,
                CLIENT_ID,
                clientSecret,
                null,
                null,
                null,
                null,
                FederatedClientAuthentication.CLIENT_SECRET_SIGNED_JWT,
                List.of("openid"),
                Map.of(),
                Map.of(),
                AppleProviderDriver.CODE);
    }
}
