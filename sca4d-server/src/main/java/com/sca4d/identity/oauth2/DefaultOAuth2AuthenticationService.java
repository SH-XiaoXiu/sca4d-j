package com.sca4d.identity.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.AccessTokenType;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationScope;
import com.sca4d.authentication.AuthenticationScopeService;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.net.HttpEndpoints;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.external.ExternalIdentityAuthenticationProof;
import com.sca4d.identity.external.ExternalIdentityAuthenticationService;
import com.sca4d.identity.external.ExternalIdentityVerification;
import com.sca4d.identity.repository.IdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 完成 OAuth2 Authorization Code + PKCE，并把上游 Profile 映射为外部身份。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultOAuth2AuthenticationService implements OAuth2AuthenticationService {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final AuthenticationProcessService authentications;
    private final AuthenticationScopeService authenticationScopes;
    private final OAuth2AuthorizationStore authorizations;
    private final IdentityProviderRepository providers;
    private final OAuth2ProviderRepository oauth2Providers;
    private final SecretKeyring keyring;
    private final OAuth2ProfileMapper profileMapper;
    private final ExternalIdentityAuthenticationService externalIdentityAuthentication;
    private final Sca4djIdentityProviderProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public @NotNull List<OAuth2AvailableProvider> providers(
            @NotNull AccountScopeType scopeType, @Nullable String tenantCode) {
        AuthenticationScope authenticationScope = authenticationScopes.resolve(scopeType, tenantCode);
        IdentityProviderScope scope = new IdentityProviderScope(
                authenticationScope.getScopeType(), authenticationScope.getScopeId());
        return providers.findProviders(scope).stream()
                .filter(provider -> provider.getProtocol() == IdentityProviderProtocol.OAUTH2)
                .filter(provider -> provider.getStatus() == IdentityProviderStatus.ACTIVE)
                .map(provider -> new OAuth2AvailableProvider(provider.getCode(), provider.getName()))
                .toList();
    }

    @Override
    public @NotNull OAuth2AuthorizationStart authorize(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String providerCode) {
        AuthenticationProcess authentication = authentications.requireProof(
                authenticationId, browserHash, AuthMethodType.OAUTH2);
        IdentityProviderScope scope = new IdentityProviderScope(authentication.getScopeType(), authentication.getScopeId());
        IdentityProvider provider = providers.findProvider(scope, providerCode);
        if (provider == null
                || provider.getProtocol() != IdentityProviderProtocol.OAUTH2
                || provider.getStatus() != IdentityProviderStatus.ACTIVE) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_PROVIDER_UNAVAILABLE);
        }
        OAuth2Client client = oauth2Providers.findClient(provider.getProviderId(), provider.getVersion());
        if (client == null) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_PROVIDER_UNAVAILABLE);
        }
        Sca4djIdentityProviderProperties.OAuth2 oauth2 = properties.getOauth2();
        URI redirectUri = oauth2.getRedirectUri();
        URI completionUri = oauth2.getCompletionUri();
        if (!HttpEndpoints.isSecure(redirectUri) || !HttpEndpoints.isSecure(completionUri)) {
            throw new IllegalStateException("OAuth2 redirect-uri 和 completion-uri 必须是精确 HTTPS URI；本机开发允许 HTTP");
        }
        Duration ttl = oauth2.getAuthorizationTtl();
        if (ttl == null
                || ttl.isZero()
                || ttl.isNegative()
                || ttl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalStateException("OAuth2 authorization-ttl 必须大于 0 且不超过 15 分钟");
        }

        State state = new State();
        CodeVerifier codeVerifier = new CodeVerifier();
        AuthorizationRequest request = new AuthorizationRequest.Builder(
                new ResponseType(ResponseType.Value.CODE), new ClientID(client.getClientId()))
                .endpointURI(URI.create(client.getAuthorizationUri()))
                .redirectionURI(redirectUri)
                .scope(new Scope(client.getScopes().toArray(String[]::new)))
                .state(state)
                .codeChallenge(codeVerifier, CodeChallengeMethod.S256)
                .build();
        Instant now = Instant.now();
        OAuth2Authorization authorization = new OAuth2Authorization()
                .setAuthorizationId(state.getValue())
                .setAuthenticationId(authentication.getAuthenticationId())
                .setProviderId(provider.getProviderId())
                .setProviderVersion(provider.getVersion())
                .setClientId(client.getClientId())
                .setCodeVerifier(codeVerifier.getValue())
                .setRedirectUri(redirectUri.toString())
                .setBrowserHash(browserHash)
                .setCreatedAt(now)
                .setExpiresAt(now.plus(ttl));
        if (!authorizations.create(authorization, ttl)) {
            throw new InternalServiceException("OAuth2 state 已存在");
        }
        log.info(
                "OAuth2 授权请求已创建 | authenticationId={} providerId={} purpose={} expiresAt={}",
                authentication.getAuthenticationId(),
                provider.getProviderId(),
                authentication.getPurpose(),
                authorization.getExpiresAt());
        return new OAuth2AuthorizationStart(request.toURI().toString(), authorization.getExpiresAt());
    }

    @Override
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.OAUTH2)
    public @NotNull AuthenticationProcess callback(
            @NotNull String authorizationId,
            @Nullable String code,
            @Nullable String error,
            @NotNull String browserHash) {
        if (!authorizationId.matches("[A-Za-z0-9_-]{20,128}")) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_AUTHORIZATION_UNAVAILABLE);
        }
        OAuth2Authorization authorization = authorizations.consume(authorizationId, browserHash);
        if (authorization == null || authorization.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_AUTHORIZATION_UNAVAILABLE);
        }
        if (error != null || code == null || code.isBlank()) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_AUTHORIZATION_REJECTED);
        }

        AuthenticationProcess authentication = authentications.requireProof(
                authorization.getAuthenticationId(), browserHash, AuthMethodType.OAUTH2);
        IdentityProviderScope scope = new IdentityProviderScope(authentication.getScopeType(), authentication.getScopeId());
        IdentityProvider provider = providers.findProvider(scope, authorization.getProviderId());
        if (provider == null
                || provider.getProtocol() != IdentityProviderProtocol.OAUTH2
                || provider.getStatus() != IdentityProviderStatus.ACTIVE
                || !provider.getVersion().equals(authorization.getProviderVersion())) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_PROVIDER_UNAVAILABLE);
        }
        OAuth2Client client = oauth2Providers.findClient(provider.getProviderId(), provider.getVersion());
        if (client == null || !client.getClientId().equals(authorization.getClientId())) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_PROVIDER_UNAVAILABLE);
        }

        String clientSecret = keyring.decrypt(
                "identity-provider.oauth2." + provider.getProviderId() + ".client-secret",
                client.getEncryptedClientSecret());
        AuthorizationCodeGrant grant = new AuthorizationCodeGrant(
                new AuthorizationCode(code),
                URI.create(authorization.getRedirectUri()),
                new CodeVerifier(authorization.getCodeVerifier()));
        TokenResponse tokenResponse;
        long tokenStartedAt = System.nanoTime();
        try {
            tokenResponse = TokenResponse.parse(send(new TokenRequest.Builder(
                    URI.create(client.getTokenUri()),
                    new ClientSecretBasic(new ClientID(client.getClientId()), new Secret(clientSecret)),
                    grant)
                    .build()
                    .toHTTPRequest()));
        } catch (ParseException exception) {
            log.debug(
                    "OAuth2 Token 响应无法解析 | authenticationId={} providerId={} durationMs={}",
                    authentication.getAuthenticationId(),
                    provider.getProviderId(),
                    (System.nanoTime() - tokenStartedAt) / 1_000_000);
            throw new BizException(IdentityProviderErrorCode.OAUTH2_AUTHORIZATION_REJECTED, exception);
        }
        if (!tokenResponse.indicatesSuccess()) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_AUTHORIZATION_REJECTED);
        }
        AccessToken accessToken = tokenResponse.toSuccessResponse().getTokens().getAccessToken();
        if (accessToken == null || !AccessTokenType.BEARER.equals(accessToken.getType())) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_AUTHORIZATION_REJECTED);
        }
        log.debug(
                "OAuth2 Token 交换完成 | authenticationId={} providerId={} durationMs={}",
                authentication.getAuthenticationId(),
                provider.getProviderId(),
                (System.nanoTime() - tokenStartedAt) / 1_000_000);

        JsonNode profile = readProfile(
                client.getProfileUri(), accessToken.toAuthorizationHeader(), authentication.getAuthenticationId(), provider.getProviderId());
        JsonNode auxiliaryProfile = client.getAuxiliaryProfileUri() == null
                ? null
                : readProfile(
                        client.getAuxiliaryProfileUri(),
                        accessToken.toAuthorizationHeader(),
                        authentication.getAuthenticationId(),
                        provider.getProviderId());
        OAuth2ProfilePreview mapped = profileMapper.map(profile, auxiliaryProfile, client.getProfileMapping());
        if (mapped.getMissingFields().contains(OAuth2ProfileField.SUBJECT)) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_PROFILE_INVALID);
        }

        // Token 交换和 Profile 映射全部通过后才选定 OAuth2；上游失败时可以重新发起授权。
        authentication = authentications.claimPrimary(
                authorization.getAuthenticationId(), browserHash, AuthMethodType.OAUTH2);
        ExternalIdentityVerification verification = externalIdentityAuthentication.authenticate(
                authentication, provider, mapped.getProfile());
        AuthenticationProcess verified = authentications.proof(
                authentication.getAuthenticationId(),
                browserHash,
                AuthMethodType.OAUTH2,
                ExternalIdentityAuthenticationProof.class,
                new ExternalIdentityAuthenticationProof(
                        verification.getIdentityId(), verification.getIdentityVersion()));
        log.info(
                "OAuth2 身份验证通过 | authenticationId={} providerId={} identityId={} subjectId={} accountId={} activityId={}",
                verified.getAuthenticationId(),
                provider.getProviderId(),
                verification.getIdentityId(),
                verification.getSubject().getSubjectId(),
                verification.getAccount().getAccountId(),
                verified.getActivityId());
        return verified;
    }

    /** @return 使用 Bearer Token 读取且限制大小的 JSON Profile */
    private @NotNull JsonNode readProfile(
            @NotNull String uri,
            @NotNull String authorization,
            @NotNull String authenticationId,
            @NotNull Long providerId) {
        long startedAt = System.nanoTime();
        try {
            byte[] body = restClient
                    .get()
                    .uri(URI.create(uri))
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .exchange((request, response) -> {
                        byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (!response.getStatusCode().is2xxSuccessful() || bytes.length > MAX_RESPONSE_BYTES) {
                            throw new BizException(IdentityProviderErrorCode.OAUTH2_UPSTREAM_FAILED);
                        }
                        return bytes;
                    });
            if (body == null || body.length == 0) {
                throw new BizException(IdentityProviderErrorCode.OAUTH2_PROFILE_INVALID);
            }
            JsonNode profile = objectMapper.readTree(body);
            if (!profile.isObject() && !profile.isArray()) {
                throw new BizException(IdentityProviderErrorCode.OAUTH2_PROFILE_INVALID);
            }
            log.debug(
                    "OAuth2 Profile 读取完成 | authenticationId={} providerId={} responseBytes={} durationMs={}",
                    authenticationId,
                    providerId,
                    body.length,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return profile;
        } catch (IOException exception) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_PROFILE_INVALID, exception);
        } catch (RestClientException exception) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_UPSTREAM_FAILED, exception);
        }
    }

    /** @return 使用共享超时、禁止重定向并限制响应大小的 Nimbus HTTP 响应 */
    private @NotNull HTTPResponse send(@NotNull HTTPRequest request) {
        try {
            RestClient.RequestBodySpec outbound = restClient
                    .method(switch (request.getMethod()) {
                        case GET -> HttpMethod.GET;
                        case POST -> HttpMethod.POST;
                        case PUT -> HttpMethod.PUT;
                        case DELETE -> HttpMethod.DELETE;
                    })
                    .uri(request.getURI());
            request.getHeaderMap().forEach((name, values) -> {
                if (name != null && values != null) {
                    outbound.header(name, values.toArray(String[]::new));
                }
            });
            if (request.getBody() != null) {
                outbound.body(request.getBody());
            }
            HTTPResponse response = outbound.exchange((clientRequest, clientResponse) -> {
                byte[] body = clientResponse.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) {
                    throw new BizException(IdentityProviderErrorCode.OAUTH2_UPSTREAM_FAILED);
                }
                HTTPResponse result = new HTTPResponse(clientResponse.getStatusCode().value());
                result.setStatusMessage(clientResponse.getStatusText());
                clientResponse.getHeaders().forEach(
                        (name, values) -> result.setHeader(name, values.toArray(String[]::new)));
                if (body.length > 0) {
                    result.setBody(new String(body, StandardCharsets.UTF_8));
                }
                return result;
            });
            if (response == null) {
                throw new BizException(IdentityProviderErrorCode.OAUTH2_UPSTREAM_FAILED);
            }
            return response;
        } catch (RestClientException exception) {
            throw new BizException(IdentityProviderErrorCode.OAUTH2_UPSTREAM_FAILED, exception);
        }
    }

}
