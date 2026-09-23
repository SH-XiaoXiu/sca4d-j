package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.FederatedBrowserBinding;
import com.sca4d.app.server.authentication.FederatedLoginProperties;
import com.sca4d.app.server.authentication.RequireAuthentication;
import com.sca4d.app.server.authentication.request.FederatedProofRequest;
import com.sca4d.app.server.authentication.view.*;
import com.sca4d.app.server.service.ActionAuthenticationService;
import com.sca4d.app.server.service.PlatformLoginService;
import com.sca4d.auth.federation.*;
import com.sca4d.auth.federation.identity.FederatedIdentityService;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** 提供匿名联邦登录、当前账号绑定和解绑外部身份的 HTTP 入口。 */
@RestController
@RequestMapping
@Tag(name = "Federated Authentication")
public class FederatedAuthenticationController {

    private final FederatedProviderService providers;

    private final FederatedIdentityService identityBindings;

    private final FederatedAuthorizationService authorizations;

    private final FederatedAuthorizationStore authorizationStore;

    private final FederatedAccountResolver accounts;

    private final PlatformLoginService login;

    private final ActionAuthenticationService actions;

    private final FederatedBrowserBinding bindings;

    private final FederatedLoginProperties properties;

    /** 创建联邦认证 HTTP 入口。 */
    public FederatedAuthenticationController(
            FederatedProviderService providers,
            FederatedIdentityService identityBindings,
            FederatedAuthorizationService authorizations,
            FederatedAuthorizationStore authorizationStore,
            FederatedAccountResolver accounts,
            PlatformLoginService login,
            ActionAuthenticationService actions,
            FederatedBrowserBinding bindings,
            FederatedLoginProperties properties) {
        this.providers = Objects.requireNonNull(providers, "Provider 服务不能为空");
        this.identityBindings = Objects.requireNonNull(identityBindings, "联邦身份服务不能为空");
        this.authorizations = Objects.requireNonNull(authorizations, "授权服务不能为空");
        this.authorizationStore = Objects.requireNonNull(authorizationStore, "授权存储不能为空");
        this.accounts = Objects.requireNonNull(accounts, "账号解析器不能为空");
        this.login = Objects.requireNonNull(login, "登录服务不能为空");
        this.actions = Objects.requireNonNull(actions, "动作认证服务不能为空");
        this.bindings = Objects.requireNonNull(bindings, "浏览器绑定不能为空");
        this.properties = Objects.requireNonNull(properties, "联邦配置不能为空");
    }

    /** 返回当前启用且允许登录页显示的 Provider。 */
    @GetMapping("/login/federated-providers")
    @AnonymousAccess
    @Operation(summary = "查询联邦登录 Provider")
    public @NotNull ApiResponse<List<FederatedProviderView>> loginProviders() {
        return ApiResponse.ok(providers.findLoginProviders().stream().map(FederatedProviderView::from).toList());
    }

    /** 为匿名登录或当前账号绑定开始上游授权。 */
    @PostMapping("/login/federated-providers/{providerCode}/authorizations")
    @AnonymousAccess
    @Operation(summary = "开始联邦授权")
    public @NotNull ApiResponse<FederatedAuthorizationView> startLogin(
            @PathVariable @Size(max = 64) @NotNull String providerCode,
            @NotNull HttpServletRequest httpRequest) {
        String url = authorizations
                .start(
                        providerCode,
                        FederatedAuthorizationPurpose.LOGIN,
                        bindings.binding(httpRequest),
                        properties.getCallbackUri());
        return ApiResponse.ok(FederatedAuthorizationView.from(url));
    }

    /** 接收上游 callback，并将一次性 completion id 放入前端完成地址。 */
    @GetMapping("/login/federated-authorizations/callback")
    @AnonymousAccess
    @Operation(summary = "接收联邦 Provider 回调")
    public ResponseEntity<Void> callback(
            @RequestParam @NotNull String state,
            @RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String error,
            @NotNull HttpServletRequest httpRequest) {
        String completion = authorizations
                .callback(state, code, error, bindings.binding(httpRequest), properties.getCallbackUri());
        URI location = UriComponentsBuilder.fromUri(properties.getCompletionUri())
                .queryParam("federatedCompletion", completion)
                .build(true)
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(location);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /** 使用回调完成资料开始匿名登录认证尝试。 */
    @PostMapping("/login/attempts/federated")
    @AnonymousAccess
    @Operation(summary = "使用联邦授权开始登录")
    public @NotNull ApiResponse<LoginResultView> federatedLogin(
            @RequestBody @Valid @NotNull FederatedProofRequest request,
            @NotNull HttpServletRequest httpRequest) {
        LoginResultView result = LoginResultView.from(
                login.start(
                        new AuthenticationSubmission<>(
                                FederatedAuthenticationMethod.CODE,
                                FederatedProof.class,
                                new FederatedProof(request.getAuthorizationId(), bindings.binding(httpRequest)))));
        return ApiResponse.created(result, URI.create("/login/attempts/" + result.getAuthentication().getAttemptId()));
    }

    /** 向登录尝试提交联邦授权完成资料。 */
    @PostMapping("/login/attempts/{attemptId}/federated/proofs")
    @AnonymousAccess
    public @NotNull ApiResponse<LoginResultView> federatedLoginProof(
            @PathVariable @Size(max = 128) @NotNull String attemptId,
            @RequestBody @Valid @NotNull FederatedProofRequest request,
            @NotNull HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                LoginResultView.from(
                        login.submit(
                                new AuthenticationAttemptId(attemptId),
                                new AuthenticationSubmission<>(
                                        FederatedAuthenticationMethod.CODE,
                                        FederatedProof.class,
                                        new FederatedProof(
                                                request.getAuthorizationId(),
                                                bindings.binding(httpRequest))))));
    }

    /** 向当前 Session 的动作认证提交联邦授权。 */
    @PostMapping("/verifications/{verificationId}/federated/proofs")
    public @NotNull ApiResponse<VerificationView> federatedVerification(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @RequestBody @Valid @NotNull FederatedProofRequest request,
            @NotNull HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                VerificationView.from(
                        actions.submit(
                                new AuthenticationAttemptId(verificationId),
                                new AuthenticationSubmission<>(
                                        FederatedAuthenticationMethod.CODE,
                                        FederatedProof.class,
                                        new FederatedProof(
                                                request.getAuthorizationId(),
                                                bindings.binding(httpRequest))))));
    }

    /** 查询当前账号已经绑定的联邦身份。 */
    @GetMapping("/me/authentication/federated-identities")
    public @NotNull ApiResponse<List<FederatedIdentityView>> identities(@CurrentAccount @NotNull Account account) {
        return ApiResponse
                .ok(
                        identityBindings.findByAccount(account.accountId())
                                .stream()
                                .map(FederatedIdentityView::from)
                                .toList());
    }

    /** 为当前账号开始指定 Provider 的联邦绑定授权。 */
    @PostMapping("/me/authentication/federated-identities/{providerCode}/authorizations")
    public @NotNull ApiResponse<FederatedAuthorizationView> startLink(
            @PathVariable @Size(max = 64) @NotNull String providerCode,
            @NotNull HttpServletRequest httpRequest) {
        String url = authorizations
                .start(
                        providerCode,
                        FederatedAuthorizationPurpose.LINK,
                        bindings.binding(httpRequest),
                        properties.getCallbackUri());
        return ApiResponse.ok(FederatedAuthorizationView.from(url));
    }

    /** 消费一次性联邦资料并绑定到当前账号；调用前必须完成动作认证。 */
    @PostMapping("/me/authentication/federated-identities")
    @RequireAuthentication(AuthenticationActions.FEDERATED_LINK_CODE)
    @Audit(operation = "authentication/account/federated-identity/link")
    public @NotNull ApiResponse<FederatedIdentityView> link(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Account account,
            @RequestBody @Valid @NotNull FederatedProofRequest request,
            @NotNull HttpServletRequest httpRequest) {
        FederatedCompletedAuthorization completed = authorizationStore
                .consumeCompletion(
                        request.getAuthorizationId(),
                        bindings.binding(httpRequest),
                        FederatedAuthorizationPurpose.LINK);
        if (completed == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        FederatedAccountResolution resolution = accounts
                .link(account, providers.activeByCode(completed.providerCode()), completed.profile());
        FederatedIdentity identity = identityBindings.findByAccount(account.accountId())
                .stream()
                .filter(item -> item.id() == resolution.identityId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("外部身份绑定未生成"));
        return ApiResponse.created(
                FederatedIdentityView.from(identity),
                URI.create("/me/authentication/federated-identities/" + identity.id()));
    }

    /** 解绑当前账号的外部身份；调用前必须完成动作认证。 */
    @DeleteMapping("/me/authentication/federated-identities/{identityId}")
    @RequireAuthentication(AuthenticationActions.FEDERATED_UNLINK_CODE)
    @Audit(operation = "authentication/account/federated-identity/unlink")
    public @NotNull ApiResponse<Void> unlink(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Account account,
            @PathVariable long identityId,
            @RequestParam long version) {
        identityBindings.unlink(identityId, account.accountId(), version);
        return ApiResponse.noContent();
    }
}
