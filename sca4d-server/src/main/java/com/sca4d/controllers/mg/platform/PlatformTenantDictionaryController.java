package com.sca4d.controllers.mg.platform;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.platform.audit.PlatformAuditOperations;
import com.sca4d.system.DictionaryService;
import com.sca4d.system.SystemClientStateTargets;
import com.sca4d.system.SystemErrorCode;
import com.sca4d.system.request.DictionaryCreateRequest;
import com.sca4d.system.request.DictionaryDeleteRequest;
import com.sca4d.system.request.DictionaryItemCreateRequest;
import com.sca4d.system.request.DictionaryItemPageRequest;
import com.sca4d.system.request.DictionaryItemUpdateRequest;
import com.sca4d.system.request.DictionaryOptionsRequest;
import com.sca4d.system.request.DictionaryPageRequest;
import com.sca4d.system.request.DictionaryReorderRequest;
import com.sca4d.system.request.DictionaryUpdateRequest;
import com.sca4d.system.view.DictionaryOrderItemView;
import com.sca4d.system.view.DictionarySelectionView;
import com.sca4d.system.view.ManagedDictionaryItemView;
import com.sca4d.system.view.ManagedDictionaryView;
import com.sca4d.tenant.policy.TenantAccessPolicy;
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

/** 平台管理员管理指定租户私有字典并读取其可见选项的接口。 */
@RestController
@RequestMapping("/mg/platform/tenants/{tenantId}/dictionaries")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformTenantDictionaryController {

    private final DictionaryService service;

    /**
     * 分页读取指定租户的私有字典管理列表。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param request 分页、编码搜索和有限状态筛选
     * @return 包含 ID、排序和版本的租户私有字典分页
     */
    @GetMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_READ)
    public @NotNull ApiResponse<PageData<ManagedDictionaryView>> page(
            @PathVariable @NotNull Long tenantId,
            @Valid @NotNull DictionaryPageRequest request) {
        return ApiResponse.ok(service
                .pageManagedTypes(
                        tenantId,
                        request.getSearch(),
                        request.getKind(),
                        request.getStatus(),
                        request.page(),
                        request.size())
                .map(ManagedDictionaryView::from));
    }

    /**
     * 代指定租户创建业务编码或输入建议字典。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param request 固定编码、含义类型和多语言名称
     * @return 新建租户私有字典类型主键
     */
    @PostMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Idempotent(operation = "platform.tenant-dictionaries.create")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.CREATE, targetType = "tenant", targetId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Long> create(
            @PathVariable @NotNull Long tenantId,
            @Valid @RequestBody @NotNull DictionaryCreateRequest request) {
        return ApiResponse.created(service.createType(
                tenantId,
                request.getCode(),
                request.getKind(),
                request.getLabels()));
    }

    /**
     * 读取指定租户的一个私有字典详情。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有字典类型主键
     * @return 包含原始多语言内容、排序和版本的字典视图
     */
    @GetMapping("/{dictionaryId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_READ)
    public @NotNull ApiResponse<ManagedDictionaryView> detail(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId) {
        return ApiResponse.ok(ManagedDictionaryView.from(service.requireManagedType(tenantId, dictionaryId)));
    }

    /**
     * 代指定租户修改私有可配置字典的名称和状态。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有字典类型主键
     * @param request 多语言名称、状态和当前版本
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/{dictionaryId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.UPDATE, targetType = "dictionary", targetId = "#dictionaryId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Void> update(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId,
            @Valid @RequestBody @NotNull DictionaryUpdateRequest request) {
        service.updateType(tenantId, dictionaryId, request.getLabels(), request.getStatus(), request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 代指定租户删除私有可配置字典；业务编码仍被引用时拒绝删除。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有字典类型主键
     * @param request 当前版本和幂等键
     * @return 没有响应数据的成功结果
     */
    @DeleteMapping("/{dictionaryId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Idempotent(operation = "platform.tenant-dictionaries.delete")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.DELETE, targetType = "dictionary", targetId = "#dictionaryId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId,
            @Valid @RequestBody @NotNull DictionaryDeleteRequest request) {
        service.deleteType(tenantId, dictionaryId, request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 分页读取指定租户的一个私有字典中的字典项。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有字典类型主键
     * @param request 分页、固定值搜索和状态筛选
     * @return 包含 ID、排序和版本的字典项分页
     */
    @GetMapping("/{dictionaryId}/items")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_READ)
    public @NotNull ApiResponse<PageData<ManagedDictionaryItemView>> items(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId,
            @Valid @NotNull DictionaryItemPageRequest request) {
        return ApiResponse.ok(service
                .pageManagedItems(
                        tenantId,
                        dictionaryId,
                        request.getSearch(),
                        request.getStatus(),
                        request.page(),
                        request.size())
                .map(ManagedDictionaryItemView::from));
    }

    /**
     * 代指定租户在可配置字典中创建业务编码项或输入建议项。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有父字典主键
     * @param request 固定值或建议文本、标签和含义色调
     * @return 新建字典项主键
     */
    @PostMapping("/{dictionaryId}/items")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Idempotent(operation = "platform.tenant-dictionary-items.create")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.CREATE_ITEM, targetType = "dictionary", targetId = "#dictionaryId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Long> createItem(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId,
            @Valid @RequestBody @NotNull DictionaryItemCreateRequest request) {
        return ApiResponse.created(service.createItem(
                tenantId,
                dictionaryId,
                request.getValue(),
                request.getLabels(),
                request.getTone(),
                request.getSelectable()));
    }

    /**
     * 代指定租户修改私有可配置字典项的内容和状态。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有父字典主键
     * @param itemId 租户私有字典项主键
     * @param request 字典项内容、状态和当前版本
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/{dictionaryId}/items/{itemId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.UPDATE_ITEM, targetType = "dictionary-item", targetId = "#itemId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Void> updateItem(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId,
            @PathVariable @NotNull Long itemId,
            @Valid @RequestBody @NotNull DictionaryItemUpdateRequest request) {
        service.updateItem(
                tenantId,
                dictionaryId,
                itemId,
                request.getValue(),
                request.getLabels(),
                request.getTone(),
                request.getSelectable(),
                request.getStatus(),
                request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 代指定租户删除私有可配置字典项；被引用的业务编码只能停用。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有父字典主键
     * @param itemId 租户私有字典项主键
     * @param request 当前版本和幂等键
     * @return 没有响应数据的成功结果
     */
    @DeleteMapping("/{dictionaryId}/items/{itemId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Idempotent(operation = "platform.tenant-dictionary-items.delete")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.DELETE_ITEM, targetType = "dictionary-item", targetId = "#itemId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Void> deleteItem(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId,
            @PathVariable @NotNull Long itemId,
            @Valid @RequestBody @NotNull DictionaryDeleteRequest request) {
        service.deleteItem(tenantId, dictionaryId, itemId, request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 批量读取指定租户私有字典与平台全局字典的共用选择项。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param request 最多 50 个固定字典类型编码
     * @return 按数据库展示顺序排列的本地化字典
     */
    @GetMapping("/options")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_READ)
    public @NotNull ApiResponse<List<DictionarySelectionView>> options(
            @PathVariable @NotNull Long tenantId, @Valid @NotNull DictionaryOptionsRequest request) {
        return ApiResponse.ok(service.selections(tenantId, request.getCodes()).stream()
                .map(DictionarySelectionView::from)
                .toList());
    }

    /**
     * 读取指定租户全部私有字典类型的当前拖拽顺序。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @return 只包含展示、拖拽和乐观提交字段的完整顺序
     */
    @GetMapping("/order")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_READ)
    public @NotNull ApiResponse<List<DictionaryOrderItemView>> typeOrder(
            @PathVariable @NotNull Long tenantId) {
        return ApiResponse.ok(service.typeOrder(tenantId).stream()
                .map(DictionaryOrderItemView::from)
                .toList());
    }

    /**
     * 读取指定租户一个私有字典中全部字典项的当前拖拽顺序。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有字典类型主键
     * @return 只包含展示、拖拽和乐观提交字段的完整顺序
     */
    @GetMapping("/{dictionaryId}/items/order")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_READ)
    public @NotNull ApiResponse<List<DictionaryOrderItemView>> itemOrder(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId) {
        return ApiResponse.ok(service.itemOrder(tenantId, dictionaryId).stream()
                .map(DictionaryOrderItemView::from)
                .toList());
    }

    /**
     * 修改指定租户私有字典类型展示排序。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param request 完整字典类型版本序列
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/order")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.CHANGE_SORT, targetType = "dictionary-order")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Void> reorderTypes(
            @PathVariable @NotNull Long tenantId,
            @Valid @RequestBody @NotNull DictionaryReorderRequest request) {
        LinkedHashMap<Long, Long> versions = new LinkedHashMap<>();
        for (DictionaryReorderRequest.Item item : request.getItems()) {
            if (versions.putIfAbsent(item.getId(), item.getVersion()) != null) {
                throw new ConcurrencyConflictException(
                        SystemErrorCode.DICTIONARY_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
            }
        }
        service.reorderTypes(tenantId, versions);
        return ApiResponse.ok();
    }

    /**
     * 修改指定租户私有字典项展示排序。
     *
     * @param tenantId 平台管理员正在管理的租户主键
     * @param dictionaryId 租户私有字典类型主键
     * @param request 完整字典项版本序列
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/{dictionaryId}/items/order")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANT_DICTIONARIES, action = PlatformAuditOperations.CHANGE_ITEM_SORT, targetType = "dictionary", targetId = "#dictionaryId")
    @RequirePermission(PlatformPermissions.TENANT_DICTIONARIES_MANAGE)
    public @NotNull ApiResponse<Void> reorderItems(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long dictionaryId,
            @Valid @RequestBody @NotNull DictionaryReorderRequest request) {
        LinkedHashMap<Long, Long> versions = new LinkedHashMap<>();
        for (DictionaryReorderRequest.Item item : request.getItems()) {
            if (versions.putIfAbsent(item.getId(), item.getVersion()) != null) {
                throw new ConcurrencyConflictException(
                        SystemErrorCode.DICTIONARY_ITEM_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
            }
        }
        service.reorderItems(tenantId, dictionaryId, versions);
        return ApiResponse.ok();
    }
}
