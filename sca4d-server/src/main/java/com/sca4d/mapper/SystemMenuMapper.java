package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.SysMenuEntity;
import com.sca4d.system.MenuOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 系统菜单 JSONB 写入、图结构锁和递归删除 SQL Mapper。 */
@Mapper
public interface SystemMenuMapper extends BaseMapper<SysMenuEntity> {

    /**
     * 获取系统菜单图的 PostgreSQL 事务级互斥锁。
     *
     * <p>创建、换父节点和递归删除共用该锁，避免多个节点并发修改后形成环或悬空结构。
     *
     * @return PostgreSQL 锁函数执行后的固定标记 1
     */
    int lockMenuCatalog();

    /**
     * 写入一个已经分配主键并完成目录校验的菜单。
     *
     * @param menu 包含 JSON 标题文本和代码路由引用的菜单实体
     * @return 实际写入行数，只能为 1
     */
    int insertMenu(@Param("menu") @NotNull SysMenuEntity menu);

    /**
     * 按主键和乐观锁版本原子更新菜单展示数据与代码路由引用。
     *
     * @param menuId 菜单固定主键
     * @param parentId 新父菜单主键；一级菜单为空
     * @param routeCode 代码路由编码；结构分组为空
     * @param permissionCode 路由绑定权限；结构分组或无权限入口为空
     * @param titleLabels 已序列化的双语标题 JSON
     * @param iconCode 图标含义
     * @param sortOrder 同级展示排序
     * @param visible 是否进入当前用户导航
     * @param version 当前乐观锁版本
     * @return 实际更新行数，只能为 0 或 1
     */
    int updateMenu(
            @Param("menuId") @NotNull Long menuId,
            @Param("parentId") @Nullable Long parentId,
            @Param("routeCode") @Nullable String routeCode,
            @Param("permissionCode") @Nullable String permissionCode,
            @Param("titleLabels") @NotNull String titleLabels,
            @Param("iconCode") @NotNull String iconCode,
            @Param("sortOrder") int sortOrder,
            @Param("visible") boolean visible,
            @Param("version") long version);

    /**
     * 使用各节点原版本一次性写入一个兄弟集合的最终顺序。
     *
     * @param items 已按最终位置分配排序值的完整兄弟集合
     * @return 实际更新行数；少于集合大小表示至少一个节点已经变化
     */
    int reorderMenus(@Param("items") @NotNull List<MenuOrder> items);

    /**
     * 在根节点版本匹配时一次递归推进整个菜单子树到删除终态。
     *
     * @param menuId 待删除子树根菜单主键
     * @param version 根菜单当前乐观锁版本
     * @return 删除的根和全部未删除后代数量；根不存在或版本变化时为 0
     */
    int markMenuTreeDeleted(
            @Param("menuId") @NotNull Long menuId, @Param("version") long version);
}
