package com.sca4d.controllers;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthAccount;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.iam.account.AccountSecurityService;
import com.sca4d.iam.account.request.AccountSecondFactorRequest;
import com.sca4d.iam.account.view.AccountSecondFactorView;
import com.sca4d.iam.account.view.AccountSecurityView;
import com.sca4d.iam.account.view.AccountSessionView;
import com.sca4d.iam.resource.IamResourceTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前登录 Subject 的 Account、认证入口和登录 Session 自助接口。 */
@RestController
@RequestMapping("/me/accounts")
@AuthPrincipal
@RequiredArgsConstructor
public class AccountSecurityController {

    private final AccountSecurityService service;
    private final AuthenticationBrowser authenticationBrowser;

    /**
     * 查询当前 Subject 名下全部 Account 及其认证入口。
     *
     * @param subject 当前登录 Subject
     * @return 按 Account 主键排序的安全表示
     */
    @GetMapping
    public @NotNull ApiResponse<List<AccountSecurityView>> accounts(
            @AuthSubject @NotNull Subject subject) {
        return ApiResponse.ok(service.accounts(subject.getSubjectId()).stream()
                .map(AccountSecurityView::from)
                .toList());
    }

    /**
     * 使用 STEP_UP 修改一个 Account 的第二因子要求并结束其全部旧登录 Session。
     *
     * @param accountId 当前 Subject 名下的目标 Account
     * @param authenticationId 已验证 STEP_UP 认证 UUID
     * @param body 目标状态和当前 Account 版本
     * @param subject 当前 Session 固定的 Subject
     * @param authenticatedAccount 当前 Session 固定的 Account
     * @param request 提供浏览器绑定 Cookie 的请求
     * @return 修改结果；启用时包含只展示一次的新恢复码
     */
    @PutMapping("/{accountId}/second-factor/requirement")
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.SECOND_FACTOR_REQUIREMENT, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId", payload = "T(java.util.Map).of('required', #body.required)")
    public @NotNull ApiResponse<AccountSecondFactorView> changeSecondFactorRequirement(
            @PathVariable @NotNull Long accountId,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @Valid @RequestBody @NotNull AccountSecondFactorRequest body,
            @AuthSubject @NotNull Subject subject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        return ApiResponse.ok(AccountSecondFactorView.from(service.changeSecondFactorRequirement(
                subject,
                authenticatedAccount,
                accountId,
                body.getRequired(),
                body.getVersion(),
                authenticationId,
                authenticationBrowser.require(request))));
    }

    /**
     * 查询当前 Subject 名下指定 Account 的登录 Session。
     *
     * @param accountId Account 主键
     * @param subject 当前登录 Subject
     * @return 不包含 Token 的登录 Session
     */
    @GetMapping("/{accountId}/sessions")
    public @NotNull ApiResponse<List<AccountSessionView>> sessions(
            @PathVariable @NotNull Long accountId,
            @AuthSubject @NotNull Subject subject) {
        return ApiResponse.ok(service.sessions(subject.getSubjectId(), accountId).stream()
                .map(AccountSessionView::from)
                .toList());
    }

    /**
     * 使用 STEP_UP 认证 撤销当前 Subject 名下的一个登录 Session。
     *
     * @param accountId 目标 Session 所属 Account
     * @param sessionId 不能用于认证的项目 Session 标识
     * @param authenticationId 已验证 STEP_UP 认证 UUID
     * @param subject 当前 Session 固定的 Subject
     * @param authenticatedAccount 当前 Session 固定的 Account
     * @param request 提供浏览器绑定 Cookie 的请求
     * @return 204 无响应体
     */
    @DeleteMapping("/{accountId}/sessions/{sessionId}")
    @Audit(module = AuthenticationAuditOperations.SESSIONS, action = AuthenticationAuditOperations.REVOKE, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId", payload = "T(java.util.Map).of('sessionId', #sessionId)")
    public @NotNull ResponseEntity<Void> endSession(
            @PathVariable @NotNull Long accountId,
            @PathVariable @BizId(max = 128) @NotNull String sessionId,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthSubject @NotNull Subject subject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        service.endSession(
                subject,
                authenticatedAccount,
                accountId,
                sessionId,
                authenticationId,
                authenticationBrowser.require(request));
        return ResponseEntity.noContent().build();
    }
}
