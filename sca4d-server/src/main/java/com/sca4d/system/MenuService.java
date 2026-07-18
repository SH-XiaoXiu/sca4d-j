package com.sca4d.system;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.system.menu.ManagedMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** 创建和修改平台菜单，并查询当前 Principal 可以访问的导航菜单。 */
public interface MenuService {

    /**
     * 读取平台维护的完整菜单树。
     *
     * @return 包含隐藏节点、代码路由、权限绑定和乐观锁版本的完整树
     */
    @NotNull
    List<ManagedMenu> menus();

    /**
     * 读取菜单编辑器可以选择的全部代码路由。
     *
     * @return 按固定编码排序且已经校验权限引用的路由目录
     */
    @NotNull
    List<NavigationRoute> routes();

    /**
     * 创建结构分组或注册路由菜单。
     *
     * @param parentId 父菜单主键；一级菜单为空
     * @param routeCode 注册路由编码；结构分组为空
     * @param titles 完整多语言标题
     * @param icon 图标含义
     * @param visible 是否进入当前用户导航
     * @return 新菜单主键
     */
    @NotNull
    Long create(
            @Nullable Long parentId,
            @Nullable String routeCode,
            @NotNull Map<String, String> titles,
            @NotNull MenuIconName icon,
            boolean visible);

    /**
     * 修改一个菜单的父关系、注册路由引用和展示数据。
     *
     * @param menuId 菜单固定主键
     * @param parentId 新父菜单主键；一级菜单为空
     * @param routeCode 注册路由编码；结构分组为空
     * @param titles 完整多语言标题
     * @param icon 图标含义
     * @param visible 是否进入当前用户导航
     * @param version 当前乐观锁版本
     */
    void update(
            @NotNull Long menuId,
            @Nullable Long parentId,
            @Nullable String routeCode,
            @NotNull Map<String, String> titles,
            @NotNull MenuIconName icon,
            boolean visible,
            long version);

    /**
     * 删除一个菜单及其全部下级节点。
     *
     * @param menuId 待删除子树根菜单主键
     * @param version 根菜单当前乐观锁版本
     */
    void delete(@NotNull Long menuId, long version);

    /**
     * 原子调整一个父节点下全部直接子菜单的顺序。
     *
     * @param parentId 父菜单主键；一级菜单使用 null
     * @param menuVersions 按最终顺序排列的菜单主键和当前版本
     */
    void reorder(@Nullable Long parentId, @NotNull Map<Long, Long> menuVersions);

    /**
     * 按当前 Principal 类型、租户数据和 Session 权限生成最终导航。
     *
     * @param principal 当前请求已经选择的执行主体
     * @param permission 当前 Principal 的会话角色权限快照
     * @return 已解析路径、当前语言标题且可以直接渲染的导航树
     */
    @NotNull
    List<NavigationMenu> navigation(
            @NotNull Principal principal, @NotNull SessionPermission permission);
}
