package com.sca4d.system.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.SysMenuEntity;
import com.sca4d.mapper.SystemMenuMapper;
import com.sca4d.system.MenuIconName;
import com.sca4d.system.MenuOrder;
import com.sca4d.system.MenuTitles;
import com.sca4d.system.SystemClientStateTargets;
import com.sca4d.system.SystemErrorCode;
import com.sca4d.system.menu.MenuSnapshot;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 读写系统菜单、JSONB 多语言标题和菜单排序关系。 */
@Repository
@RequiredArgsConstructor
public class MenuRepository {

    private static final TypeReference<Map<String, String>> TITLE_MAP = new TypeReference<>() {
    };

    private final SystemMenuMapper mapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /**
     * 读取全部未删除菜单数据。
     *
     * <p>固定执行一条单表 SQL，结果按父关系、排序和主键在 Service 中构建树，不按节点继续查询。
     *
     * @return 按排序和主键固定排列的菜单快照
     */
    public @NotNull List<MenuSnapshot> findMenus() {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysMenuEntity::getId),
                        column(SysMenuEntity::getParentId),
                        column(SysMenuEntity::getPermissionCode),
                        column(SysMenuEntity::getRouteCode),
                        column(SysMenuEntity::getTitleLabels).cast("text").as(SysMenuEntity::getTitleLabels),
                        column(SysMenuEntity::getIconCode),
                        column(SysMenuEntity::getSortOrder),
                        column(SysMenuEntity::getVisible),
                        column(SysMenuEntity::getVersion))
                .where(SysMenuEntity::getDeleted)
                .eq(0)
                .orderBy(SysMenuEntity::getSortOrder)
                .asc()
                .orderBy(SysMenuEntity::getId)
                .asc();
        return mapper.selectListByQuery(query).stream().map(this::snapshot).toList();
    }

    /**
     * 锁定菜单目录并读取当前完整图，供一个写事务完成父子、路由唯一性和环校验。
     *
     * <p>固定执行一次 PostgreSQL advisory lock 和一次单表查询。所有正式菜单写入口共用同一事务锁，集群多节点并发时仍串行修改图结构。
     *
     * @return 当前未删除菜单数据
     */
    @Transactional
    public @NotNull List<MenuSnapshot> lockMenus() {
        mapper.lockMenuCatalog();
        return findMenus();
    }

    /**
     * 创建一个已经通过代码目录和菜单图校验的节点。
     *
     * @param parentId 父菜单主键；一级菜单为空
     * @param routeCode 代码路由编码；结构分组为空
     * @param permissionCode 路由绑定权限；结构分组或无权限入口为空
     * @param titles 完整双语标题
     * @param icon 图标含义
     * @param sortOrder 同级展示排序
     * @param visible 是否进入当前用户导航
     * @return 新菜单主键
     */
    public @NotNull Long createMenu(
            @Nullable Long parentId,
            @Nullable String routeCode,
            @Nullable String permissionCode,
            @NotNull MenuTitles titles,
            @NotNull MenuIconName icon,
            int sortOrder,
            boolean visible) {
        SysMenuEntity entity = new SysMenuEntity()
                .setId(idGenerator.nextId())
                .setParentId(parentId)
                .setPermissionCode(permissionCode)
                .setRouteCode(routeCode)
                .setTitleLabels(encode(titles))
                .setIconCode(icon.value())
                .setSortOrder(sortOrder)
                .setVisible(visible)
                .setDeleted(0);
        try {
            if (mapper.insertMenu(entity) != 1) {
                throw new IllegalStateException("菜单写入行数异常");
            }
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_sys_menu_route")) {
                throw new BizException(SystemErrorCode.MENU_ROUTE_OCCUPIED);
            }
            throw exception;
        }
        return entity.getId();
    }

    /**
     * 原子修改菜单父关系、代码路由引用和展示数据。
     *
     * @param menuId 菜单固定主键
     * @param parentId 新父菜单主键；一级菜单为空
     * @param routeCode 代码路由编码；结构分组为空
     * @param permissionCode 路由绑定权限；结构分组或无权限入口为空
     * @param titles 完整双语标题
     * @param icon 图标含义
     * @param sortOrder 同级展示排序
     * @param visible 是否进入当前用户导航
     * @param version 当前乐观锁版本
     */
    public void updateMenu(
            @NotNull Long menuId,
            @Nullable Long parentId,
            @Nullable String routeCode,
            @Nullable String permissionCode,
            @NotNull MenuTitles titles,
            @NotNull MenuIconName icon,
            int sortOrder,
            boolean visible,
            long version) {
        try {
            if (mapper.updateMenu(
                    menuId,
                    parentId,
                    routeCode,
                    permissionCode,
                    encode(titles),
                    icon.value(),
                    sortOrder,
                    visible,
                    version) != 1) {
                throw new ConcurrencyConflictException(SystemErrorCode.MENU_CHANGED, SystemClientStateTargets.MENUS);
            }
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_sys_menu_route")) {
                throw new BizException(SystemErrorCode.MENU_ROUTE_OCCUPIED);
            }
            throw exception;
        }
    }

    /**
     * 原子写入一个兄弟集合的最终顺序。
     *
     * @param items 已完成集合、版本和位置校验的菜单顺序
     */
    public void reorderMenus(@NotNull List<MenuOrder> items) {
        if (mapper.reorderMenus(items) != items.size()) {
            throw new ConcurrencyConflictException(SystemErrorCode.MENU_CHANGED, SystemClientStateTargets.MENUS);
        }
    }

    /**
     * 在根版本匹配时把一个菜单和全部下级节点推进到删除终态。
     *
     * <p>固定执行一条递归 CTE 更新；调用方已持有目录事务锁，新增下级和换父节点不能与本操作交错。
     *
     * @param menuId 待删除子树根菜单主键
     * @param version 根菜单当前乐观锁版本
     */
    public void deleteMenuTree(@NotNull Long menuId, long version) {
        if (mapper.markMenuTreeDeleted(menuId, version) == 0) {
            throw new ConcurrencyConflictException(SystemErrorCode.MENU_CHANGED, SystemClientStateTargets.MENUS);
        }
    }

    /**
     * 批量读取资源引用需要的菜单安全投影。
     *
     * <p>固定执行一条单表 SQL并保留删除状态；不加载权限、组件或任意前端路径。
     *
     * @param menuIds 去重后的菜单主键
     * @return 菜单标题和删除状态投影
     */
    public @NotNull List<SysMenuEntity> findMenuReferences(@NotNull Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysMenuEntity::getId),
                        column(SysMenuEntity::getTitleLabels).cast("text").as(SysMenuEntity::getTitleLabels),
                        column(SysMenuEntity::getDeleted))
                .where(SysMenuEntity::getId)
                .in(menuIds);
        return mapper.selectListByQuery(query);
    }

    /**
     * 把菜单持久化实体转换为已解析多语言标题的 MenuSnapshot。
     *
     * @param entity 单表查询得到的菜单实体
     * @return 已恢复双语标题的菜单数据
     */
    private @NotNull MenuSnapshot snapshot(@NotNull SysMenuEntity entity) {
        return new MenuSnapshot(
                entity.getId(),
                entity.getParentId(),
                entity.getRouteCode(),
                entity.getPermissionCode(),
                restoreTitles(entity.getTitleLabels()),
                MenuIconName.of(entity.getIconCode()),
                entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getVisible()),
                entity.getVersion());
    }

    /**
     * 使用项目共用 Jackson 契约编码菜单双语标题。
     *
     * @param titles 已完成语言和长度校验的菜单标题
     * @return 可显式转换为 PostgreSQL JSONB 的对象文本
     */
    private @NotNull String encode(@NotNull MenuTitles titles) {
        try {
            return objectMapper.writeValueAsString(titles.asMap());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("菜单标题编码失败", exception);
        }
    }

    /**
     * 使用项目共用 Jackson 契约恢复菜单双语标题。
     *
     * @param titleLabels PostgreSQL JSONB 文本投影
     * @return 已完成完整语言校验的菜单标题
     */
    public @NotNull MenuTitles restoreTitles(@NotNull String titleLabels) {
        try {
            return new MenuTitles(objectMapper.readValue(titleLabels, TITLE_MAP));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("菜单标题不是合法 JSON 对象", exception);
        }
    }

}
