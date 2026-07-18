# Request、View 与 Controller

HTTP 层需要提供四组入口：平台公告阅读、租户公告阅读、平台管理和租户管理。Request 与 View 可以复用，Principal、Permission、Access 和路由前缀各自明确声明。

## 前置条件

- 已完成[Service 业务流程](./announcement-service.md)。
- 平台和租户的权限常量已经确定。
- 公告查询、创建、更新、状态修改和删除方法已经可以由 Service 测试调用。

## Request

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementPageRequest extends PageQuery {

    @SafeText(max = 100)
    private String search;

    private AnnouncementStatus status;
}

@Data
public class AnnouncementCreateRequest implements IdempotentRequest {

    @NotBlank
    @SafeText(max = 120)
    private String title;

    @NotBlank
    @Size(max = 20_000)
    private String content;

    @NotBlank
    @BizId(max = 128)
    private String idempotencyKey;
}

@Data
public class AnnouncementUpdateRequest implements VersionedRequest {

    @NotBlank
    @SafeText(max = 120)
    private String title;

    @NotBlank
    @Size(max = 20_000)
    private String content;

    @NotNull
    @PositiveOrZero
    private Long version;
}

@Data
public class AnnouncementStatusRequest implements VersionedRequest {

    @NotNull
    private AnnouncementStatus status;

    @NotNull
    @PositiveOrZero
    private Long version;
}

@Data
public class AnnouncementDeleteRequest implements VersionedRequest {

    @NotNull
    @PositiveOrZero
    private Long version;
}
```

Request 中没有 tenantId。平台或租户归属由当前 Principal 与 `@Access` 决定，不能让客户端在创建 Body 中选择。

正文允许换行，所以只用 `@Size` 控制长度。项目还需要明确正文是纯文本、Markdown 还是受限 HTML；HTML 必须在服务端白名单清洗。

## View

产品端 View 只返回阅读所需字段：

```java
@Data
public class AnnouncementView {
    private Long announcementId;
    private Long tenantId;
    private String title;
    private String content;
    private OffsetDateTime publishedAt;

    public static @NotNull AnnouncementView from(
            @NotNull Announcement announcement) {
        AnnouncementView view = new AnnouncementView();
        view.setAnnouncementId(announcement.getAnnouncementId());
        view.setTenantId(announcement.getTenantId());
        view.setTitle(announcement.getTitle());
        view.setContent(announcement.getContent());
        view.setPublishedAt(announcement.getPublishedAt());
        return view;
    }
}
```

管理端需要归属、状态、操作者和版本：

```java
@Data
public class AnnouncementManagementView {
    private Long announcementId;
    private Long tenantId;
    private String title;
    private String content;
    private AnnouncementStatus status;
    private Long createdByPrincipalId;
    private Long publishedByPrincipalId;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    public static @NotNull AnnouncementManagementView from(
            @NotNull Announcement announcement) {
        AnnouncementManagementView view = new AnnouncementManagementView();
        BeanUtil.copyProperties(announcement, view);
        return view;
    }
}
```

Entity 的 `deleted` 不进入任何 View。

## 平台公告阅读

```java
@RestController
@RequestMapping("/announcements")
@AuthPrincipal
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    public @NotNull ApiResponse<PageData<AnnouncementView>> page(
            @Valid @ModelAttribute PageQuery request) {
        return ApiResponse.ok(service.pageGlobalPublished(
                request.page(), request.size())
                .map(AnnouncementView::from));
    }

    @GetMapping("/{announcementId}")
    public @NotNull ApiResponse<AnnouncementView> detail(
            @PathVariable @NotNull Long announcementId) {
        return ApiResponse.ok(AnnouncementView.from(
                service.requireGlobalPublished(announcementId)));
    }
}
```

`@AuthPrincipal` 使用默认选择器取得 `DEFAULT Principal`。这里只查询 `tenant_id IS NULL` 的已发布公告。

## 租户公告阅读

```java
@RestController
@RequestMapping("/tenants/{tenantId}/announcements")
@AuthPrincipal(
        types = PrincipalType.TENANT,
        selection = TenantPrincipalSelection.class,
        tenantId = "#path.tenantId")
@RequiredArgsConstructor
public class TenantAnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    @Access(
            policy = AnnouncementAccessPolicy.class,
            action = AccessAction.READ)
    public @NotNull ApiResponse<PageData<AnnouncementView>> page(
            @PathVariable @NotNull Long tenantId,
            @Valid @ModelAttribute PageQuery request) {
        return ApiResponse.ok(service.pageTenantPublished(
                request.page(), request.size())
                .map(AnnouncementView::from));
    }

    @GetMapping("/{announcementId}")
    @Access(
            policy = AnnouncementAccessPolicy.class,
            action = AccessAction.READ,
            resourceId = "#announcementId")
    public @NotNull ApiResponse<AnnouncementView> detail(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long announcementId) {
        return ApiResponse.ok(AnnouncementView.from(
                service.requireTenantPublished(announcementId)));
    }
}
```

知道 tenantId 不等于拥有访问权。选择器必须从 Session 找到该租户的 TENANT Principal，Policy 再把 tenantId 固化进 Criteria。

## 平台管理 Controller

```java
@RestController
@RequestMapping("/mg/platform/announcements")
@AuthPrincipal(
        types = PrincipalType.PLATFORM,
        selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformAnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    @RequirePermission(PlatformPermissions.ANNOUNCEMENTS_READ)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.READ)
    public ApiResponse<PageData<AnnouncementManagementView>> page(
            @Valid @ModelAttribute AnnouncementPageRequest request) {
        return ApiResponse.ok(service.pageManagement(
                request.getSearch(), request.getStatus(),
                request.page(), request.size())
                .map(AnnouncementManagementView::from));
    }

    @GetMapping("/{announcementId}")
    @RequirePermission(PlatformPermissions.ANNOUNCEMENTS_READ)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.READ,
            resourceId = "#announcementId")
    public ApiResponse<AnnouncementManagementView> detail(
            @PathVariable Long announcementId) {
        return ApiResponse.ok(AnnouncementManagementView.from(
                service.requireManagement(announcementId)));
    }

    @PostMapping
    @Idempotent(operation = "platform.announcements.create")
    @Audit(module = "platform.announcements", action = "create",
            targetType = "announcement", targetId = "#result.data")
    @RequirePermission(PlatformPermissions.ANNOUNCEMENTS_CREATE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.WRITE)
    public ApiResponse<Long> create(
            @Valid @RequestBody AnnouncementCreateRequest request,
            @AuthPrincipal Principal actor) {
        return ApiResponse.ok(service.create(
                request.getTitle(), request.getContent(), actor));
    }

    @PutMapping("/{announcementId}")
    @Audit(module = "platform.announcements", action = "update",
            targetType = "announcement", targetId = "#announcementId")
    @RequirePermission(PlatformPermissions.ANNOUNCEMENTS_UPDATE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.WRITE,
            resourceId = "#announcementId")
    public ApiResponse<Void> update(
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementUpdateRequest request) {
        service.update(announcementId, request.getTitle(),
                request.getContent(), request.getVersion());
        return ApiResponse.ok();
    }

    @PutMapping("/{announcementId}/status")
    @Audit(module = "platform.announcements", action = "change-status",
            targetType = "announcement", targetId = "#announcementId")
    @RequirePermission(PlatformPermissions.ANNOUNCEMENTS_UPDATE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.WRITE,
            resourceId = "#announcementId")
    public ApiResponse<Void> changeStatus(
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementStatusRequest request,
            @AuthPrincipal Principal actor) {
        service.changeStatus(announcementId, request.getStatus(),
                request.getVersion(), actor);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{announcementId}")
    @Audit(module = "platform.announcements", action = "delete",
            targetType = "announcement", targetId = "#announcementId")
    @RequirePermission(PlatformPermissions.ANNOUNCEMENTS_DELETE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.MANAGE,
            resourceId = "#announcementId")
    public ApiResponse<Void> delete(
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementDeleteRequest request) {
        service.delete(announcementId, request.getVersion());
        return ApiResponse.ok();
    }
}
```

平台 Principal 由 Policy 转成 `tenantId = null` 的范围，因此管理列表和所有写入只作用于平台公告。

## 租户管理 Controller

租户端保持相同 Service 调用，只替换入口声明：

```java
@RestController
@RequestMapping("/mg/tenant/tenants/{tenantId}/announcements")
@AuthPrincipal(
        types = PrincipalType.TENANT,
        selection = TenantPrincipalSelection.class,
        tenantId = "#path.tenantId")
@RequiredArgsConstructor
public class TenantAnnouncementManagementController {

    private final AnnouncementService service;

    @GetMapping
    @RequirePermission(TenantPermissions.ANNOUNCEMENTS_READ)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.READ)
    public ApiResponse<PageData<AnnouncementManagementView>> page(
            @PathVariable Long tenantId,
            @Valid @ModelAttribute AnnouncementPageRequest request) {
        return ApiResponse.ok(service.pageManagement(
                request.getSearch(), request.getStatus(),
                request.page(), request.size())
                .map(AnnouncementManagementView::from));
    }

    @GetMapping("/{announcementId}")
    @RequirePermission(TenantPermissions.ANNOUNCEMENTS_READ)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.READ,
            resourceId = "#announcementId")
    public ApiResponse<AnnouncementManagementView> detail(
            @PathVariable Long tenantId,
            @PathVariable Long announcementId) {
        return ApiResponse.ok(AnnouncementManagementView.from(
                service.requireManagement(announcementId)));
    }

    @PostMapping
    @Idempotent(operation = "tenant.announcements.create")
    @Audit(module = "tenant.announcements", action = "create",
            targetType = "announcement", targetId = "#result.data")
    @RequirePermission(TenantPermissions.ANNOUNCEMENTS_CREATE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.WRITE)
    public ApiResponse<Long> create(
            @PathVariable Long tenantId,
            @Valid @RequestBody AnnouncementCreateRequest request,
            @AuthPrincipal Principal actor) {
        return ApiResponse.ok(service.create(
                request.getTitle(), request.getContent(), actor));
    }

    @PutMapping("/{announcementId}")
    @Audit(module = "tenant.announcements", action = "update",
            targetType = "announcement", targetId = "#announcementId")
    @RequirePermission(TenantPermissions.ANNOUNCEMENTS_UPDATE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.WRITE,
            resourceId = "#announcementId")
    public ApiResponse<Void> update(
            @PathVariable Long tenantId,
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementUpdateRequest request) {
        service.update(announcementId, request.getTitle(),
                request.getContent(), request.getVersion());
        return ApiResponse.ok();
    }

    @PutMapping("/{announcementId}/status")
    @Audit(module = "tenant.announcements", action = "change-status",
            targetType = "announcement", targetId = "#announcementId")
    @RequirePermission(TenantPermissions.ANNOUNCEMENTS_UPDATE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.WRITE,
            resourceId = "#announcementId")
    public ApiResponse<Void> changeStatus(
            @PathVariable Long tenantId,
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementStatusRequest request,
            @AuthPrincipal Principal actor) {
        service.changeStatus(announcementId, request.getStatus(),
                request.getVersion(), actor);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{announcementId}")
    @Audit(module = "tenant.announcements", action = "delete",
            targetType = "announcement", targetId = "#announcementId")
    @RequirePermission(TenantPermissions.ANNOUNCEMENTS_DELETE)
    @Access(policy = AnnouncementAccessPolicy.class,
            action = AccessAction.MANAGE,
            resourceId = "#announcementId")
    public ApiResponse<Void> delete(
            @PathVariable Long tenantId,
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementDeleteRequest request) {
        service.delete(announcementId, request.getVersion());
        return ApiResponse.ok();
    }
}
```

tenantId 不传入 Service。`TenantPrincipalSelection` 先验证成员关系，`AnnouncementAccessPolicy` 再把当前租户写入 Criteria，Repository 最终将它写入 SQL。

## 检查结果

Controller 测试应覆盖匿名读取、平台管理、租户管理、400、401、403、404 和 409。OpenAPI 中不能出现 `Subject`、`Account`、`Principal` 或 `AnnouncementAccessCriteria` 参数，创建 Request 也不能包含 tenantId 或操作者 ID。

[权限、访问范围与管理端](./announcement-integration)添加 Policy、权限目录和页面。
