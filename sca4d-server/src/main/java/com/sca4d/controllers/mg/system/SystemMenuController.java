package com.sca4d.controllers.mg.system;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.system.MenuService;
import com.sca4d.system.SystemClientStateTargets;
import com.sca4d.system.SystemErrorCode;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.SystemResourceTypes;
import com.sca4d.system.audit.SystemAuditOperations;
import com.sca4d.system.request.MenuCreateRequest;
import com.sca4d.system.request.MenuDeleteRequest;
import com.sca4d.system.request.MenuReorderRequest;
import com.sca4d.system.request.MenuUpdateRequest;
import com.sca4d.system.view.ManagedMenuView;
import com.sca4d.system.view.NavigationRouteView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;

/** 平台系统管理端的共用菜单树和注册路由管理接口。 */
@RestController
@RequestMapping("/mg/system/menus")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class SystemMenuController {

    private final MenuService service;
    private final LocalizedMessages messages;

    /**
     * 读取包含隐藏节点和版本的完整平台菜单树。
     *
     * @return 按数据库同级排序排列的管理菜单树
     */
    @GetMapping
    @RequirePermission(SystemPermissions.MENUS_READ)
    public @NotNull ApiResponse<List<ManagedMenuView>> menus() {
        return ApiResponse.ok(service.menus().stream().map(ManagedMenuView::from).toList());
    }

    /**
     * 读取菜单可以引用的代码注册前端路由。
     *
     * @return 路由编码、路径模板、Principal 范围、权限和默认展示元数据
     */
    @GetMapping("/route-options")
    @RequirePermission(SystemPermissions.MENUS_READ)
    public @NotNull ApiResponse<List<NavigationRouteView>> routeOptions() {
        return ApiResponse.ok(service.routes().stream()
                .map(route -> NavigationRouteView.from(route, messages))
                .toList());
    }

    /**
     * 创建结构分组或注册路由菜单。
     *
     * @param request 父关系、路由编码、多语言标题、图标和显隐
     * @return 新菜单主键
     */
    @PostMapping
    @Idempotent(operation = "system.menus.create")
    @Audit(module = SystemAuditOperations.MENUS, action = SystemAuditOperations.CREATE, targetType = SystemResourceTypes.MENU, targetId = "#result?.data")
    @RequirePermission(SystemPermissions.MENUS_MANAGE)
    public @NotNull ApiResponse<Long> create(
            @Valid @RequestBody @NotNull MenuCreateRequest request) {
        return ApiResponse.created(service.create(
                request.getParentId(),
                request.getRouteCode(),
                request.getTitles(),
                request.getIcon(),
                request.getVisible()));
    }

    /**
     * 修改一个菜单的父关系、代码路由引用和展示数据。
     *
     * @param menuId 菜单固定主键
     * @param request 新菜单数据和当前版本
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/{menuId}")
    @Audit(module = SystemAuditOperations.MENUS, action = SystemAuditOperations.UPDATE, targetType = SystemResourceTypes.MENU, targetId = "#menuId")
    @RequirePermission(SystemPermissions.MENUS_MANAGE)
    public @NotNull ApiResponse<Void> update(
            @PathVariable @NotNull Long menuId,
            @Valid @RequestBody @NotNull MenuUpdateRequest request) {
        service.update(
                menuId,
                request.getParentId(),
                request.getRouteCode(),
                request.getTitles(),
                request.getIcon(),
                request.getVisible(),
                request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 删除一个菜单及其全部下级节点。
     *
     * @param menuId 待删除子树根菜单主键
     * @param request 根菜单当前版本和幂等键
     * @return 没有响应数据的成功结果
     */
    @DeleteMapping("/{menuId}")
    @Idempotent(operation = "system.menus.delete")
    @Audit(module = SystemAuditOperations.MENUS, action = SystemAuditOperations.DELETE, targetType = SystemResourceTypes.MENU, targetId = "#menuId")
    @RequirePermission(SystemPermissions.MENUS_MANAGE)
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @NotNull Long menuId,
            @Valid @RequestBody @NotNull MenuDeleteRequest request) {
        service.delete(menuId, request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 原子保存一个父节点下全部直接子菜单的拖拽顺序。
     *
     * @param request 父节点和完整兄弟菜单版本序列
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/order")
    @Audit(module = SystemAuditOperations.MENUS, action = SystemAuditOperations.REORDER, targetType = "menu-order")
    @RequirePermission(SystemPermissions.MENUS_MANAGE)
    public @NotNull ApiResponse<Void> reorder(@Valid @RequestBody @NotNull MenuReorderRequest request) {
        LinkedHashMap<Long, Long> menuVersions = new LinkedHashMap<>();
        for (MenuReorderRequest.Item item : request.getItems()) {
            if (menuVersions.putIfAbsent(item.getMenuId(), item.getVersion()) != null) {
                throw new ConcurrencyConflictException(SystemErrorCode.MENU_CHANGED, SystemClientStateTargets.MENUS);
            }
        }
        service.reorder(request.getParentId(), menuVersions);
        return ApiResponse.ok();
    }
}
