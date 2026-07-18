package com.sca4d.controllers.mg.platform;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
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
import com.sca4d.common.exception.BizException;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.account.AccountAccessPolicy;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.AccountSecurityPermissions;
import com.sca4d.iam.account.AccountSecurityService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.request.AccountPageRequest;
import com.sca4d.iam.account.request.AccountPasswordCredentialBindRequest;
import com.sca4d.iam.account.request.AccountPasswordCredentialReplaceRequest;
import com.sca4d.iam.account.request.AccountSecondFactorRequest;
import com.sca4d.iam.account.request.GlobalAccountCreateRequest;
import com.sca4d.iam.account.request.ManagedAccountProfileUpdateRequest;
import com.sca4d.iam.account.request.ManagedAccountStatusRequest;
import com.sca4d.iam.account.service.AccountLifecycleService;
import com.sca4d.iam.account.service.ManagedAccountProfileService;
import com.sca4d.iam.account.view.AccountSecondFactorView;
import com.sca4d.iam.account.view.AccountSecurityView;
import com.sca4d.iam.account.view.AccountSessionView;
import com.sca4d.iam.account.view.ManagedAccountProfileView;
import com.sca4d.iam.account.view.ManagedAccountView;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.iam.profile.request.DefaultProfileAvatarDeleteRequest;
import com.sca4d.iam.profile.request.DefaultProfileAvatarUpdateRequest;
import com.sca4d.iam.profile.service.DefaultProfileService;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.platform.audit.PlatformAuditOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台 GLOBAL Account 管理接口。 */
@RestController
@RequestMapping("/mg/platform/accounts")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformAccountController {

    private final AccountService accountService;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountSecurityService accountSecurityService;
    private final AccountAccessPolicy accountAccessPolicy;
    private final AuthenticationBrowser authenticationBrowser;
    private final ManagedAccountProfileService managedAccountProfileService;
    private final AccountLifecycleService accountLifecycleService;
    private final DefaultProfileService defaultProfileService;

    /** 分页查询 GLOBAL Account，不返回 Principal、角色或认证秘密。 */
    @GetMapping
    @RequirePermission(PlatformPermissions.ACCOUNTS_READ)
    public @NotNull ApiResponse<PageData<ManagedAccountView>> page(@NotNull AccountPageRequest request) {
        return ApiResponse.ok(accountService.page(
                AccountScopeType.GLOBAL, null, request.getSearch(), request.getStatus(), request.page(), request.size())
                .map(ManagedAccountView::from));
    }

    /** 读取 GLOBAL Account 对应的通用资料。 */
    @GetMapping("/{accountId}/profile")
    @RequirePermission(PlatformPermissions.ACCOUNTS_READ)
    public @NotNull ApiResponse<ManagedAccountProfileView> profile(
            @PathVariable @NotNull Long accountId, @AuthPrincipal @NotNull Principal principal) {
        var account = requireGlobalAccount(accountId, principal);
        return ApiResponse.ok(ManagedAccountProfileView.from(managedAccountProfileService.profile(account.getId())));
    }

    /** 使用 STEP_UP 修改 GLOBAL Account 对应的显示名称和联系方式。 */
    @PutMapping("/{accountId}/profile")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.UPDATE, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    @RequirePermission(PlatformPermissions.ACCOUNTS_UPDATE)
    public @NotNull ApiResponse<Void> updateProfile(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull ManagedAccountProfileUpdateRequest body,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = requireGlobalAccount(accountId, principal);
        managedAccountProfileService.update(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                account.getSubjectId(),
                body.getDisplayName(),
                body.getPhone(),
                body.getEmail(),
                body.getSubjectVersion(),
                authenticationId,
                authenticationBrowser.require(request));
        return ApiResponse.ok();
    }

    /** 替换 GLOBAL Account 的默认头像。 */
    @PutMapping("/{accountId}/profile/avatar")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.UPDATE, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    @RequirePermission(PlatformPermissions.ACCOUNTS_UPDATE)
    public @NotNull ApiResponse<Void> replaceAvatar(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull DefaultProfileAvatarUpdateRequest body,
            @AuthPrincipal @NotNull Principal principal) {
        var account = requireGlobalAccount(accountId, principal);
        defaultProfileService.replaceAvatar(
                account.getSubjectId(),
                body.getFileId(),
                body.getUploadGrant(),
                body.getProfileVersion(),
                principal.requirePrincipalId());
        return ApiResponse.ok();
    }

    /** 删除 GLOBAL Account 的默认头像。 */
    @DeleteMapping("/{accountId}/profile/avatar")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.UPDATE, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    @RequirePermission(PlatformPermissions.ACCOUNTS_UPDATE)
    public @NotNull ApiResponse<Void> removeAvatar(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull DefaultProfileAvatarDeleteRequest body,
            @AuthPrincipal @NotNull Principal principal) {
        var account = requireGlobalAccount(accountId, principal);
        defaultProfileService.removeAvatar(account.getSubjectId(), body.getProfileVersion());
        return ApiResponse.ok();
    }

    /** 修改 GLOBAL Account 是否允许登录。 */
    @PatchMapping("/{accountId}")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.CHANGE_STATUS, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    @RequirePermission(PlatformPermissions.ACCOUNTS_UPDATE)
    public @NotNull ApiResponse<Void> changeStatus(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull ManagedAccountStatusRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @AuthPrincipal @NotNull Principal principal) {
        requireGlobalAccount(accountId, principal);
        accountLifecycleService.changeStatus(
                accountId,
                body.getStatus(),
                IfMatchVersion.require(ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED));
        return ApiResponse.ok();
    }

    /** 删除 GLOBAL Account；仍承载有效授权入口时拒绝。 */
    @DeleteMapping("/{accountId}")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.DELETE, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    @RequirePermission(PlatformPermissions.ACCOUNTS_DELETE)
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @AuthPrincipal @NotNull Principal principal) {
        requireGlobalAccount(accountId, principal);
        accountLifecycleService.delete(
                accountId,
                IfMatchVersion.require(ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED));
        return ResponseEntity.noContent().build();
    }

    /** 读取一个 GLOBAL Account 的认证方式、MFA 和恢复码摘要。 */
    @GetMapping("/{accountId}/security")
    @RequirePermission(AccountSecurityPermissions.SECURITY_READ)
    public @NotNull ApiResponse<AccountSecurityView> security(
            @PathVariable @NotNull Long accountId, @AuthPrincipal @NotNull Principal principal) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
        return ApiResponse.ok(AccountSecurityView.from(accountSecurityService.security(accountId)));
    }

    /** 读取一个 GLOBAL Account 的活跃 Session，不返回 Token。 */
    @GetMapping("/{accountId}/sessions")
    @RequirePermission(AccountSecurityPermissions.SESSIONS_READ)
    public @NotNull ApiResponse<List<AccountSessionView>> sessions(
            @PathVariable @NotNull Long accountId, @AuthPrincipal @NotNull Principal principal) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
        return ApiResponse.ok(accountSecurityService.managedSessions(accountId).stream()
                .map(AccountSessionView::from)
                .toList());
    }

    /**
     * 以当前操作账号的 STEP_UP 修改目标 Account 的 MFA 要求并重签或撤销恢复码。
     *
     * <p>Account 版本使用强 If-Match，认证材料状态变化只终止目标 Account 的 Session。
     */
    @PutMapping("/{accountId}/second-factor/requirement")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.RESET_MFA, targetType = "account", targetId = "#accountId", payload = "T(java.util.Map).of('required', #body.required)")
    @RequirePermission(AccountSecurityPermissions.MFA_RESET)
    public @NotNull ApiResponse<AccountSecondFactorView> changeSecondFactorRequirement(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull AccountSecondFactorRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (body.getVersion() != null && body.getVersion() != version) {
            throw new BizException(IamErrorCode.ACCOUNT_CHANGED);
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

    /** 使用当前操作账号的 STEP_UP 为 GLOBAL Account 重签恢复码并终止其全部 Session。 */
    @PostMapping("/{accountId}/recovery-codes")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.REISSUE_RECOVERY_CODES, targetType = "account", targetId = "#accountId")
    @RequirePermission(AccountSecurityPermissions.RECOVERY_MANAGE)
    public @NotNull ApiResponse<AccountSecondFactorView> reissueRecoveryCodes(
            @PathVariable @NotNull Long accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
        return ApiResponse.ok(AccountSecondFactorView.from(accountSecurityService.reissueManagedRecoveryCodes(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                IfMatchVersion.require(ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED),
                authenticationId,
                authenticationBrowser.require(request))));
    }

    /** 使用当前操作账号的 STEP_UP 为 GLOBAL Account 绑定 PASSWORD Credential。 */
    @PostMapping("/{accountId}/credentials/password")
    @Idempotent(operation = "platform.accounts.credentials.bind-password")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.BIND_PASSWORD, targetType = "account", targetId = "#accountId")
    @RequirePermission(AccountSecurityPermissions.CREDENTIALS_MANAGE)
    public @NotNull ApiResponse<Long> bindPassword(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull AccountPasswordCredentialBindRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (!Long.valueOf(version).equals(account.getVersion())) {
            throw new BizException(IamErrorCode.ACCOUNT_CHANGED);
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

    /** 使用当前操作账号的 STEP_UP 和 Credential If-Match 重置 GLOBAL Account 密码。 */
    @PutMapping("/{accountId}/credentials/{credentialId}/password")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.REPLACE_PASSWORD, targetType = "credential", targetId = "#credentialId")
    @RequirePermission(AccountSecurityPermissions.CREDENTIALS_MANAGE)
    public @NotNull ApiResponse<Void> replacePassword(
            @PathVariable @NotNull Long accountId,
            @PathVariable @NotNull Long credentialId,
            @Valid @RequestBody @NotNull AccountPasswordCredentialReplaceRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
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

    /** 使用操作账号的 STEP_UP 撤销 GLOBAL Account 的指定 Session。 */
    @DeleteMapping("/{accountId}/sessions/{sessionId}")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.REVOKE_SESSION, targetType = "account", targetId = "#accountId", payload = "T(java.util.Map).of('sessionId', #sessionId)")
    @RequirePermission(AccountSecurityPermissions.SESSIONS_REVOKE)
    public @NotNull ResponseEntity<Void> endSession(
            @PathVariable @NotNull Long accountId,
            @PathVariable @BizId(max = 128) @NotNull String sessionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (!Long.valueOf(version).equals(account.getVersion())) {
            throw new BizException(IamErrorCode.ACCOUNT_CHANGED);
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

    /** 使用操作账号的 STEP_UP 撤销 GLOBAL Account 的全部 Session。 */
    @DeleteMapping("/{accountId}/sessions")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.REVOKE_SESSIONS, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    @RequirePermission(AccountSecurityPermissions.SESSIONS_REVOKE)
    public @NotNull ResponseEntity<Void> endSessions(
            @PathVariable @NotNull Long accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthPrincipal @NotNull Principal principal,
            @AuthSubject @NotNull Subject authenticatedSubject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        var account = requireGlobalAccount(accountId, principal);
        long version = IfMatchVersion.require(
                ifMatch, IamErrorCode.ACCOUNT_IF_MATCH_REQUIRED, IamErrorCode.ACCOUNT_CHANGED);
        if (!Long.valueOf(version).equals(account.getVersion())) {
            throw new BizException(IamErrorCode.ACCOUNT_CHANGED);
        }
        accountSecurityService.endManagedSessions(
                authenticatedSubject,
                authenticatedAccount,
                accountId,
                authenticationId,
                authenticationBrowser.require(request));
        return ResponseEntity.noContent().build();
    }

    /** 创建新的 GLOBAL Account，不创建 PLATFORM Principal。 */
    @PostMapping
    @Idempotent(operation = "platform.accounts.create")
    @Audit(module = PlatformAuditOperations.ACCOUNTS, action = PlatformAuditOperations.CREATE, targetType = "account", targetId = "#result?.data")
    @RequirePermission(PlatformPermissions.ACCOUNTS_CREATE)
    public @NotNull ApiResponse<Long> create(@Valid @RequestBody @NotNull GlobalAccountCreateRequest request) {
        return ApiResponse.created(accountProvisioningService.create(
                request.getIdentifier(), request.credential(), AccountScopeType.GLOBAL, null,
                request.getDisplayName(), request.getPhone(), request.getEmail(), null, null, null)
                .getAccount().getId());
    }

    private @NotNull com.sca4d.entity.IamAccountEntity requireGlobalAccount(
            @NotNull Long accountId, @NotNull Principal principal) {
        var account = accountService.require(accountId);
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        accountAccessPolicy.require(principal, account);
        return account;
    }
}
