package com.sca4d.openapi.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 配置 OpenAPI 独立 JWT 的签发开关、密钥、受众和有效期。 */
@ConfigurationProperties("sca4dj.openapi")
public class OpenApiProperties {

    private final Jwt jwt = new Jwt();

    /** 创建默认关闭的 OpenAPI JWT 配置；关闭时 Token 端点复用 Session access Token。 */
    public OpenApiProperties() {
    }

    /** @return OpenAPI JWT 配置 */
    public @NotNull Jwt getJwt() {
        return jwt;
    }

    /** 配置只给 OpenAPI 请求使用的 HS256 JWT。 */
    public static class Jwt {

        private boolean enabled;

        private String activeKeyId;

        private final Map<String, String> keys = new LinkedHashMap<>();

        private String issuer;

        private String audience;

        private Duration accessTtl = Duration.ofMinutes(5);

        private int maxTokenLength = 6144;

        /** @return 是否签发和接受独立 OpenAPI JWT */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用独立 OpenAPI JWT */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return 当前签发密钥标识 */
        public String getActiveKeyId() {
            return activeKeyId;
        }

        /** @param activeKeyId 当前签发密钥标识 */
        public void setActiveKeyId(String activeKeyId) {
            this.activeKeyId = activeKeyId;
        }

        /** @return key id 到 Base64 HS256 密钥的映射 */
        public @NotNull Map<String, String> getKeys() {
            return keys;
        }

        /** @return JWT issuer */
        public String getIssuer() {
            return issuer;
        }

        /** @param issuer JWT issuer */
        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        /** @return JWT audience */
        public String getAudience() {
            return audience;
        }

        /** @param audience JWT audience */
        public void setAudience(String audience) {
            this.audience = audience;
        }

        /** @return OpenAPI access JWT 有效期 */
        public @NotNull Duration getAccessTtl() {
            return accessTtl;
        }

        /** @param accessTtl 必须大于零的 OpenAPI access JWT 有效期 */
        public void setAccessTtl(@NotNull Duration accessTtl) {
            if (accessTtl.isZero() || accessTtl.isNegative()) {
                throw new IllegalArgumentException("OpenAPI JWT 有效期必须大于零");
            }
            this.accessTtl = accessTtl;
        }

        /** @return JWT 最大字符数 */
        public int getMaxTokenLength() {
            return maxTokenLength;
        }

        /** @param maxTokenLength 必须大于零的 JWT 最大字符数 */
        public void setMaxTokenLength(int maxTokenLength) {
            if (maxTokenLength <= 0) {
                throw new IllegalArgumentException("OpenAPI JWT 最大长度必须大于零");
            }
            this.maxTokenLength = maxTokenLength;
        }
    }
}
