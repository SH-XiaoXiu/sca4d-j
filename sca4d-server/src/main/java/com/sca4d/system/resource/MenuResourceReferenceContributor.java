package com.sca4d.system.resource;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceContributor;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.entity.SysMenuEntity;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.SystemResourceTypes;
import com.sca4d.system.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 系统菜单节点的批量资源引用贡献者。 */
@Component
@RequiredArgsConstructor
public class MenuResourceReferenceContributor implements ResourceReferenceContributor {

    private final MenuRepository repository;
    private final LocalizedMessages messages;

    @Override
    public @NotNull Set<String> resourceTypes() {
        return Set.of(SystemResourceTypes.MENU);
    }

    @Override
    public @NotNull Map<ResourceReferenceKey, ResourceReference> resolve(
            @NotNull Set<ResourceReferenceKey> keys, @NotNull ResourceReferenceContext context) {
        // 先把合法菜单主键去重为一个集合；历史非法标识不参与数据库查询，也不会作为标签回显。
        Set<Long> menuIds = new LinkedHashSet<>();
        for (ResourceReferenceKey key : keys) {
            try {
                menuIds.add(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                // 历史非法主键在下方共用返回删除态，不向界面回显裸值。
            }
        }
        // 整个资源引用批次固定执行一条菜单安全投影查询，随后只在内存中恢复标题和导航状态。
        Map<Long, SysMenuEntity> menus = repository.findMenuReferences(menuIds).stream()
                .collect(Collectors.toMap(SysMenuEntity::getId, Function.identity()));
        Map<ResourceReferenceKey, ResourceReference> references = new LinkedHashMap<>();
        for (ResourceReferenceKey key : keys) {
            SysMenuEntity menu;
            try {
                menu = menus.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                menu = null;
            }
            String reason = messages.get("resource.reference.deleted");
            if (menu == null || menu.getDeleted() == null || menu.getDeleted() != 0) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, reason));
                continue;
            }
            // 资源存在时按当前请求语言解析业务标题；是否可以打开详情页仍由当前 Principal 和已加载权限决定。
            String label = repository.restoreTitles(menu.getTitleLabels())
                    .resolve(LocaleContextHolder.getLocale());
            if (context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(SystemPermissions.MENUS_READ)) {
                references.put(key, new ResourceReference(
                        key,
                        label,
                        null,
                        ResourceNavigationState.NAVIGABLE,
                        "/system/menus/" + menu.getId(),
                        null));
                continue;
            }
            String forbidden = messages.get("resource.reference.forbidden");
            references.put(key, new ResourceReference(
                    key, null, null, ResourceNavigationState.FORBIDDEN, null, forbidden));
        }
        return Map.copyOf(references);
    }
}
