package com.sca4d.system.service;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.web.ClientState;
import com.sca4d.system.MenuIconName;
import com.sca4d.system.MenuOrder;
import com.sca4d.system.MenuService;
import com.sca4d.system.MenuTitles;
import com.sca4d.system.NavigationMenu;
import com.sca4d.system.NavigationRoute;
import com.sca4d.system.NavigationRouteCatalog;
import com.sca4d.system.SystemClientStateTargets;
import com.sca4d.system.SystemErrorCode;
import com.sca4d.system.menu.ManagedMenu;
import com.sca4d.system.menu.MenuSnapshot;
import com.sca4d.system.realtime.SystemRealtimePublisher;
import com.sca4d.system.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 使用代码路由目录维护平台菜单并生成请求级最终导航。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultMenuService implements MenuService {

    private static final Comparator<MenuSnapshot> MENU_ORDER = Comparator.comparingInt(MenuSnapshot::getSortOrder)
            .thenComparing(MenuSnapshot::getMenuId);

    private final MenuRepository repository;
    private final NavigationRouteCatalog routeCatalog;
    private final SystemRealtimePublisher realtimePublisher;

    @Override
    public @NotNull List<ManagedMenu> menus() {
        // 一次读取完整菜单数据并建立主键索引；后续父子装配、排序和路由校验全部在内存中完成。
        List<MenuSnapshot> snapshots = repository.findMenus();
        Map<Long, MenuSnapshot> byId = new LinkedHashMap<>();
        Map<Long, List<MenuSnapshot>> children = new HashMap<>();
        List<MenuSnapshot> roots = new ArrayList<>();
        for (MenuSnapshot snapshot : snapshots) {
            if (byId.putIfAbsent(snapshot.getMenuId(), snapshot) != null) {
                throw new IllegalStateException("系统菜单存在重复主键: " + snapshot.getMenuId());
            }
            registeredRoute(snapshot);
        }
        for (MenuSnapshot snapshot : snapshots) {
            if (snapshot.getParentId() == null) {
                roots.add(snapshot);
                continue;
            }
            MenuSnapshot parent = byId.get(snapshot.getParentId());
            if (parent == null || parent.getRouteCode() != null) {
                throw new IllegalStateException("系统菜单父关系无效: " + snapshot.getMenuId());
            }
            children.computeIfAbsent(parent.getMenuId(), ignored -> new ArrayList<>()).add(snapshot);
        }
        roots.sort(MENU_ORDER);
        children.values().forEach(items -> items.sort(MENU_ORDER));

        Set<Long> visited = new HashSet<>();
        List<ManagedMenu> tree = new ArrayList<>();
        for (MenuSnapshot root : roots) {
            tree.add(managedMenu(root, children, visited));
        }
        if (visited.size() != snapshots.size()) {
            throw new IllegalStateException("系统菜单存在循环或不可达节点");
        }
        return List.copyOf(tree);
    }

    @Override
    public @NotNull List<NavigationRoute> routes() {
        return routeCatalog.routes();
    }

    @Override
    @Transactional
    public @NotNull Long create(
            @Nullable Long parentId,
            @Nullable String routeCode,
            @NotNull Map<String, String> titles,
            @NotNull MenuIconName icon,
            boolean visible) {
        // 所有菜单写入口先取得同一数据库事务锁，再基于同一份完整图校验父节点和路由占用数据。
        List<MenuSnapshot> snapshots = repository.lockMenus();
        Map<Long, MenuSnapshot> byId = snapshots.stream()
                .collect(Collectors.toMap(MenuSnapshot::getMenuId, item -> item));
        if (parentId != null) {
            MenuSnapshot parent = byId.get(parentId);
            if (parent == null) {
                throw new BizException(SystemErrorCode.MENU_PARENT_NOT_FOUND);
            }
            if (parent.getRouteCode() != null) {
                throw new BizException(SystemErrorCode.MENU_PARENT_MUST_BE_GROUP);
            }
        }

        NavigationRoute route = null;
        String normalizedRouteCode = routeCode == null || routeCode.isBlank() ? null : routeCode.trim();
        if (normalizedRouteCode != null) {
            route = routeCatalog.find(normalizedRouteCode);
            if (route == null) {
                throw new BizException(SystemErrorCode.MENU_ROUTE_NOT_REGISTERED);
            }
            boolean occupied = snapshots.stream()
                    .anyMatch(menu -> normalizedRouteCode.equals(menu.getRouteCode()));
            if (occupied) {
                throw new BizException(SystemErrorCode.MENU_ROUTE_OCCUPIED);
            }
        }

        MenuTitles checkedTitles;
        try {
            checkedTitles = new MenuTitles(titles);
        } catch (IllegalArgumentException exception) {
            throw new BizException(SystemErrorCode.MENU_TITLES_INVALID);
        }
        int sortOrder = Math.addExact(snapshots.stream()
                .filter(menu -> Objects.equals(menu.getParentId(), parentId))
                .mapToInt(MenuSnapshot::getSortOrder)
                .max()
                .orElse(0), 10);
        // 权限编码只从代码路由目录派生，客户端不能直接写入或覆盖权限关系。
        Long menuId = repository.createMenu(
                parentId,
                normalizedRouteCode,
                route == null ? null : route.getPermissionCode(),
                checkedTitles,
                icon,
                sortOrder,
                visible);
        // 菜单保存成功后使当前客户端重新读取服务端过滤导航；其他客户端在下次 /me 刷新时读取同一数据库数据。
        ClientState.refresh(SystemClientStateTargets.MENUS);
        realtimePublisher.menusChanged();
        log.info(
                "系统菜单创建已写入事务，实时更新已登记 | menuId={} parentId={} visible={}",
                menuId,
                parentId,
                visible);
        return menuId;
    }

    @Override
    @Transactional
    public void update(
            @NotNull Long menuId,
            @Nullable Long parentId,
            @Nullable String routeCode,
            @NotNull Map<String, String> titles,
            @NotNull MenuIconName icon,
            boolean visible,
            long version) {
        // 目录事务锁保证父关系、路由唯一性和最终乐观写入之间没有其他节点插入新的菜单图变化。
        List<MenuSnapshot> snapshots = repository.lockMenus();
        Map<Long, MenuSnapshot> byId = snapshots.stream()
                .collect(Collectors.toMap(MenuSnapshot::getMenuId, item -> item));
        MenuSnapshot current = byId.get(menuId);
        if (current == null) {
            throw new BizException(SystemErrorCode.MENU_NOT_FOUND);
        }
        if (parentId != null) {
            if (parentId.equals(menuId)) {
                throw new BizException(SystemErrorCode.MENU_CYCLE);
            }
            MenuSnapshot parent = byId.get(parentId);
            if (parent == null) {
                throw new BizException(SystemErrorCode.MENU_PARENT_NOT_FOUND);
            }
            if (parent.getRouteCode() != null) {
                throw new BizException(SystemErrorCode.MENU_PARENT_MUST_BE_GROUP);
            }
            Set<Long> ancestors = new HashSet<>();
            MenuSnapshot cursor = parent;
            // 沿现有父链向上检查，禁止把节点移动到自身任一后代下面。
            while (cursor != null && cursor.getParentId() != null) {
                if (!ancestors.add(cursor.getMenuId()) || menuId.equals(cursor.getParentId())) {
                    throw new BizException(SystemErrorCode.MENU_CYCLE);
                }
                cursor = byId.get(cursor.getParentId());
            }
        }

        NavigationRoute route = null;
        String normalizedRouteCode = routeCode == null || routeCode.isBlank() ? null : routeCode.trim();
        if (normalizedRouteCode != null) {
            route = routeCatalog.find(normalizedRouteCode);
            if (route == null) {
                throw new BizException(SystemErrorCode.MENU_ROUTE_NOT_REGISTERED);
            }
            boolean hasChildren = snapshots.stream().anyMatch(menu -> menuId.equals(menu.getParentId()));
            if (hasChildren) {
                throw new BizException(SystemErrorCode.MENU_GROUP_HAS_CHILDREN);
            }
            boolean occupied = snapshots.stream()
                    .anyMatch(menu -> !menuId.equals(menu.getMenuId())
                            && normalizedRouteCode.equals(menu.getRouteCode()));
            if (occupied) {
                throw new BizException(SystemErrorCode.MENU_ROUTE_OCCUPIED);
            }
        }

        MenuTitles checkedTitles;
        try {
            checkedTitles = new MenuTitles(titles);
        } catch (IllegalArgumentException exception) {
            throw new BizException(SystemErrorCode.MENU_TITLES_INVALID);
        }
        int sortOrder = current.getSortOrder();
        if (!Objects.equals(current.getParentId(), parentId)) {
            sortOrder = Math.addExact(snapshots.stream()
                    .filter(menu -> Objects.equals(menu.getParentId(), parentId))
                    .mapToInt(MenuSnapshot::getSortOrder)
                    .max()
                    .orElse(0), 10);
        }
        // Repository 在一条原子 SQL 中同时约束菜单主键、版本和未删除状态，并翻译数据库唯一冲突。
        repository.updateMenu(
                menuId,
                parentId,
                normalizedRouteCode,
                route == null ? null : route.getPermissionCode(),
                checkedTitles,
                icon,
                sortOrder,
                visible,
                version);
        ClientState.refresh(SystemClientStateTargets.MENUS);
        realtimePublisher.menusChanged();
        log.info(
                "系统菜单更新已写入事务，实时更新已登记 | menuId={} parentId={} visible={} version={}",
                menuId,
                parentId,
                visible,
                version + 1);
    }

    @Override
    @Transactional
    public void delete(@NotNull Long menuId, long version) {
        // 删除与创建、换父节点共用目录事务锁，递归子树在整个事务中保持固定。
        List<MenuSnapshot> snapshots = repository.lockMenus();
        if (snapshots.stream().noneMatch(menu -> menuId.equals(menu.getMenuId()))) {
            throw new BizException(SystemErrorCode.MENU_NOT_FOUND);
        }
        // 一条递归 CTE 同步隔离根节点和全部后代，不逐节点执行更新。
        repository.deleteMenuTree(menuId, version);
        ClientState.refresh(SystemClientStateTargets.MENUS);
        realtimePublisher.menusChanged();
        log.info("系统菜单及子菜单删除已写入事务，实时更新已登记 | menuId={}", menuId);
    }

    @Override
    @Transactional
    public void reorder(@Nullable Long parentId, @NotNull Map<Long, Long> menuVersions) {
        List<MenuSnapshot> siblings = repository.lockMenus().stream()
                .filter(menu -> Objects.equals(menu.getParentId(), parentId))
                .toList();
        Set<Long> siblingIds = siblings.stream().map(MenuSnapshot::getMenuId).collect(Collectors.toSet());
        if (menuVersions.size() != siblings.size() || !siblingIds.equals(menuVersions.keySet())) {
            throw new ConcurrencyConflictException(SystemErrorCode.MENU_CHANGED, SystemClientStateTargets.MENUS);
        }
        Map<Long, MenuSnapshot> siblingById = siblings.stream()
                .collect(Collectors.toMap(MenuSnapshot::getMenuId, menu -> menu));
        List<MenuOrder> orders = new ArrayList<>();
        int position = 10;
        for (Map.Entry<Long, Long> entry : menuVersions.entrySet()) {
            MenuSnapshot menu = siblingById.get(entry.getKey());
            if (menu == null || menu.getVersion() != entry.getValue()) {
                throw new ConcurrencyConflictException(SystemErrorCode.MENU_CHANGED, SystemClientStateTargets.MENUS);
            }
            orders.add(new MenuOrder(menu.getMenuId(), menu.getVersion(), position));
            position += 10;
        }
        repository.reorderMenus(orders);
        ClientState.refresh(SystemClientStateTargets.MENUS);
        realtimePublisher.menusChanged();
        log.info(
                "系统菜单排序已写入事务，实时更新已登记 | parentId={} itemCount={}",
                parentId,
                orders.size());
    }

    @Override
    public @NotNull List<NavigationMenu> navigation(
            @NotNull Principal principal, @NotNull SessionPermission permission) {
        // 一条 SQL 读取整棵菜单树；主体类型、权限、语言和租户路径解析均在当前请求内完成，不形成 N+1。
        List<MenuSnapshot> snapshots = repository.findMenus();
        Map<Long, MenuSnapshot> byId = snapshots.stream()
                .collect(Collectors.toMap(MenuSnapshot::getMenuId, item -> item));
        Map<Long, List<MenuSnapshot>> children = new HashMap<>();
        List<MenuSnapshot> roots = new ArrayList<>();
        for (MenuSnapshot snapshot : snapshots) {
            registeredRoute(snapshot);
            if (snapshot.getParentId() == null) {
                roots.add(snapshot);
                continue;
            }
            MenuSnapshot parent = byId.get(snapshot.getParentId());
            if (parent == null || parent.getRouteCode() != null) {
                throw new IllegalStateException("系统菜单父关系无效: " + snapshot.getMenuId());
            }
            children.computeIfAbsent(parent.getMenuId(), ignored -> new ArrayList<>()).add(snapshot);
        }
        roots.sort(MENU_ORDER);
        children.values().forEach(items -> items.sort(MENU_ORDER));

        Set<Long> visited = new HashSet<>();
        List<NavigationMenu> navigation = new ArrayList<>();
        for (MenuSnapshot root : roots) {
            NavigationMenu item = navigationMenu(root, children, visited, principal, permission);
            if (item != null) {
                navigation.add(item);
            }
        }
        if (visited.size() != snapshots.size()) {
            throw new IllegalStateException("系统菜单存在循环或不可达节点");
        }
        return List.copyOf(navigation);
    }

    /**
     * 递归构建管理端完整树，并记录每个节点只访问一次。
     *
     * @param snapshot 当前菜单数据
     * @param children 父菜单到已排序下级数据的索引
     * @param visited 当前树构建已经访问的菜单主键
     * @return 包含代码路由展示信息和下级节点的管理菜单
     */
    private @NotNull ManagedMenu managedMenu(
            @NotNull MenuSnapshot snapshot,
            @NotNull Map<Long, List<MenuSnapshot>> children,
            @NotNull Set<Long> visited) {
        if (!visited.add(snapshot.getMenuId())) {
            throw new IllegalStateException("系统菜单存在循环: " + snapshot.getMenuId());
        }
        NavigationRoute route = registeredRoute(snapshot);
        List<ManagedMenu> managedChildren = new ArrayList<>();
        for (MenuSnapshot child : children.getOrDefault(snapshot.getMenuId(), List.of())) {
            managedChildren.add(managedMenu(child, children, visited));
        }
        return new ManagedMenu(
                snapshot.getMenuId(),
                snapshot.getParentId(),
                snapshot.getRouteCode(),
                route == null ? null : route.getPathTemplate(),
                snapshot.getPermissionCode(),
                snapshot.getTitles(),
                snapshot.getIcon(),
                snapshot.getSortOrder(),
                snapshot.isVisible(),
                snapshot.getVersion(),
                managedChildren);
    }

    /**
     * 递归过滤一个菜单子树并解析最终路径和当前语言标题。
     *
     * <p>每个节点只在内存中访问一次，不执行数据库或外部调用。隐藏分组会连同全部后代关闭；空结构分组不返回浏览器。
     *
     * @param snapshot 当前菜单数据
     * @param children 父菜单到已排序下级数据的索引
     * @param visited 当前过滤已经访问的菜单主键
     * @param principal 当前请求执行主体
     * @param permission 当前主体会话权限
     * @return 当前主体可见节点；被隐藏、类型不匹配、无权限或空分组时返回 null
     */
    private @Nullable NavigationMenu navigationMenu(
            @NotNull MenuSnapshot snapshot,
            @NotNull Map<Long, List<MenuSnapshot>> children,
            @NotNull Set<Long> visited,
            @NotNull Principal principal,
            @NotNull SessionPermission permission) {
        if (!visited.add(snapshot.getMenuId())) {
            throw new IllegalStateException("系统菜单存在循环: " + snapshot.getMenuId());
        }
        if (!snapshot.isVisible()) {
            // 隐藏分组关闭整棵子树，但仍完整遍历后代以识别循环或不可达结构。
            List<MenuSnapshot> descendants = new ArrayList<>(children.getOrDefault(snapshot.getMenuId(), List.of()));
            while (!descendants.isEmpty()) {
                MenuSnapshot descendant = descendants.remove(descendants.size() - 1);
                if (!visited.add(descendant.getMenuId())) {
                    throw new IllegalStateException("系统菜单存在循环: " + descendant.getMenuId());
                }
                descendants.addAll(children.getOrDefault(descendant.getMenuId(), List.of()));
            }
            return null;
        }
        NavigationRoute route = registeredRoute(snapshot);
        if (route != null) {
            if (!route.getPrincipalTypes().contains(principal.getPrincipalType())
                    || (route.getPermissionCode() != null
                            && !permission.getPermissionCodes().contains(route.getPermissionCode()))) {
                return null;
            }
            return new NavigationMenu(
                    snapshot.getMenuId(),
                    route.getCode(),
                    route.pathFor(principal),
                    snapshot.getTitles().resolve(LocaleContextHolder.getLocale()),
                    snapshot.getIcon(),
                    List.of());
        }

        List<NavigationMenu> visibleChildren = new ArrayList<>();
        for (MenuSnapshot child : children.getOrDefault(snapshot.getMenuId(), List.of())) {
            NavigationMenu item = navigationMenu(child, children, visited, principal, permission);
            if (item != null) {
                visibleChildren.add(item);
            }
        }
        if (visibleChildren.isEmpty()) {
            return null;
        }
        return new NavigationMenu(
                snapshot.getMenuId(),
                null,
                null,
                snapshot.getTitles().resolve(LocaleContextHolder.getLocale()),
                snapshot.getIcon(),
                visibleChildren);
    }

    /**
     * 解析菜单保存的代码路由并确认权限引用未漂移。
     *
     * @param snapshot 菜单持久化数据
     * @return 已注册路由；结构分组返回 null
     * @throws IllegalStateException 数据库引用未注册路由或权限与代码目录不一致时抛出
     */
    private @Nullable NavigationRoute registeredRoute(@NotNull MenuSnapshot snapshot) {
        if (snapshot.getRouteCode() == null) {
            if (snapshot.getPermissionCode() != null) {
                throw new IllegalStateException("系统菜单结构分组不能绑定权限: " + snapshot.getMenuId());
            }
            return null;
        }
        NavigationRoute route = routeCatalog.find(snapshot.getRouteCode());
        if (route == null || !Objects.equals(route.getPermissionCode(), snapshot.getPermissionCode())) {
            throw new IllegalStateException("系统菜单路由或权限引用已失效: " + snapshot.getMenuId());
        }
        return route;
    }
}
