# Service 业务流程

平台和租户公告使用相同的状态转换和写入规则，因此 Service 只提供一组管理方法。`AnnouncementAccessCriteria` 提供当前可操作范围，Controller 将操作者 Principal 传给 Service。

## 前置条件

- 已完成[Mapper 与 Repository](./announcement-persistence.md)。
- `MessagePublisher` 和 `RealtimePublisher` 已由 Spring 注入。
- 写方法通过 Spring Bean 调用，使 `@Transactional` 生效。

## Service 接口

```java
public interface AnnouncementService {

    @NotNull
    PageData<Announcement> pageGlobalPublished(long page, long size);

    @NotNull
    Announcement requireGlobalPublished(@NotNull Long announcementId);

    @NotNull
    PageData<Announcement> pageTenantPublished(long page, long size);

    @NotNull
    Announcement requireTenantPublished(@NotNull Long announcementId);

    @NotNull
    PageData<Announcement> pageManagement(
            @Nullable String search,
            @Nullable AnnouncementStatus status,
            long page,
            long size);

    @NotNull
    Announcement requireManagement(@NotNull Long announcementId);

    @NotNull
    Long create(
            @NotNull String title,
            @NotNull String content,
            @NotNull Principal actor);

    void update(
            @NotNull Long announcementId,
            @NotNull String title,
            @NotNull String content,
            @NotNull Long version);

    void changeStatus(
            @NotNull Long announcementId,
            @NotNull AnnouncementStatus targetStatus,
            @NotNull Long version,
            @NotNull Principal actor);

    void delete(
            @NotNull Long announcementId,
            @NotNull Long version);
}
```

不分别创建 `platformCreate` 和 `tenantCreate`。平台或租户归属已经由 Access Criteria 提供，两种方法的实现会完全相同。

## 公开查询

```java
@Service
@RequiredArgsConstructor
public class DefaultAnnouncementService
        implements AnnouncementService {

    private final AnnouncementRepository repository;
    private final RealtimePublisher realtimePublisher;
    private final MessagePublisher messagePublisher;

    @Override
    public @NotNull PageData<Announcement> pageGlobalPublished(
            long page, long size) {
        return repository.pageGlobalPublished(page, size);
    }

    @Override
    public @NotNull Announcement requireGlobalPublished(
            @NotNull Long announcementId) {
        Announcement announcement =
                repository.findGlobalPublished(announcementId);
        if (announcement == null) {
            throw new BizException(
                    AnnouncementErrorCode.ANNOUNCEMENT_NOT_PUBLISHED);
        }
        return announcement;
    }

    @Override
    public @NotNull PageData<Announcement> pageTenantPublished(
            long page, long size) {
        return repository.pageTenantPublished(page, size);
    }

    @Override
    public @NotNull Announcement requireTenantPublished(
            @NotNull Long announcementId) {
        Announcement announcement =
                repository.findTenantPublished(announcementId);
        if (announcement == null) {
            throw new BizException(
                    AnnouncementErrorCode.ANNOUNCEMENT_NOT_PUBLISHED);
        }
        return announcement;
    }
```

平台公开查询使用固定全局范围；租户公开查询依赖 Controller 已经建立的 READ Criteria。两者都对范围外记录返回“公告不存在”。

## 管理查询

```java
    @Override
    public @NotNull PageData<Announcement> pageManagement(
            @Nullable String search,
            @Nullable AnnouncementStatus status,
            long page,
            long size) {
        String checkedSearch = StringUtils.hasText(search)
                ? search.trim()
                : null;
        return repository.pageManagement(
                checkedSearch, status, page, size);
    }

    @Override
    public @NotNull Announcement requireManagement(
            @NotNull Long announcementId) {
        Announcement announcement =
                repository.findManagement(announcementId);
        if (announcement == null) {
            throw new BizException(
                    AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return announcement;
    }
```

同一方法在平台 Controller 下只看到平台公告，在租户 Controller 下只看到当前租户公告。

## 创建

```java
    @Override
    @Transactional
    public @NotNull Long create(
            @NotNull String title,
            @NotNull String content,
            @NotNull Principal actor) {
        return repository.create(
                title.trim(),
                content.trim(),
                actor.requirePrincipalId());
    }
```

Service 显式接收 actor，用于保存 `created_by_principal_id`。tenantId 不作为参数传入，因为归属范围已经由 `@Access` 解析，Repository 只能从 Criteria 取得。

## 更新

```java
    @Override
    @Transactional
    public void update(
            @NotNull Long announcementId,
            @NotNull String title,
            @NotNull String content,
            @NotNull Long version) {
        repository.update(
                announcementId,
                title.trim(),
                content.trim(),
                version);
    }
```

Repository 的原子 SQL 同时保护租户范围和版本。Service 不需要先查询一次再判断归属。

## 状态变更

事件使用明确类型，tenantId 允许为空以表达平台公告：

```java
public final class AnnouncementStatusChangedEvent {
    private final Long announcementId;
    private final Long tenantId;
    private final String title;
    private final AnnouncementStatus status;

    public AnnouncementStatusChangedEvent(
            @NotNull Long announcementId,
            @Nullable Long tenantId,
            @NotNull String title,
            @NotNull AnnouncementStatus status) {
        this.announcementId = announcementId;
        this.tenantId = tenantId;
        this.title = title;
        this.status = status;
    }

    public @NotNull Long getAnnouncementId() { return announcementId; }
    public @Nullable Long getTenantId() { return tenantId; }
    public @NotNull String getTitle() { return title; }
    public @NotNull AnnouncementStatus getStatus() { return status; }
}
```

```java
    @Override
    @Transactional
    public void changeStatus(
            @NotNull Long announcementId,
            @NotNull AnnouncementStatus targetStatus,
            @NotNull Long version,
            @NotNull Principal actor) {
        Announcement announcement = requireManagement(announcementId);
        AnnouncementStatus expectedStatus = announcement.getStatus();

        boolean allowed = switch (expectedStatus) {
            case DRAFT -> targetStatus == AnnouncementStatus.PUBLISHED;
            case PUBLISHED -> targetStatus == AnnouncementStatus.CLOSED;
            case CLOSED -> targetStatus == AnnouncementStatus.PUBLISHED;
        };
        if (!allowed) {
            throw new BizException(
                    AnnouncementErrorCode.ANNOUNCEMENT_STATUS_CONFLICT);
        }

        if (!repository.changeStatus(
                announcementId,
                expectedStatus,
                targetStatus,
                actor.requirePrincipalId(),
                version)) {
            throw new ConcurrencyConflictException(
                    "公告状态已被其他请求修改",
                    "announcement:" + announcementId);
        }

        AnnouncementStatusChangedEvent event =
                new AnnouncementStatusChangedEvent(
                        announcementId,
                        announcement.getTenantId(),
                        announcement.getTitle(),
                        targetStatus);
        if (announcement.getTenantId() == null) {
            realtimePublisher.sendToTopic(
                    "announcements.global",
                    "announcement.status.changed",
                    event);
        } else {
            realtimePublisher.sendToTenant(
                    announcement.getTenantId(),
                    "announcement.status.changed",
                    event);
        }

        if (targetStatus == AnnouncementStatus.PUBLISHED) {
            MessageCommand command = MessageCommand.of(
                    "sca4dj.event",
                    "announcement.published",
                    "announcement.published",
                    event)
                    .setBusinessKey(announcementId.toString());
            messagePublisher.publishReliable(command);
        }
    }
```

`RealtimePublisher` 在事务存在时提交后发送。平台公告使用公共主题，租户公告定向到当前 tenantId。可靠消息在当前事务写入 Outbox，消费者通过 tenantId 区分公告归属。明确的事件类型也避免平台公告的 null tenantId 与 Map 工厂冲突。

## 删除

```java
    @Override
    @Transactional
    public void delete(
            @NotNull Long announcementId,
            @NotNull Long version) {
        repository.delete(announcementId, version);
    }
}
```

删除范围和版本都由 Repository 原子判断。操作目标由 Controller 的 `@Audit` 注解声明。具体代码见[权限、访问范围与管理端](./announcement-integration#记录操作日志)。

## 检查结果

Service 测试应覆盖创建、查询、更新、发布、关闭、重新发布和删除。事务回滚时公告和 Outbox 都不能留下记录；提交成功后才发送实时刷新。状态不允许或 version 过期时返回冲突，不执行后续操作。

[Request、View 与 Controller](./announcement-web)分别提供平台和租户入口。
