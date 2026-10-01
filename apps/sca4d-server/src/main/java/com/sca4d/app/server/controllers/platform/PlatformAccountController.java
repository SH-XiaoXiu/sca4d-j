package com.sca4d.app.server.controllers.platform;

import com.sca4d.app.server.audit.ApplicationAuditValues;
import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.RequireAuthentication;
import com.sca4d.app.server.platform.PlatformAccountRecord;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.platform.request.CreatePlatformAccountRequest;
import com.sca4d.app.server.platform.request.ReplaceAccountRolesRequest;
import com.sca4d.app.server.platform.request.ReplaceAvatarRequest;
import com.sca4d.app.server.platform.request.UpdatePlatformAccountRequest;
import com.sca4d.app.server.platform.view.AccountRolesView;
import com.sca4d.app.server.platform.view.PlatformAccountView;
import com.sca4d.app.server.service.PlatformAccountApplicationService;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditPayload;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.CurrentSession;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.core.spring.web.annotation.PageOptions;
import com.sca4d.iam.base.PlatformAccountQuery;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.base.rbac.PlatformOperations;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.rbac.PrincipalRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

/** 管理示例 App 的 Platform Account、资料摘要和角色关系。 */
@RestController
@RequestMapping("/platform/accounts")
@Tag(name = "Platform Accounts")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class PlatformAccountController {

    private final @NotNull PlatformAccountApplicationService accounts;

    /**
     * 创建 Platform Account 管理入口。
     *
     * @param accounts Platform Account 应用服务
     */
    public PlatformAccountController(@NotNull PlatformAccountApplicationService accounts) {
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 应用服务不能为空");
    }

    /**
     * 查询一页 Platform Account。
     *
     * @param search 可选用户名搜索词
     * @param status 可选账号状态
     * @param page 从 1 开始且每页数量不超过 100 的分页要求
     * @return 当前页账号和完整命中数量
     */
    @GetMapping
    @Operation(summary = "查询 Platform Account")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_READ_VALUE)
    public @NotNull ApiResponse<Page<PlatformAccountView>> find(
            @RequestParam(required = false) @Nullable String search,
            @RequestParam(required = false) @Nullable IdentityStatus status,
            @PageOptions(maxSize = 100) @NotNull PageRequest page) {
        return ApiResponse.ok(
                accounts.find(new PlatformAccountQuery(search, status, page))
                        .map(PlatformAccountView::from));
    }

    /**
     * 创建完整 Platform Account。
     *
     * @param request 用户名、初始密码和 App 资料
     * @return 新账号和 Location
     */
    @PostMapping
    @Operation(summary = "创建 Platform Account")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE_VALUE)
    @Audit(operation = "PLATFORM/ACCOUNT/CREATE", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<PlatformAccountView> create(
            @RequestBody @NotNull @AuditPayload CreatePlatformAccountRequest request) {
        PlatformAccountRecord created = accounts.create(
                request.getUsername(),
                request.getInitialPassword(),
                request.getDisplayName(),
                request.getEmail(),
                request.getPhoneNumber());
        long accountId = created.account().accountId();
        return ApiResponse.created(
                PlatformAccountView.from(created),
                URI.create("/platform/accounts/" + accountId));
    }

    /**
     * 查询 Platform Account 详情。
     *
     * @param accountId Account 标识
     * @return Account 详情
     */
    @GetMapping("/{accountId}")
    @Operation(summary = "查询 Platform Account 详情")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_READ_VALUE)
    public @NotNull ApiResponse<PlatformAccountView> get(@PathVariable long accountId) {
        PlatformAccountRecord result = accounts.loadByAccountId(accountId);
        return ApiResponse.ok(PlatformAccountView.from(result));
    }

    /**
     * 修改 Platform Account 状态。
     *
     * @param accountId 目标 Account 标识
     * @param request 新状态
     * @param currentAccountId 当前 Account 标识，用于禁止停用自己
     * @return 修改后的 Account
     */
    @PatchMapping("/{accountId}")
    @Operation(summary = "修改 Platform Account")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE_VALUE)
    @Audit(operation = "PLATFORM/ACCOUNT/STATUS/CHANGE", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<PlatformAccountView> update(
            @PathVariable @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) long accountId,
            @RequestBody @NotNull @AuditPayload UpdatePlatformAccountRequest request,
            @CurrentAccount @NotNull Long currentAccountId) {
        PlatformAccountRecord updated = accounts.changeStatus(
                accountId,
                request.getStatus(),
                currentAccountId);
        return ApiResponse.ok(PlatformAccountView.from(updated));
    }

    /**
     * 查询 Platform Account 的角色关系。
     *
     * @param accountId Account 标识
     * @return 当前角色和 ETag
     */
    @GetMapping("/{accountId}/roles")
    @Operation(summary = "查询 Platform Account 角色")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_READ_VALUE)
    public @NotNull ApiResponse<AccountRolesView> findRoles(@PathVariable long accountId) {
        PrincipalRoles result = accounts.findRoles(accountId);
        return ApiResponse.ok(AccountRolesView.from(accountId, result));
    }

    /**
     * 原子替换 Platform Account 角色。
     *
     * @param accountId Account 标识
     * @param request 新角色集合
     * @return 替换后的角色和新 ETag
     */
    @PutMapping("/{accountId}/roles")
    @IfMatch
    @Operation(summary = "替换 Platform Account 角色")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE_VALUE)
    @Audit(operation = "PLATFORM/ACCOUNT/ROLES/REPLACE", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<AccountRolesView> replaceRoles(
            @PathVariable @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) long accountId,
            @RequestBody @NotNull @AuditPayload ReplaceAccountRolesRequest request) {
        PrincipalRoles result = accounts.replaceRoles(
                accountId,
                request.getRoleIds());
        return ApiResponse.ok(AccountRolesView.from(accountId, result));
    }

    /**
     * 把当前管理员上传完成的文件绑定为目标账号头像。
     *
     * @param accountId 目标 Account 标识
     * @param request 头像文件标识
     * @param currentPrincipalId 当前 Platform Principal 标识
     * @return 修改后的 Platform Account
     */
    @PutMapping("/{accountId}/profile/avatar")
    @Operation(summary = "替换 Platform Account 头像")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE_VALUE)
    @Audit(operation = "PLATFORM/ACCOUNT/AVATAR/REPLACE", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<PlatformAccountView> replaceAvatar(
            @PathVariable @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) long accountId,
            @RequestBody @NotNull @AuditPayload ReplaceAvatarRequest request,
            @CurrentPrincipal @NotNull Long currentPrincipalId) {
        PlatformAccountRecord updated = accounts.replaceAvatar(
                accountId,
                new PlatformPrincipal(currentPrincipalId),
                request.getFileId());
        return ApiResponse.ok(PlatformAccountView.from(updated));
    }

    /**
     * 删除目标账号头像。
     *
     * @param accountId 目标 Account 标识
     * @return 修改后的 Platform Account
     */
    @DeleteMapping("/{accountId}/profile/avatar")
    @Operation(summary = "删除 Platform Account 头像")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE_VALUE)
    @Audit(operation = "PLATFORM/ACCOUNT/AVATAR/DELETE")
    public @NotNull ApiResponse<PlatformAccountView> deleteAvatar(
            @PathVariable @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) long accountId) {
        PlatformAccountRecord updated = accounts.deleteAvatar(accountId);
        return ApiResponse.ok(PlatformAccountView.from(updated));
    }

    /**
     * 使用一次性动作认证终态删除 Platform Account。
     *
     * @param accountId 目标 Account 标识
     * @param session 当前 Session，用于禁止删除自己
     * @return 空成功响应
     */
    @DeleteMapping("/{accountId}")
    @RequireSessionToken
    @RequireAuthentication(AuthenticationActions.ACCOUNT_DELETE_CODE)
    @Operation(summary = "删除 Platform Account")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE_VALUE)
    @Audit(operation = "PLATFORM/ACCOUNT/DELETE")
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) long accountId,
            @CurrentSession @NotNull Session session) {
        accounts.delete(accountId, session.account().accountId());
        return ApiResponse.noContent();
    }
}
