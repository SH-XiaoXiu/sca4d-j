package com.sca4d.auth.config;

import com.sca4d.core.api.authentication.AuthTokenMode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 配置默认 Token 模式、服务端 Session 和短效 JWT 投影。 */
@ConfigurationProperties("sca4dj.auth")
public class AuthProperties {

    private final Token token = new Token();

    private List<String> excludedPaths = List.of();

    private final Jwt jwt = new Jwt();

    /** 创建使用 SESSION 模式的部署配置对象。 */
    public AuthProperties() {
    }

    /**
     * 读取普通 HTTP 请求默认使用的 Token 模式。
     *
     * @return 默认 SESSION 模式
     */
    public AuthTokenMode getTokenMode() {
        return token.mode;
    }

    /**
     * 设置普通 HTTP 请求默认使用的 Token 模式。
     *
     * @param tokenMode SESSION 或 JWT
     */
    public void setTokenMode(AuthTokenMode tokenMode) {
        token.setMode(tokenMode);
    }

    /** @return Token 资源配置 */
    public Token getToken() {
        return token;
    }

    /**
     * 读取不进入认证拦截器的 Spring MVC 路径模式。
     *
     * @return 不可修改的路径模式列表，默认没有排除项
     */
    public @NotNull @Unmodifiable List<@NotNull String> getExcludedPaths() {
        return excludedPaths;
    }

    /**
     * 设置不进入认证拦截器的 Spring MVC 路径模式。
     *
     * @param excludedPaths 由具体应用负责限制暴露环境的非空路径模式
     * @throws IllegalArgumentException 列表包含空白模式时抛出
     */
    public void setExcludedPaths(@NotNull List<@NotNull String> excludedPaths) {
        if (excludedPaths.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("认证排除路径不能为空");
        }
        this.excludedPaths = List.copyOf(excludedPaths);
    }

    /**
     * 读取 JWT 签发和验证配置。
     *
     * @return 可由 Spring Boot 绑定的 JWT 配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring Boot must bind nested properties through this live final configuration object")
    public Jwt getJwt() {
        return jwt;
    }

    /** 配置普通请求使用的认证 Token 模式。 */
    public static class Token {

        private AuthTokenMode mode = AuthTokenMode.SESSION;

        public AuthTokenMode getMode() {
            return mode;
        }

        public void setMode(AuthTokenMode mode) {
            if (mode == null) {
                throw new IllegalArgumentException("Token 模式不能为空");
            }
            this.mode = mode;
        }
    }

    /** 配置 HS256 JWT 的密钥轮换、受众、有效期和长度限制。 */
    public static class Jwt {

        private String activeKeyId;

        private final Map<String, String> keys = new LinkedHashMap<>();

        private String issuer;

        private String audience;

        private int maxTokenLength = 6144;

        /** 创建使用五分钟 access Token 和 6144 字符上限的 JWT 配置。 */
        public Jwt() {
        }

        /** @return 当前签发 JWT 使用的密钥标识 */
        public String getActiveKeyId() {
            return activeKeyId;
        }

        /** @param activeKeyId 当前签发 JWT 使用且必须存在于 keys 的密钥标识 */
        public void setActiveKeyId(String activeKeyId) {
            this.activeKeyId = activeKeyId;
        }

        /**
         * 读取验证密钥表。
         *
        * @return key id 到 Base64 编码 HS256 密钥的可绑定 Map
        */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring Boot must bind indexed keys through this live final map")
        public Map<String, String> getKeys() {
            return keys;
        }

        /** @return JWT issuer 声明 */
        public String getIssuer() {
            return issuer;
        }

        /** @param issuer 签发和验证使用的非空 issuer */
        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        /** @return JWT audience 声明 */
        public String getAudience() {
            return audience;
        }

        /** @param audience 签发和验证使用的非空 audience */
        public void setAudience(String audience) {
            this.audience = audience;
        }

        /** @return 接受和签发的 JWT 最大字符数，默认为 6144 */
        public int getMaxTokenLength() {
            return maxTokenLength;
        }

        /**
         * 设置接受和签发的 JWT 最大字符数。
         *
         * @param maxTokenLength 必须大于零的字符数
         */
        public void setMaxTokenLength(int maxTokenLength) {
            if (maxTokenLength <= 0) {
                throw new IllegalArgumentException("JWT 最大长度必须大于零");
            }
            this.maxTokenLength = maxTokenLength;
        }
    }
}
