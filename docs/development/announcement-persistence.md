# Mapper 与 Repository

持久化层同时处理两类读取：平台公开公告固定查询 `tenant_id IS NULL`；租户读取和全部管理操作必须消费 `AnnouncementAccessCriteria`。范围检查不能只停留在 Controller。

## 前置条件

- 已完成[数据模型与迁移](./announcement-model.md)。
- `AnnouncementAccessCriteria` 和 `ResourceAccess` 已加入公告包。
- 测试使用 PostgreSQL，不用 H2 代替 JSONB、部分索引和原子更新 SQL。

## Mapper 接口

```java
public interface AnnouncementMapper
        extends BaseMapper<AnnouncementEntity> {

    int updateAnnouncement(
            @Param("announcementId") @NotNull Long announcementId,
            @Param("tenantId") @Nullable Long tenantId,
            @Param("title") @NotNull String title,
            @Param("content") @NotNull String content,
            @Param("version") @NotNull Long version);

    int updateAnnouncementStatus(
            @Param("announcementId") @NotNull Long announcementId,
            @Param("tenantId") @Nullable Long tenantId,
            @Param("expectedStatus") @NotNull AnnouncementStatus expectedStatus,
            @Param("targetStatus") @NotNull AnnouncementStatus targetStatus,
            @Param("publishedByPrincipalId") @NotNull Long publishedByPrincipalId,
            @Param("version") @NotNull Long version);

    int deleteAnnouncement(
            @Param("announcementId") @NotNull Long announcementId,
            @Param("tenantId") @Nullable Long tenantId,
            @Param("version") @NotNull Long version);
}
```

这里的 tenantId 不是客户端输入，而是 `AnnouncementAccessPolicy` 生成的范围。null 明确表示平台公告范围。

## 原子写入 SQL

三个写入都使用相同的归属条件：

```xml
<sql id="announcementScope">
    AND (
        (#{tenantId} IS NULL AND tenant_id IS NULL)
        OR tenant_id = #{tenantId}
    )
</sql>
```

资料更新：

```xml
<update id="updateAnnouncement">
    UPDATE announcement
    SET title = #{title},
        content = #{content},
        updated_at = CURRENT_TIMESTAMP,
        version = version + 1
    WHERE id = #{announcementId}
      <include refid="announcementScope"/>
      AND version = #{version}
      AND deleted = 0
</update>
```

状态变化：

```xml
<update id="updateAnnouncementStatus">
    UPDATE announcement
    SET status = #{targetStatus},
        published_by_principal_id = CASE
            WHEN #{targetStatus} = 'PUBLISHED'
                THEN #{publishedByPrincipalId}
            ELSE published_by_principal_id
        END,
        published_at = CASE
            WHEN #{targetStatus} = 'PUBLISHED'
                THEN CURRENT_TIMESTAMP
            ELSE published_at
        END,
        updated_at = CURRENT_TIMESTAMP,
        version = version + 1
    WHERE id = #{announcementId}
      <include refid="announcementScope"/>
      AND status = #{expectedStatus}
      AND version = #{version}
      AND deleted = 0
</update>
```

逻辑删除：

```xml
<update id="deleteAnnouncement">
    UPDATE announcement
    SET deleted = 1,
        updated_at = CURRENT_TIMESTAMP,
        version = version + 1
    WHERE id = #{announcementId}
      <include refid="announcementScope"/>
      AND version = #{version}
      AND deleted = 0
</update>
```

即使租户 A 猜到租户 B 的 announcementId，SQL 也无法命中。范围、状态、版本和删除标记在同一条语句中判断，不留下检查后被其他请求修改的窗口。

## Repository 依赖

```java
@Repository
@RequiredArgsConstructor
public class AnnouncementRepository {

    private final AnnouncementMapper mapper;
    private final IdGenerator idGenerator;
    private final ResourceAccess resourceAccess;
```

`ResourceAccess` 只在需要平台或租户管理范围的调用中读取。公开平台公告使用固定全局条件，不伪造 CurrentRequest Criteria。

## 平台公开读取

```java
public @NotNull PageData<Announcement> pageGlobalPublished(
        long page, long size) {
    QueryWrapper query = QueryWrapper.create()
            .where(AnnouncementEntity::getTenantId).isNull()
            .and(AnnouncementEntity::getStatus)
            .eq(AnnouncementStatus.PUBLISHED)
            .and(AnnouncementEntity::getDeleted).eq(0)
            .orderBy(AnnouncementEntity::getPublishedAt).desc()
            .orderBy(AnnouncementEntity::getId).desc();
    return PageData.from(
            mapper.paginate(page, size, query),
            Announcement::from);
}

public @Nullable Announcement findGlobalPublished(
        @NotNull Long announcementId) {
    QueryWrapper query = QueryWrapper.create()
            .where(AnnouncementEntity::getId).eq(announcementId)
            .and(AnnouncementEntity::getTenantId).isNull()
            .and(AnnouncementEntity::getStatus)
            .eq(AnnouncementStatus.PUBLISHED)
            .and(AnnouncementEntity::getDeleted).eq(0);
    AnnouncementEntity entity = mapper.selectOneByQuery(query);
    return entity == null ? null : Announcement.from(entity);
}
```

## 租户公开读取

租户产品端 Controller 已经声明 `@Access(READ)`，Repository 取得 tenantId：

```java
public @NotNull PageData<Announcement> pageTenantPublished(
        long page, long size) {
    AnnouncementAccessCriteria criteria = access(AccessAction.READ);
    QueryWrapper query = QueryWrapper.create()
            .where(AnnouncementEntity::getTenantId)
            .eq(criteria.requireTenantId())
            .and(AnnouncementEntity::getStatus)
            .eq(AnnouncementStatus.PUBLISHED)
            .and(AnnouncementEntity::getDeleted).eq(0)
            .orderBy(AnnouncementEntity::getPublishedAt).desc()
            .orderBy(AnnouncementEntity::getId).desc();
    return PageData.from(
            mapper.paginate(page, size, query),
            Announcement::from);
}

public @Nullable Announcement findTenantPublished(
        @NotNull Long announcementId) {
    AnnouncementAccessCriteria criteria = access(AccessAction.READ);
    criteria.requireTarget(announcementId);
    QueryWrapper query = QueryWrapper.create()
            .where(AnnouncementEntity::getId).eq(announcementId)
            .and(AnnouncementEntity::getTenantId)
            .eq(criteria.requireTenantId())
            .and(AnnouncementEntity::getStatus)
            .eq(AnnouncementStatus.PUBLISHED)
            .and(AnnouncementEntity::getDeleted).eq(0);
    AnnouncementEntity entity = mapper.selectOneByQuery(query);
    return entity == null ? null : Announcement.from(entity);
}
```

`requireTenantId()` 使这个方法不能被 PLATFORM Criteria 调用。租户公开接口只读取本租户公告；平台公告由独立入口读取，两个来源不会在 Repository 中隐式合并。

## 管理查询

```java
public @NotNull PageData<Announcement> pageManagement(
        @Nullable String search,
        @Nullable AnnouncementStatus status,
        long page,
        long size) {
    AnnouncementAccessCriteria criteria = access(AccessAction.READ);
    QueryWrapper query = scoped(criteria)
            .and(AnnouncementEntity::getStatus)
            .eq(status, Objects::nonNull);
    if (StringUtils.hasText(search)) {
        query.and(AnnouncementEntity::getTitle)
                .like("%" + search.trim() + "%");
    }
    query.orderBy(AnnouncementEntity::getCreatedAt).desc()
            .orderBy(AnnouncementEntity::getId).desc();
    return PageData.from(
            mapper.paginate(page, size, query),
            Announcement::from);
}

public @Nullable Announcement findManagement(
        @NotNull Long announcementId) {
    AnnouncementAccessCriteria criteria = access(AccessAction.READ);
    criteria.requireTarget(announcementId);
    AnnouncementEntity entity = mapper.selectOneByQuery(
            scoped(criteria)
                    .and(AnnouncementEntity::getId)
                    .eq(announcementId));
    return entity == null ? null : Announcement.from(entity);
}
```

范围构造集中在一个私有方法：

```java
private @NotNull QueryWrapper scoped(
        @NotNull AnnouncementAccessCriteria criteria) {
    QueryWrapper query = QueryWrapper.create()
            .where(AnnouncementEntity::getDeleted).eq(0);
    if (criteria.getTenantId() == null) {
        return query.and(AnnouncementEntity::getTenantId).isNull();
    }
    return query.and(AnnouncementEntity::getTenantId)
            .eq(criteria.getTenantId());
}
```

## 创建与写入

```java
public @NotNull Long create(
        @NotNull String title,
        @NotNull String content,
        @NotNull Long actorPrincipalId) {
    AnnouncementAccessCriteria criteria = access(AccessAction.WRITE);
    Long id = idGenerator.nextId();
    OffsetDateTime now = TimeSupport.now();
    mapper.insert(new AnnouncementEntity()
            .setId(id)
            .setTenantId(criteria.getTenantId())
            .setTitle(title)
            .setContent(content)
            .setStatus(AnnouncementStatus.DRAFT)
            .setCreatedByPrincipalId(actorPrincipalId)
            .setPublishedByPrincipalId(null)
            .setPublishedAt(null)
            .setCreatedAt(now)
            .setUpdatedAt(now)
            .setVersion(0L)
            .setDeleted(0));
    return id;
}

public void update(
        @NotNull Long announcementId,
        @NotNull String title,
        @NotNull String content,
        @NotNull Long version) {
    AnnouncementAccessCriteria criteria = access(AccessAction.WRITE);
    criteria.requireTarget(announcementId);
    if (mapper.updateAnnouncement(
            announcementId,
            criteria.getTenantId(),
            title,
            content,
            version) != 1) {
        throw conflict(announcementId);
    }
}

public boolean changeStatus(
        @NotNull Long announcementId,
        @NotNull AnnouncementStatus expectedStatus,
        @NotNull AnnouncementStatus targetStatus,
        @NotNull Long publishedByPrincipalId,
        @NotNull Long version) {
    AnnouncementAccessCriteria criteria = access(AccessAction.WRITE);
    criteria.requireTarget(announcementId);
    return mapper.updateAnnouncementStatus(
            announcementId,
            criteria.getTenantId(),
            expectedStatus,
            targetStatus,
            publishedByPrincipalId,
            version) == 1;
}

public void delete(
        @NotNull Long announcementId,
        @NotNull Long version) {
    AnnouncementAccessCriteria criteria = access(AccessAction.MANAGE);
    criteria.requireTarget(announcementId);
    if (mapper.deleteAnnouncement(
            announcementId,
            criteria.getTenantId(),
            version) != 1) {
        throw conflict(announcementId);
    }
}
```

辅助方法只处理 Criteria 的固定检查：

```java
private @NotNull AnnouncementAccessCriteria access(
        @NotNull AccessAction required) {
    AnnouncementAccessCriteria criteria =
            resourceAccess.require(AnnouncementAccessCriteria.class);
    criteria.requireAction(required);
    return criteria;
}
```

## 检查结果

Repository 测试应证明平台查询只读取 `tenant_id IS NULL`，租户查询只读取 Criteria 中的 tenantId，旧 version 更新影响 0 行，列表关联查询不会随公告数量增加。出现越权数据时先检查 Mapper SQL 是否使用 tenantId；出现并发覆盖时检查 UPDATE 是否同时包含 ID、tenantId、状态和 version。

[Service 业务流程](./announcement-service)调用这些 Repository 方法完成公告操作。
