package com.sca4d.openapi.web;

import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.hmac.HmacProof;
import com.sca4d.auth.session.SessionService;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.authentication.EstablishedSession;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.spring.call.CallHandlerInterceptor;
import com.sca4d.core.spring.web.BearerToken;
import com.sca4d.openapi.client.OpenApiClient;
import com.sca4d.openapi.client.OpenApiClientService;
import com.sca4d.openapi.oauth.OpenApiJwtTokenService;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 在通用认证拦截器前恢复 OpenAPI 独立 JWT 或验证 OpenAPI MAC 请求。 */
public class OpenApiAuthenticationInterceptor extends CallHandlerInterceptor {

    /** OpenAPI MAC Client 标识请求头。 */
    public static final String CLIENT_ID_HEADER = "X-OpenApi-Client-Id";

    /** OpenAPI MAC Unix 秒请求头。 */
    public static final String TIMESTAMP_HEADER = "X-OpenApi-Timestamp";

    /** OpenAPI MAC 一次性随机数请求头。 */
    public static final String NONCE_HEADER = "X-OpenApi-Nonce";

    /** OpenAPI MAC 无填充 Base64URL 签名请求头。 */
    public static final String SIGNATURE_HEADER = "X-OpenApi-Signature";

    private final @NotNull OpenApiJwtTokenService jwtTokens;

    private final @NotNull OpenApiClientService clients;

    private final @NotNull HmacAuthenticationMethod hmac;

    private final @NotNull SessionService sessions;

    /** 创建 OpenAPI 认证拦截器。 */
    public OpenApiAuthenticationInterceptor(
            @NotNull OpenApiJwtTokenService jwtTokens,
            @NotNull OpenApiClientService clients,
            @NotNull HmacAuthenticationMethod hmac,
            @NotNull SessionService sessions) {
        this.jwtTokens = Objects.requireNonNull(jwtTokens, "OpenAPI JWT 服务不能为空");
        this.clients = Objects.requireNonNull(clients, "OpenAPI Client 服务不能为空");
        this.hmac = Objects.requireNonNull(hmac, "HMAC 方法不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 服务不能为空");
    }

    /** 恢复 OpenAPI JWT 或将 MAC Proof 转换为当前调用的 Session。 */
    @Override
    protected boolean preHandleWithCall(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            @NotNull Call call) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), OpenApiRoute.class) == null
                || call.isAuthenticationAssessed()) {
            return true;
        }
        Optional<String> bearer = BearerToken.parse(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (bearer.isPresent()) {
            jwtTokens.restore(bearer.get()).ifPresent(call::authenticate);
            return true;
        }
        if (hasHmacHeader(request)) {
            authenticateHmac(request, call);
        }
        return true;
    }

    private void authenticateHmac(HttpServletRequest request, Call call) {
        String clientId = requireHeader(request, CLIENT_ID_HEADER);
        String timestampValue = requireHeader(request, TIMESTAMP_HEADER);
        String nonce = requireHeader(request, NONCE_HEADER);
        String signature = requireHeader(request, SIGNATURE_HEADER);
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestampValue);
        } catch (NumberFormatException exception) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        if (!(request instanceof OpenApiCachedBodyHttpServletRequest cached)) {
            throw new IllegalStateException("OpenAPI 请求体没有经过缓存过滤器");
        }
        OpenApiClient client;
        try {
            client = clients.loadByClientId(clientId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        VerifiedAuthentication verification;
        try {
            verification = hmac.authenticate(
                    clientsAccount(client),
                    new HmacProof(
                            Instant.ofEpochSecond(epochSeconds),
                            nonce,
                            new OpenApiHmacCanonicalRequest(
                                    clientId,
                                    request.getMethod(),
                                    requestPath(request),
                                    canonicalQuery(request.getQueryString()),
                                    cached.body(),
                                    Instant.ofEpochSecond(epochSeconds),
                                    nonce)
                                    .canonicalBytes(),
                            signature));
        } catch (BusinessException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        EstablishedSession established = sessions.establish(
                verification.account(),
                List.of(verification.authentication()));
        call.authenticate(established.session());
    }

    private Account clientsAccount(OpenApiClient client) {
        try {
            return clients.account(client.accountId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
    }

    private static boolean hasHmacHeader(HttpServletRequest request) {
        return request.getHeader(CLIENT_ID_HEADER) != null
                || request.getHeader(TIMESTAMP_HEADER) != null
                || request.getHeader(NONCE_HEADER) != null
                || request.getHeader(SIGNATURE_HEADER) != null;
    }

    private static String requireHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        return value;
    }

    private static String requestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        if (!path.startsWith("/openapi/")) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        return path;
    }

    private static String canonicalQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        return Arrays.stream(query.split("&", -1))
                .sorted()
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }
}
