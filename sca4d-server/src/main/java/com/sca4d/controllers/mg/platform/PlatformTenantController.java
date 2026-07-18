package com.sca4d.controllers.mg.platform;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.platform.audit.PlatformAuditOperations;
import com.sca4d.tenant.TenantService;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import com.sca4d.tenant.request.TenantCreateRequest;
import com.sca4d.tenant.request.TenantDeleteRequest;
import com.sca4d.tenant.request.TenantPageRequest;
import com.sca4d.tenant.request.TenantStatusRequest;
import com.sca4d.tenant.request.TenantUpdateRequest;
import com.sca4d.tenant.view.TenantView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台租户管理接口。
 */
@RestController
@RequestMapping("/mg/platform/tenants")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantService tenantService;

    /**
     * 分页查询平台可管理的租户。
     *
     * @param request 分页和筛选条件
     * @return 租户展示对象分页
     */
    @GetMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ)
    @RequirePermission(PlatformPermissions.TENANTS_READ)
    public @NotNull ApiResponse<PageData<TenantView>> page(@NotNull TenantPageRequest request) {
        return ApiResponse.ok(tenantService.page(request.getSearch(), request.getStatus(), request.page(), request.size())
                .map(TenantView::from));
    }

    /**
     * 查询一个租户的管理端资料。
     *
     * @param tenantId 租户主键
     * @return 租户展示对象
     */
    @GetMapping("/{tenantId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANTS_READ)
    public @NotNull ApiResponse<TenantView> detail(@PathVariable @NotNull Long tenantId) {
        return ApiResponse.ok(TenantView.from(tenantService.require(tenantId)));
    }

    /**
     * 创建具有固定编码的租户。
     *
     * @param request 租户编码、名称和类型
     * @return 新建租户主键
     */
    @PostMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE)
    @Idempotent(operation = "platform.tenants.create")
    @Audit(module = PlatformAuditOperations.TENANTS, action = PlatformAuditOperations.CREATE, targetType = "tenant", targetId = "#result?.data")
    @RequirePermission(PlatformPermissions.TENANTS_CREATE)
    public @NotNull ApiResponse<Long> create(@Valid @RequestBody @NotNull TenantCreateRequest request) {
        return ApiResponse.created(tenantService.create(request.getCode(), request.getName(), request.getTenantType()));
    }

    /**
     * 更新租户资料。
     *
     * @param tenantId 租户主键
     * @param request 待写入的租户编码、名称和乐观锁版本
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/{tenantId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANTS, action = PlatformAuditOperations.UPDATE, targetType = "tenant", targetId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANTS_UPDATE)
    public @NotNull ApiResponse<Void> update(
            @PathVariable @NotNull Long tenantId, @Valid @RequestBody @NotNull TenantUpdateRequest request) {
        tenantService.update(tenantId, request.getCode(), request.getName(), request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 修改租户自身状态，不修改租户成员 Principal。
     *
     * @param tenantId 租户主键
     * @param request 目标状态和当前版本
     * @return 没有响应数据的成功结果
     */
    @PatchMapping("/{tenantId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANTS, action = PlatformAuditOperations.CHANGE_STATUS, targetType = "tenant", targetId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANTS_UPDATE)
    public @NotNull ApiResponse<Void> changeStatus(
            @PathVariable @NotNull Long tenantId, @Valid @RequestBody @NotNull TenantStatusRequest request) {
        tenantService.changeStatus(tenantId, request.getStatus(), request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 将租户同步隔离并登记集群异步关系清理任务。
     *
     * @param tenantId 租户主键
     * @param request 当前版本和本次删除意图的幂等键
     * @return 没有响应数据的成功结果
     */
    @DeleteMapping("/{tenantId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Idempotent(operation = "platform.tenants.delete")
    @Audit(module = PlatformAuditOperations.TENANTS, action = PlatformAuditOperations.DELETE, targetType = "tenant", targetId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANTS_DELETE)
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @NotNull Long tenantId,
            @Valid @RequestBody @NotNull TenantDeleteRequest request) {
        tenantService.delete(tenantId, request.getVersion());
        return ApiResponse.ok();
    }
}
