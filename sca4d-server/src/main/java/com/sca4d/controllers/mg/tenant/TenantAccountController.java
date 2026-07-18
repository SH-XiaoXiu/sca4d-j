package com.sca4d.controllers.mg.tenant;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthAccount;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.account.AccountAccessPolicy;
import com.sca4d.iam.account.AccountSecurityPermissions;
import com.sca4d.iam.account.AccountSecurityService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.request.AccountPageRequest;
import com.sca4d.iam.account.request.AccountPasswordCredentialBindRequest;
import com.sca4d.iam.account.request.AccountPasswordCredentialReplaceRequest;
import com.sca4d.iam.account.request.AccountSecondFactorRequest;
import com.sca4d.iam.account.view.AccountSecondFactorView;
import com.sca4d.iam.account.view.AccountSecurityView;
import com.sca4d.iam.account.view.AccountSessionView;
import com.sca4d.iam.account.view.ManagedAccountView;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.audit.TenantAuditOperations;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** TENANT Principal 管理当前租户 TENANT Account 的安全资源。 */
@RestController
@RequestMapping("/mg/tenant/tenants/{tenantId}/accounts")
@AuthPrincipal(types = PrincipalType.TENANT, selection = TenantPrincipalSelection.class, tenantId = "#path.tenantId")
@RequiredArgsConstructor
public class TenantAccountController {

    private final AccountService accountService;
    private final AccountSecurityService accountSecurityService;
    private final AccountAccessPolicy accountAccessPolicy;
    private final AuthenticationBrowser authenticationBrowser;

    /** 分页读取当前租户 Account，不返回角色或认证秘密。 */
    @GetMapping
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_READ)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    public @NotNull ApiResponse<PageData<ManagedAccountView>> page(
            @PathVariable @NotNull Long tenantId, @NotNull AccountPageRequest request) {
        return ApiResponse.ok(accountService.page(
                AccountScopeType.TENANT, tenantId, request.getSearch(), request.getStatus(), request.page(), request.size())
                .map(ManagedAccountView::from));
    }

    /** 读取当前租户 Account 的认证方式、MFA 和恢复码摘要。 */
    @GetMapping("/{accountId}/security")
    @RequirePermission(AccountSecurityPermissions.SECURITY_READ)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    public @NotNull ApiResponse<AccountSecurityView> security(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @AuthPrincipal @NotNull Principal principal) {
        accountAccessPolicy.requireTenantAccount(principal, tenantId, accountService.require(accountId));
        return ApiResponse.ok(AccountSecurityView.from(accountSecurityService.security(accountId)));
    }

    /** 读取当前租户 Account 的活跃 Session，不返回 Token。 */
    @GetMapping("/{accountId}/sessions")
    @RequirePermission(AccountSecurityPermissions.SESSIONS_READ)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    public @NotNull ApiResponse<List<AccountSessionView>> sessions(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @AuthPrincipal @NotNull Principal principal) {
        accountAccessPolicy.requireTenantAccount(principal, tenantId, accountService.require(accountId));
        return ApiResponse.ok(accountSecurityService.managedSessions(accountId).stream()
                .map(AccountSessionView::from)
                .toList());
    }

    /** 使用当前操作账号的 STEP_UP 修改当前租户 Account MFA 要求并重签或撤销恢复码。 */
    @PutMapping("/{accountId}/second-factor/requirement")
    @Audit(module = TenantAuditOperations.ACCOUNTS, action = TenantAuditOperations.RESET_MFA, targetType = "account", targetId = "#accountId", payload = "T(java.util.Map).of('required', #body.required)")
    @RequirePermission(AccountSecurityPermissions.MFA_RESET)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ApiResponse<AccountSecondFactorView> changeSecondFactorRequirement(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull AccountSecondFactorRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        accountAccessPolicy.requireTenantAccount(principal, tenantId, account);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (body.getVersion() != version) {
            throw new com.sca4d.common.exception.BizException(IamErrorCode.ACCOUNT_CHANGED);
        }
        return ApiResponse.ok(AccountSecondFactorView.from(accountSecurityService.changeManagedSecondFactorRequirement(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                body.getRequired(),
                version,
                authenticationId,
                authenticationBrowser.require(request))));
    }

    /** 使用当前操作账号的 STEP_UP 为当前租户 Account 重签恢复码并终止其全部 Session。 */
    @PostMapping("/{accountId}/recovery-codes")
    @Audit(module = TenantAuditOperations.ACCOUNTS, action = TenantAuditOperations.REISSUE_RECOVERY_CODES, targetType = "account", targetId = "#accountId")
    @RequirePermission(AccountSecurityPermissions.RECOVERY_MANAGE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ApiResponse<AccountSecondFactorView> reissueRecoveryCodes(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        accountAccessPolicy.requireTenantAccount(principal, tenantId, accountService.require(accountId));
        return ApiResponse.ok(AccountSecondFactorView.from(accountSecurityService.reissueManagedRecoveryCodes(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                IfMatchVersion.require(ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED),
                authenticationId,
                authenticationBrowser.require(request))));
    }

    /** 使用当前操作账号的 STEP_UP 为当前租户 Account 绑定 PASSWORD Credential。 */
    @PostMapping("/{accountId}/credentials/password")
    @Idempotent(operation = "tenant.accounts.credentials.bind-password")
    @Audit(module = TenantAuditOperations.ACCOUNTS, action = TenantAuditOperations.BIND_PASSWORD, targetType = "account", targetId = "#accountId")
    @RequirePermission(AccountSecurityPermissions.CREDENTIALS_MANAGE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ApiResponse<Long> bindPassword(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull AccountPasswordCredentialBindRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        accountAccessPolicy.requireTenantAccount(principal, tenantId, account);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (!Long.valueOf(version).equals(account.getVersion())) {
            throw new com.sca4d.common.exception.BizException(IamErrorCode.ACCOUNT_CHANGED);
        }
        return ApiResponse.created(accountSecurityService.bindManagedPassword(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                body.getIdentifier(),
                body.credential(),
                authenticationId,
                authenticationBrowser.require(request)));
    }

    /** 使用当前操作账号的 STEP_UP 和 Credential If-Match 重置当前租户 Account 密码。 */
    @PutMapping("/{accountId}/credentials/{credentialId}/password")
    @Audit(module = TenantAuditOperations.ACCOUNTS, action = TenantAuditOperations.REPLACE_PASSWORD, targetType = "credential", targetId = "#credentialId")
    @RequirePermission(AccountSecurityPermissions.CREDENTIALS_MANAGE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ApiResponse<Void> replacePassword(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @PathVariable @NotNull Long credentialId,
            @Valid @RequestBody @NotNull AccountPasswordCredentialReplaceRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        accountAccessPolicy.requireTenantAccount(principal, tenantId, accountService.require(accountId));
        accountSecurityService.replaceManagedPassword(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                credentialId,
                IfMatchVersion.require(
                        ifMatch,
                        IamErrorCode.CREDENTIAL_IF_MATCH_REQUIRED,
                        IamErrorCode.CREDENTIAL_CHANGED),
                body.credential(),
                authenticationId,
                authenticationBrowser.require(request));
        return ApiResponse.ok();
    }

    /** 使用操作账号的 STEP_UP 撤销当前租户 Account 的指定 Session。 */
    @DeleteMapping("/{accountId}/sessions/{sessionId}")
    @Audit(module = TenantAuditOperations.ACCOUNTS, action = TenantAuditOperations.REVOKE_SESSION, targetType = "account", targetId = "#accountId", payload = "T(java.util.Map).of('sessionId', #sessionId)")
    @RequirePermission(AccountSecurityPermissions.SESSIONS_REVOKE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ResponseEntity<Void> endSession(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @PathVariable @BizId(max = 128) @NotNull String sessionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        accountAccessPolicy.requireTenantAccount(principal, tenantId, account);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (!Long.valueOf(version).equals(account.getVersion())) {
            throw new com.sca4d.common.exception.BizException(IamErrorCode.ACCOUNT_CHANGED);
        }
        accountSecurityService.endManagedSession(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                sessionId,
                authenticationId,
                authenticationBrowser.require(request));
        return ResponseEntity.noContent().build();
    }

    /** 使用操作账号的 STEP_UP 撤销当前租户 Account 的全部 Session。 */
    @DeleteMapping("/{accountId}/sessions")
    @Audit(module = TenantAuditOperations.ACCOUNTS, action = TenantAuditOperations.REVOKE_SESSIONS, targetType = "account", targetId = "#accountId")
    @RequirePermission(AccountSecurityPermissions.SESSIONS_REVOKE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ResponseEntity<Void> endSessions(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        accountAccessPolicy.requireTenantAccount(principal, tenantId, account);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (!Long.valueOf(version).equals(account.getVersion())) {
            throw new com.sca4d.common.exception.BizException(IamErrorCode.ACCOUNT_CHANGED);
        }
        accountSecurityService.endManagedSessions(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                authenticationId,
                authenticationBrowser.require(request));
        return ResponseEntity.noContent().build();
    }
}
