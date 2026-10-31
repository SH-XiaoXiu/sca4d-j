package com.sca4d.open.oauth;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.auth.session.SessionService;
import com.sca4d.auth.sharedsecret.SharedSecretAuthenticationMethod;
import com.sca4d.auth.sharedsecret.SharedSecretProof;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.authentication.EstablishedSession;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.open.client.OpenClient;
import com.sca4d.open.client.OpenClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 执行应用通过 {@link OpenAuthenticationEndpoint} 公开的 Client Credentials Token 交换。 */
public class OpenTokenEndpointHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTokenEndpointHandler.class);

    private final @NotNull IdentityService identities;

    private final @NotNull OpenClientService clients;

    private final @NotNull OpenJwtTokenService jwtTokens;

    private final @NotNull SessionService sessions;

    private final @NotNull SharedSecretAuthenticationMethod sharedSecrets;

    /**
     * 创建使用现有 Client、IAM、Shared Secret、Session 和 Open JWT 服务的端点 Handler。
     *
     * @param clients Open Client 服务
     * @param identities IAM 身份服务
     * @param sharedSecrets Shared Secret 认证方法
     * @param sessions Session 建立服务
     * @param jwtTokens Open 独立 JWT 服务
     */
    public OpenTokenEndpointHandler(
            @NotNull OpenClientService clients,
            @NotNull IdentityService identities,
            @NotNull SharedSecretAuthenticationMethod sharedSecrets,
            @NotNull SessionService sessions,
            @NotNull OpenJwtTokenService jwtTokens) {
        this.clients = Objects.requireNonNull(clients, "Open Client 服务不能为空");
        this.identities = Objects.requireNonNull(identities, "身份服务不能为空");
        this.sharedSecrets = Objects.requireNonNull(sharedSecrets, "Shared Secret 方法不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 服务不能为空");
        this.jwtTokens = Objects.requireNonNull(jwtTokens, "Open JWT 服务不能为空");
    }

    /**
     * 使用 HTTP Basic 中的 Client ID 和 Shared Secret 签发 Bearer Token。
     *
     * @param grantType 固定为 {@code client_credentials}
     * @param authorization 使用 {@code client_secret_basic} 的 HTTP Basic 请求头
     * @return 当前应用 Token 模式对应的 Access Token、剩余秒数和 Operation scope
     * @throws BusinessException grant type、Basic 凭据、Client、Account 或 Shared Secret 不可用
     */
    @AnonymousAccess
    @Operation(operationId = "openApiClientCredentialsToken", tags = "认证", summary = "签发 Open Access Token", description = "使用 Client ID 和 Shared Secret 完成 Client Credentials 认证并签发 Bearer Token。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "认证成功并签发 Access Token", useReturnTypeSchema = true, content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "token", summary = "Token 签发成功", value = """
                    {
                      "access_token": "eyJraWQiOiJvcGVuYXBpLTIwMjYtMDgifQ.example",
                      "expires_in": 300,
                      "scope": "iot:devices:read",
                      "token_type": "Bearer"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "grant_type 缺失或表单格式不合法", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/OpenApiError"), examples = @ExampleObject(name = "invalidRequest", summary = "请求参数不合法", value = """
                    {
                      "code": "1100",
                      "messageKey": null,
                      "messageArgs": [],
                      "message": "请求参数不合法",
                      "data": null,
                      "callId": "01K2N8Y6T4FJ7N8Z5P3Q6R9S0A",
                      "timestamp": "2026-08-15T02:30:00Z"
                    }
                    """))),
            @ApiResponse(responseCode = "401", description = "Client ID、Shared Secret、Client 状态或 Account 状态不允许签发 Token", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/OpenApiError"), examples = @ExampleObject(name = "authenticationFailed", summary = "认证失败", value = """
                    {
                      "code": "2100",
                      "messageKey": "error.auth.authentication-failed",
                      "messageArgs": [],
                      "message": "认证失败",
                      "data": null,
                      "callId": "01K2N8Y6T4FJ7N8Z5P3Q6R9S0A",
                      "timestamp": "2026-08-15T02:30:00Z"
                    }
                    """)))
    })
    public @NotNull OpenTokenResponse token(
            @Parameter(description = "OAuth 2.0 grant type，固定为 client_credentials。", example = "client_credentials", required = true) @RequestParam("grant_type") @NotNull String grantType,
            @Parameter(hidden = true) @RequestHeader("Authorization") @NotNull String authorization) {
        if (!"client_credentials".equals(grantType)) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }

        // 统一校验 Client、Account 和 Shared Secret，不向调用者泄露具体失败对象。
        BasicCredentials credentials = parseBasic(authorization);
        OpenClient client = loadClientById(credentials.clientId);
        if (!client.active()) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        Account account = activeClientAccountById(client.accountId());
        var verified = sharedSecrets.authenticate(account, new SharedSecretProof(credentials.secret));

        // 建立 Application Session，并按应用配置签发现有 Session Token 或独立 Open JWT。
        EstablishedSession established = sessions.establish(account, List.of(verified.authentication()));
        List<TreeCode> operations = clients.operations(client.clientId());
        String scope = operations.stream().map(code -> code.format(COLON)).collect(Collectors.joining(" "));
        OpenTokenResponse response;
        if (jwtTokens.enabled()) {
            OpenJwtTokenService.IssuedOpenJwt token = jwtTokens.issue(established.session());
            response = new OpenTokenResponse(token.token(), token.expiresAt(), scope);
        } else {
            response = new OpenTokenResponse(
                    established.tokens().accessToken(),
                    established.tokens().accessTokenExpiresAt(),
                    scope);
        }
        LOG.info(
                "Open Token 已签发 | clientId={} accountId={} operationCount={}",
                client.clientId(),
                client.accountId(),
                operations.size());
        return response;
    }

    /** 把未知 Client 转换为不泄露 Client 是否存在的认证失败。 */
    private @NotNull OpenClient loadClientById(@NotNull String clientId) {
        try {
            return clients.loadByClientId(clientId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED, exception);
        }
    }

    /** 把不存在或停用的 Client Account 转换为统一认证失败。 */
    private @NotNull Account activeClientAccountById(long accountId) {
        try {
            return identities.activeAccountById(accountId);
        } catch (BusinessException exception) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED, exception);
        }
    }

    /** 解析 {@code client_secret_basic} 请求头并统一转换格式失败。 */
    private static @NotNull BasicCredentials parseBasic(@NotNull String header) {
        if (!header.startsWith("Basic ")) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(header.substring("Basic ".length()).trim()),
                    StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("Basic 凭据格式错误");
            }
            return new BasicCredentials(decoded.substring(0, separator), decoded.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED, exception);
        }
    }

    /** 保存已经从 HTTP Basic 请求头分离的 Client ID 和 Shared Secret。 */
    private static class BasicCredentials {

        private final @NotNull String clientId;

        private final @NotNull String secret;

        private BasicCredentials(@NotNull String clientId, @NotNull String secret) {
            this.clientId = clientId;
            this.secret = secret;
        }

    }
}
