# 添加数据库表和 Repository

以下步骤以 `sys_menu` 为例，说明 Flyway、Entity、Mapper、XML 和 Repository 的对应关系。

## 前置条件

- PostgreSQL 已启动，主应用可以连接开发数据库。
- 已确认下一个可用的 Flyway 版本号。
- 已确定表字段、索引、删除方式和并发更新方式。

## 1. 添加 Flyway 迁移

迁移文件位于：

```text
sca4d-server/src/main/resources/db/migration/
```

`sys_menu` 包含菜单定义、索引、初始菜单和字段注释。

新迁移使用下一个未占用版本号。已经在共享数据库执行过的迁移文件不能修改。

## 2. 添加 Entity

Entity 放在 `com.sca4d.entity`，字段与数据库列对应：

```java
@Data
@Accessors(chain = true)
@Table("sys_menu")
public class SysMenuEntity {

    private Long id;
    private Long parentId;
    private String permissionCode;
    private String routeCode;
    private String titleLabels;
    private String iconCode;
    private Integer sortOrder;
    private Boolean visible;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
```

`title_labels` 是 JSONB，Entity 使用 String 接收显式转换后的 JSON 文本。`icon_code` 保存经过 `MenuIconName` 校验的开放 Lucide 名称，Entity 仍按数据库字符串映射。

## 3. 添加 Mapper

简单单表查询使用 `BaseMapper`。需要 PostgreSQL advisory lock、JSONB 写入或递归 CTE 时声明 Mapper 方法：

```java
@Mapper
public interface SystemMenuMapper extends BaseMapper<SysMenuEntity> {

    int lockMenuCatalog();

    int insertMenu(@Param("menu") SysMenuEntity menu);

    int updateMenu(
            @Param("menuId") Long menuId,
            @Param("parentId") Long parentId,
            @Param("routeCode") String routeCode,
            @Param("permissionCode") String permissionCode,
            @Param("titleLabels") String titleLabels,
            @Param("iconCode") String iconCode,
            @Param("sortOrder") int sortOrder,
            @Param("visible") boolean visible,
            @Param("version") long version);

    int markMenuTreeDeleted(
            @Param("menuId") Long menuId,
            @Param("version") long version);
}
```

## 4. 编写 XML

Mapper XML 位于 `sca4d-server/src/main/resources/mapper`，namespace 必须等于 Mapper 完整类名：

```xml
<mapper namespace="com.sca4d.mapper.SystemMenuMapper">
  <select id="lockMenuCatalog" resultType="int">
    SELECT 1
    FROM pg_advisory_xact_lock(
      hashtext('sca4d:system:menu-catalog'))
  </select>

  <update id="updateMenu">
    UPDATE sys_menu
    SET parent_id = #{parentId},
        permission_code = #{permissionCode},
        route_code = #{routeCode},
        title_labels = CAST(#{titleLabels} AS JSONB),
        icon_code = #{iconCode},
        sort_order = #{sortOrder},
        visible = #{visible},
        updated_at = CURRENT_TIMESTAMP,
        version = version + 1
    WHERE id = #{menuId}
      AND version = #{version}
      AND deleted = 0
  </update>
</mapper>
```

动态列表、联表、聚合和批量查询也放在 XML 中。所有列名和查询条件可以直接从 SQL 审查。

## 5. 编写 Repository

Repository 位于业务包，不放在 `mapper` 包。它把 Mapper 结果转换为 Service 使用的对象：

```java
@Repository
@RequiredArgsConstructor
public class MenuRepository {

    private static final TypeReference<Map<String, String>> TITLE_MAP =
            new TypeReference<>() {
            };

    private final SystemMenuMapper mapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public List<MenuSnapshot> findMenus() {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysMenuEntity::getId),
                        column(SysMenuEntity::getParentId),
                        column(SysMenuEntity::getPermissionCode),
                        column(SysMenuEntity::getRouteCode),
                        column(SysMenuEntity::getTitleLabels)
                                .cast("text")
                                .as(SysMenuEntity::getTitleLabels),
                        column(SysMenuEntity::getIconCode),
                        column(SysMenuEntity::getSortOrder),
                        column(SysMenuEntity::getVisible),
                        column(SysMenuEntity::getVersion))
                .where(SysMenuEntity::getDeleted)
                .eq(0)
                .orderBy(SysMenuEntity::getSortOrder).asc()
                .orderBy(SysMenuEntity::getId).asc();
        return mapper.selectListByQuery(query).stream()
                .map(this::snapshot)
                .toList();
    }

    private MenuSnapshot snapshot(SysMenuEntity entity) {
        return new MenuSnapshot(
                entity.getId(),
                entity.getParentId(),
                entity.getRouteCode(),
                entity.getPermissionCode(),
                restoreTitles(entity.getTitleLabels()),
                entity.getIconCode(),
                entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getVisible()),
                entity.getVersion());
    }

    private MenuTitles restoreTitles(String titleLabels) {
        try {
            return new MenuTitles(objectMapper.readValue(
                    titleLabels, TITLE_MAP));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "菜单标题不是合法 JSON 对象", exception);
        }
    }
}
```

列表固定执行一条 SQL。不要在 `.map(...)` 中再次调用 Mapper 查询父菜单、权限或路由。

## 6. 处理乐观锁

```java
public void updateMenu(
        Long menuId,
        Long parentId,
        String routeCode,
        String permissionCode,
        MenuTitles titles,
        MenuIconName icon,
        boolean visible,
        long version) {
    int updated = mapper.updateMenu(
            menuId,
            parentId,
            routeCode,
            permissionCode,
            encode(titles),
            icon.value(),
            visible,
            version);
    if (updated != 1) {
        throw new ConcurrencyConflictException(
                SystemErrorCode.MENU_CHANGED, "menus");
    }
}
```

版本检查和 UPDATE 在同一条 SQL 中完成。Controller 收到的 409 会提示管理端重新查询菜单树。

## 7. 处理逻辑删除

`sys_menu` 使用 `deleted=0/1`。查询显式包含 `deleted = 0`，唯一索引只约束未删除路由。删除菜单树使用递归 CTE，一次更新根菜单和全部下级菜单。

不是所有表都需要 `deleted`。临时关系可以直接 DELETE；操作日志和消息记录可以按保留策略清理。

## 8. 测试

测试需要检查：

- 从空数据库执行全部迁移；
- routeCode 唯一索引，以及 Repository 对路由、标题和图标类型的写入校验；
- JSONB 标题写入和读取；
- 旧 version 更新返回 0 行；
- 递归删除包含全部下级菜单；
- 菜单列表只执行固定数量 SQL；
- 两个事务同时修改菜单父子关系时不会形成循环。

运行菜单相关测试：

```bash
./gradlew :sca4d-server:test \
  --tests 'com.sca4d.system.*Menu*Test'
```

## 运行结果

空数据库可以执行全部迁移，菜单查询固定为一条 SQL，旧版本更新返回 409，删除操作一次更新整个子树。

## 失败处理

迁移失败时根据 Flyway 报出的版本和 SQL 修正新迁移；不要修改已经在共享数据库执行过的文件。Mapper 绑定失败时核对 XML namespace、方法名和 `@Param` 名称。

## 下一步

Repository 可用后，在 Service 中实现完整业务流程，再[添加 HTTP 接口](./web-api.md)。
