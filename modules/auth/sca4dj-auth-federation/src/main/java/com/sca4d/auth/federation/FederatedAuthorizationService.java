package com.sca4d.auth.federation;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import com.sca4d.core.api.error.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/** 执行 OIDC/OAuth2 授权码、PKCE、Token 交换和用户资料标准化。 */
public final class FederatedAuthorizationService {

    private static final Logger LOG = LoggerFactory.getLogger(FederatedAuthorizationService.class);

    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(5);

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final FederatedProviderService providers;

    private final FederatedAuthorizationStore store;

    private final ObjectMapper mapper;

    private final RestClient restClient;

    private final Clock clock;

    private final SecureRandom random = new SecureRandom();

    /** 创建授权服务。 */
    public FederatedAuthorizationService(
            FederatedProviderService providers,
            FederatedAuthorizationStore store,
            ObjectMapper mapper,
            RestClient restClient,
            Clock clock) {
        this.providers = Objects.requireNonNull(providers, "Provider 服务不能为空");
        this.store = Objects.requireNonNull(store, "授权状态存储不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.restClient = Objects.requireNonNull(restClient, "RestClient 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
    }

    /** 创建授权地址；redirectUri 必须是应用固定的 HTTPS callback。 */
    public @NotNull String start(
            @NotNull String providerCode,
            @NotNull FederatedAuthorizationPurpose purpose,
            @NotNull String browserBinding,
            @NotNull URI redirectUri) {
        FederatedAuthorizationPurpose checkedPurpose = Objects.requireNonNull(purpose, "授权用途不能为空");
        FederatedProvider provider = providers.activeByCode(providerCode);
        FederatedProviderConfiguration configured = authorizationConfiguration(provider);
        Discovery discovery = discover(configured);
        String state = randomToken();
        String nonce = randomToken();
        String verifier = randomToken();
        store.save(
                new FederatedAuthorization(
                        state,
                        provider.code(),
                        checkedPurpose,
                        browserBinding,
                        nonce,
                        verifier,
                        redirectUri.toString(),
                        clock.instant().plus(AUTHORIZATION_TTL)),
                AUTHORIZATION_TTL);
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(DigestUtil.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(discovery.authorizationEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", configured.clientId())
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("scope", String.join(" ", configured.scopes()))
                .queryParam("state", state)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256");
        if (provider.protocol() == FederatedProviderProtocol.OIDC)
            builder.queryParam("nonce", nonce);
        configured.authorizationParameters().forEach(builder::queryParam);
        LOG.debug(
                "联邦授权地址已创建 | providerCode={} purpose={} ttlSeconds={}",
                provider.code(),
                checkedPurpose,
                AUTHORIZATION_TTL.toSeconds());
        return builder.build(true).toUriString();
    }

    /** 处理 Provider callback 并保存不包含 Token 的完成资料。 */
    public @NotNull String callback(
            @NotNull String state,
            @Nullable String code,
            @Nullable String error,
            @NotNull String browserBinding,
            @NotNull URI redirectUri) {
        long startedAt = System.nanoTime();
        FederatedAuthorization authorization = store.consume(state, browserBinding);
        if (authorization == null) {
            throw new BusinessException(FederatedErrorCode.AUTHORIZATION_UNAVAILABLE);
        }
        if (error != null || code == null || code.isBlank())
            throw new BusinessException(FederatedErrorCode.AUTHORIZATION_REJECTED);
        if (!authorization.redirectUri().equals(redirectUri.toString())) {
            throw new BusinessException(FederatedErrorCode.AUTHORIZATION_REJECTED);
        }
        FederatedProvider provider = providers.activeByCode(authorization.providerCode());
        FederatedProviderConfiguration configured = authorizationConfiguration(provider);
        Discovery discovery = discover(configured);
        JsonNode token = exchangeCode(
                discovery.tokenEndpoint,
                configured,
                code,
                authorization.codeVerifier(),
                redirectUri);
        FederatedProfile profile = provider.protocol() == FederatedProviderProtocol.OIDC
                ? oidcProfile(configured, discovery, token, authorization.nonce())
                : oauth2Profile(configured, token);
        String completionId = randomToken();
        store.saveCompletion(
                completionId,
                provider.code(),
                authorization.purpose(),
                profile,
                browserBinding,
                AUTHORIZATION_TTL);
        LOG.debug(
                "联邦授权回调已验证 | providerCode={} purpose={} elapsedMillis={}",
                provider.code(),
                authorization.purpose(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return completionId;
    }

    /** 读取已启用 Provider 的协议配置；没有配置时返回统一不可用错误。 */
    private FederatedProviderConfiguration authorizationConfiguration(FederatedProvider provider) {
        FederatedProviderConfiguration config = providers.authorizationConfiguration(provider);
        if (config == null)
            throw new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE);
        return config;
    }

    /** 读取 OIDC Discovery，或使用 OAuth2 配置中已保存的端点。 */
    private Discovery discover(FederatedProviderConfiguration config) {
        if (config.protocol() == FederatedProviderProtocol.OAUTH2) {
            LOG.trace("使用已保存端点执行 OAuth2 授权 | protocol={}", config.protocol());
            return new Discovery(
                    config.authorizationEndpoint(),
                    config.tokenEndpoint(),
                    config.userInfoEndpoint(),
                    config.jwksEndpoint());
        }
        String issuer = config.issuer();
        try {
            LOG.trace("读取 OIDC Discovery 文档 | protocol={}", config.protocol());
            String body = get(
                    issuer.endsWith("/")
                            ? issuer + ".well-known/openid-configuration"
                            : issuer + "/.well-known/openid-configuration");
            JsonNode node = mapper.readTree(body);
            return new Discovery(
                    required(node, "authorization_endpoint"),
                    required(node, "token_endpoint"),
                    text(node, "userinfo_endpoint"),
                    required(node, "jwks_uri"));
        } catch (Exception exception) {
            throw new BusinessException(FederatedErrorCode.UPSTREAM_FAILED, exception);
        }
    }

    /** 使用授权码和 PKCE verifier 交换 Token，并拒绝无访问令牌或超大响应。 */
    private JsonNode exchangeCode(
            String tokenEndpoint,
            FederatedProviderConfiguration config,
            String code,
            String verifier,
            URI redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri.toString());
        form.add("client_id", config.clientId());
        form.add("code_verifier", verifier);
        if (config.clientAuthentication() == FederatedClientAuthentication.CLIENT_SECRET_POST
                && config.clientSecret() != null)
            form.add("client_secret", config.clientSecret());
        RestClient.RequestBodySpec request = restClient.post()
                .uri(URI.create(tokenEndpoint))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (config.clientAuthentication() == FederatedClientAuthentication.CLIENT_SECRET_BASIC
                && config.clientSecret() != null) {
            String value = Base64.getEncoder()
                    .encodeToString((config.clientId() + ":" + config.clientSecret()).getBytes(StandardCharsets.UTF_8));
            request.header(HttpHeaders.AUTHORIZATION, "Basic " + value);
            form.remove("client_secret");
        }
        try {
            String body = request.body(form).retrieve().body(String.class);
            if (body == null || body.length() > MAX_RESPONSE_BYTES)
                throw new IllegalStateException("Token 响应过大");
            JsonNode json = mapper.readTree(body);
            if (json.path("error").isTextual() || !json.path("access_token").isTextual())
                throw new BusinessException(FederatedErrorCode.AUTHORIZATION_REJECTED);
            return json;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(FederatedErrorCode.UPSTREAM_FAILED, exception);
        }
    }

    /** 校验 OIDC ID Token、Nonce 和 UserInfo subject 后生成统一资料。 */
    private FederatedProfile oidcProfile(
            FederatedProviderConfiguration config,
            Discovery discovery,
            JsonNode token,
            String nonce) {
        try {
            SignedJWT jwt = SignedJWT.parse(token.path("id_token").asText());
            String jwks = get(discovery.jwksEndpoint);
            IDTokenClaimsSet claims = new IDTokenValidator(
                    new Issuer(config.issuer()),
                    new ClientID(config.clientId()),
                    JWSAlgorithm.RS256,
                    JWKSet.parse(jwks)).validate(jwt, new Nonce(nonce));
            JsonNode profile = mapper.valueToTree(claims.toJSONObject());
            if (discovery.userInfoEndpoint != null) {
                JsonNode userInfo = readUserInfo(discovery.userInfoEndpoint, token.path("access_token").asText());
                if (!claims.getSubject().getValue().equals(userInfo.path("sub").asText()))
                    throw new BusinessException(FederatedErrorCode.PROFILE_INVALID);
                profile = mergeProfile(claims.toJSONObject(), userInfo);
            }
            return oidcProfile(config.issuer(), profile);
        } catch (BusinessException exception) {
            throw exception;
        } catch (ParseException | BadJOSEException | JOSEException exception) {
            throw new BusinessException(FederatedErrorCode.PROFILE_INVALID, exception);
        } catch (Exception exception) {
            throw new BusinessException(FederatedErrorCode.UPSTREAM_FAILED, exception);
        }
    }

    /** 使用配置的字段映射读取 OAuth2 UserInfo，并转换为统一外部资料。 */
    private FederatedProfile oauth2Profile(FederatedProviderConfiguration config, JsonNode token) {
        JsonNode profile = readUserInfo(config.userInfoEndpoint(), token.path("access_token").asText());
        String subjectPath = config.profileMapping().get("subject");
        String subject = scalar(profile, subjectPath);
        if (subject == null)
            throw new BusinessException(FederatedErrorCode.PROFILE_INVALID);
        return new FederatedProfile(
                null,
                subject,
                scalar(profile, config.profileMapping().get("displayName")),
                scalar(profile, config.profileMapping().get("email")),
                "true".equalsIgnoreCase(scalar(profile, config.profileMapping().get("emailVerified"))),
                scalar(profile, config.profileMapping().get("avatar")));
    }

    /** 从已经验证签名、受众和 nonce 的 OIDC Claims 转换统一外部资料。 */
    private FederatedProfile oidcProfile(String issuer, JsonNode json) {
        String subject = scalar(json, "$.sub");
        if (subject == null)
            throw new BusinessException(FederatedErrorCode.PROFILE_INVALID);
        String email = scalar(json, "$.email");
        return new FederatedProfile(
                issuer,
                subject,
                scalar(json, "$.name"),
                email,
                "true".equalsIgnoreCase(scalar(json, "$.email_verified")),
                scalar(json, "$.picture"));
    }

    /** 使用短期访问令牌读取 UserInfo，并限制响应大小。 */
    private JsonNode readUserInfo(String endpoint, String accessToken) {
        try {
            String body = restClient.get()
                    .uri(URI.create(endpoint))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.length() > MAX_RESPONSE_BYTES)
                throw new IllegalStateException("Profile 响应过大");
            return mapper.readTree(body);
        } catch (Exception exception) {
            throw new BusinessException(FederatedErrorCode.UPSTREAM_FAILED, exception);
        }
    }

    /** 读取 OIDC Discovery 或 JWKS 文档，并限制响应大小。 */
    private String get(String endpoint) {
        try {
            String body = restClient.get().uri(URI.create(endpoint)).retrieve().body(String.class);
            if (body == null || body.length() > MAX_RESPONSE_BYTES)
                throw new IllegalStateException("上游响应过大");
            return body;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(FederatedErrorCode.UPSTREAM_FAILED, exception);
        }
    }

    /** 合并 UserInfo 到 ID Token，保留 ID Token 中未被外部资料覆盖的字段。 */
    private JsonNode mergeProfile(Object idToken, JsonNode userInfo) {
        ObjectNode merged = mapper.valueToTree(idToken);
        userInfo.properties().forEach(entry -> {
            if (entry.getValue() != null && !entry.getValue().isNull() && !entry.getValue().isMissingNode()) {
                merged.set(entry.getKey(), entry.getValue());
            }
        });
        return merged;
    }

    /** 读取 Discovery 必填文本字段，缺失时中止当前上游请求。 */
    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null)
            throw new IllegalArgumentException("Discovery 缺少 " + field);
        return value;
    }

    /** 读取可选的非空文本字段。 */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    /** 按受限点分路径读取 JSON 标量，不执行表达式或数组访问。 */
    private static String scalar(JsonNode root, String path) {
        if (path == null)
            return null;
        String current = path.startsWith("$.") ? path.substring(2) : path;
        JsonNode node = root;
        for (String part : current.split("\\.")) {
            node = node == null ? null : node.get(part);
        }
        if (node == null || node.isNull() || !node.isValueNode())
            return null;
        return node.asText();
    }

    /** 创建用于 state、nonce、PKCE 和完成结果的随机 URL 安全标识。 */
    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 已解析的 Discovery 端点。 */
    @SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
    private static final class Discovery {
        private final String authorizationEndpoint;

        private final String tokenEndpoint;

        private final String userInfoEndpoint;

        private final String jwksEndpoint;

        private Discovery(
                String authorizationEndpoint,
                String tokenEndpoint,
                String userInfoEndpoint,
                String jwksEndpoint) {
            this.authorizationEndpoint = authorizationEndpoint;
            this.tokenEndpoint = tokenEndpoint;
            this.userInfoEndpoint = userInfoEndpoint;
            this.jwksEndpoint = jwksEndpoint;
        }
    }
}
