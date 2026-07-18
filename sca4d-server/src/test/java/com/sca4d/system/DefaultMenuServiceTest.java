package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.web.ClientState;
import com.sca4d.system.menu.MenuSnapshot;
import com.sca4d.system.realtime.SystemRealtimePublisher;
import com.sca4d.system.repository.MenuRepository;
import com.sca4d.system.service.DefaultMenuService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 验证菜单创建、移动、修改、删除和当前用户导航查询。 */
class DefaultMenuServiceTest {

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
        ClientState.clear();
    }

    @Test
    void shouldReturnOnlyVisibleRoutesAllowedByPrincipalAndPermission() {
        LocaleContextHolder.setLocale(Locale.US);
        MenuRepository repository = mock(MenuRepository.class);
        NavigationRouteCatalog catalog = mock(NavigationRouteCatalog.class);
        NavigationRoute users = route(
                "platform.accounts", "/platform/accounts", PrincipalType.PLATFORM, "platform:accounts:read", "Users");
        NavigationRoute tenantMembers = route(
                "tenant.members",
                "/tenant/tenants/{tenantId}/members",
                PrincipalType.TENANT,
                "tenant:members:read",
                "Users");
        when(catalog.find("platform.accounts")).thenReturn(users);
        when(catalog.find("tenant.members")).thenReturn(tenantMembers);
        when(repository.findMenus()).thenReturn(List.of(
                menu(1L, null, null, null, "管理", "Management", "FolderTree", 10, true, 0),
                menu(2L, 1L, "platform.accounts", "platform:accounts:read", "用户", "Users", "Users", 10, true, 0),
                menu(3L, 1L, "tenant.members", "tenant:members:read", "成员", "Members", "Users", 20, true, 0),
                menu(4L, null, null, null, "隐藏", "Hidden", "FolderTree", 20, false, 0),
                menu(5L, 4L, "platform.accounts", "platform:accounts:read", "重复", "Duplicate", "Users", 10, true, 0)));
        DefaultMenuService service = new DefaultMenuService(repository, catalog, mock(SystemRealtimePublisher.class));
        Principal principal = Principal.withDisplayName(11L, PrincipalType.PLATFORM, 9L, null, "平台管理员");

        var navigation = service.navigation(
                principal,
                new SessionPermission(11L, Set.of(), Set.of("platform:accounts:read")));

        assertThat(navigation).singleElement().satisfies(group -> {
            assertThat(group.getTitle()).isEqualTo("Management");
            assertThat(group.getChildren()).singleElement().satisfies(item -> {
                assertThat(item.getRouteCode()).isEqualTo("platform.accounts");
                assertThat(item.getPath()).isEqualTo("/platform/accounts");
                assertThat(item.getTitle()).isEqualTo("Users");
            });
        });
    }

    @Test
    void shouldRejectChildUnderRouteAndConcurrentCycle() {
        MenuRepository repository = mock(MenuRepository.class);
        NavigationRouteCatalog catalog = mock(NavigationRouteCatalog.class);
        NavigationRoute users = route(
                "platform.accounts", "/platform/accounts", PrincipalType.PLATFORM, "platform:accounts:read", "Users");
        when(catalog.find("platform.accounts")).thenReturn(users);
        MenuSnapshot root = menu(
                1L, null, null, null, "管理", "Management", "FolderTree", 10, true, 0);
        MenuSnapshot leaf = menu(
                2L, 1L, "platform.accounts", "platform:accounts:read", "用户", "Users", "Users", 10, true, 0);
        MenuSnapshot childGroup = menu(
                3L, 1L, null, null, "下级分组", "Child group", "FolderTree", 20, true, 0);
        when(repository.lockMenus()).thenReturn(List.of(root, leaf, childGroup));
        DefaultMenuService service = new DefaultMenuService(repository, catalog, mock(SystemRealtimePublisher.class));

        assertThatThrownBy(() -> service.create(
                2L,
                null,
                Map.of("zh-CN", "下级", "en-US", "Child"),
                MenuIconName.of("FolderTree"),
                true))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.menu.parent-must-be-group"));

        assertThatThrownBy(() -> service.update(
                1L,
                3L,
                null,
                Map.of("zh-CN", "管理", "en-US", "Management"),
                MenuIconName.of("FolderTree"),
                true,
                0))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.menu.cycle"));
    }

    @Test
    void shouldCreateRouteWithPermissionDerivedFromCatalog() {
        MenuRepository repository = mock(MenuRepository.class);
        NavigationRouteCatalog catalog = mock(NavigationRouteCatalog.class);
        NavigationRoute users = route(
                "platform.accounts", "/platform/accounts", PrincipalType.PLATFORM, "platform:accounts:read", "Users");
        when(repository.lockMenus()).thenReturn(List.of(
                menu(1L, null, null, null, "管理", "Management", "FolderTree", 30, true, 0)));
        when(catalog.find("platform.accounts")).thenReturn(users);
        when(repository.createMenu(
                null,
                "platform.accounts",
                "platform:accounts:read",
                new MenuTitles(Map.of("zh-CN", "平台账号", "en-US", "Platform accounts")),
                MenuIconName.of("Users"),
                40,
                true))
                .thenReturn(9L);
        SystemRealtimePublisher realtimePublisher = mock(SystemRealtimePublisher.class);
        DefaultMenuService service = new DefaultMenuService(repository, catalog, realtimePublisher);

        Long menuId = service.create(
                null,
                "platform.accounts",
                Map.of("zh-CN", "平台账号", "en-US", "Platform accounts"),
                MenuIconName.of("Users"),
                true);

        assertThat(menuId).isEqualTo(9L);
        assertThat(ClientState.currentOrNull()).isNotNull();
        assertThat(ClientState.currentOrNull().getRefreshTargets())
                .containsExactly(SystemClientStateTargets.MENUS);
        verify(realtimePublisher).menusChanged();
        verify(repository).createMenu(
                null,
                "platform.accounts",
                "platform:accounts:read",
                new MenuTitles(Map.of("zh-CN", "平台账号", "en-US", "Platform accounts")),
                MenuIconName.of("Users"),
                40,
                true);
    }

    @Test
    void shouldRefreshCurrentNavigationAfterUpdateAndDelete() {
        MenuRepository repository = mock(MenuRepository.class);
        NavigationRouteCatalog catalog = mock(NavigationRouteCatalog.class);
        MenuSnapshot group = menu(
                1L, null, null, null, "管理", "Management", "FolderTree", 10, true, 3);
        when(repository.lockMenus()).thenReturn(List.of(group));
        SystemRealtimePublisher realtimePublisher = mock(SystemRealtimePublisher.class);
        DefaultMenuService service = new DefaultMenuService(repository, catalog, realtimePublisher);

        service.update(
                1L,
                null,
                null,
                Map.of("zh-CN", "系统", "en-US", "System"),
                MenuIconName.of("FolderTree"),
                true,
                3);

        assertThat(ClientState.currentOrNull()).isNotNull();
        assertThat(ClientState.currentOrNull().getRefreshTargets())
                .containsExactly(SystemClientStateTargets.MENUS);
        verify(repository).updateMenu(
                1L,
                null,
                null,
                null,
                new MenuTitles(Map.of("zh-CN", "系统", "en-US", "System")),
                MenuIconName.of("FolderTree"),
                10,
                true,
                3);
        ClientState.clear();

        service.delete(1L, 3);

        assertThat(ClientState.currentOrNull()).isNotNull();
        assertThat(ClientState.currentOrNull().getRefreshTargets())
                .containsExactly(SystemClientStateTargets.MENUS);
        verify(repository).deleteMenuTree(1L, 3);
        verify(realtimePublisher, Mockito.times(2)).menusChanged();
    }

    @Test
    void shouldAppendMenuWhenMovingToAnotherParent() {
        MenuRepository repository = mock(MenuRepository.class);
        MenuSnapshot moving = menu(
                1L, null, null, null, "移动", "Moving", "FolderTree", 10, true, 3);
        MenuSnapshot parent = menu(
                2L, null, null, null, "目标", "Target", "FolderTree", 20, true, 1);
        MenuSnapshot existingChild = menu(
                3L, 2L, null, null, "已有", "Existing", "FolderTree", 70, true, 2);
        when(repository.lockMenus()).thenReturn(List.of(moving, parent, existingChild));
        DefaultMenuService service = new DefaultMenuService(
                repository,
                mock(NavigationRouteCatalog.class),
                mock(SystemRealtimePublisher.class));

        service.update(
                1L,
                2L,
                null,
                Map.of("zh-CN", "移动", "en-US", "Moving"),
                MenuIconName.of("FolderTree"),
                true,
                3);

        verify(repository).updateMenu(
                1L,
                2L,
                null,
                null,
                new MenuTitles(Map.of("zh-CN", "移动", "en-US", "Moving")),
                MenuIconName.of("FolderTree"),
                80,
                true,
                3);
    }

    /**
     * 创建只允许一种 Principal 且绑定一个权限的测试导航路由。
     *
     * @param code 测试场景使用的固定路由编码
     * @param path 不包含数据库自由输入的前端路径模板
     * @param principalType 唯一允许进入该路由的 Principal 类型
     * @param permission 路由要求的代码权限
     * @param icon 路由默认图标含义
     * @return 可以注册到测试路由目录的导航路由
     */
    private @NotNull NavigationRoute route(
            @NotNull String code,
            @NotNull String path,
            @NotNull PrincipalType principalType,
            @NotNull String permission,
            @NotNull String icon) {
        return new NavigationRoute(
                code,
                path,
                Set.of(principalType),
                permission,
                MenuIconName.of(icon));
    }

    /**
     * 创建测试菜单图中的一个持久化数据快照。
     *
     * @param menuId 菜单主键
     * @param parentId 父菜单主键；根节点为空
     * @param routeCode 注册路由编码；结构分组为空
     * @param permissionCode 路由代码权限；结构分组为空
     * @param zhCn 简体中文标题
     * @param enUs 英文标题
     * @param icon 图标含义
     * @param sortOrder 同级排序值
     * @param visible 是否进入最终导航
     * @param version 乐观锁版本
     * @return 可供菜单服务构建树和过滤导航的快照
     */
    private @NotNull MenuSnapshot menu(
            long menuId,
            @Nullable Long parentId,
            @Nullable String routeCode,
            @Nullable String permissionCode,
            @NotNull String zhCn,
            @NotNull String enUs,
            @NotNull String icon,
            int sortOrder,
            boolean visible,
            long version) {
        return new MenuSnapshot(
                menuId,
                parentId,
                routeCode,
                permissionCode,
                new MenuTitles(Map.of("zh-CN", zhCn, "en-US", enUs)),
                MenuIconName.of(icon),
                sortOrder,
                visible,
                version);
    }
}
