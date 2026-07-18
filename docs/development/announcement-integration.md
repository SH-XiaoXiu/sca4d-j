# 权限、访问范围与管理端

平台与租户公告使用不同的权限编码，但共用 `AnnouncementAccessPolicy` 和管理端 API 结构。

## 前置条件

- 已完成[Request、View 与 Controller](./announcement-web.md)。
- 管理端依赖已经安装，后端 `/v3/api-docs` 可以访问。
- 平台和租户各有一个可用于联调的 Principal。

## 平台权限

在现有 `PlatformPermissions` 中增加资源：

```java
private static final String ANNOUNCEMENTS = PLATFORM + ":announcements";

public static final String ANNOUNCEMENTS_READ =
        ANNOUNCEMENTS + ":read";
public static final String ANNOUNCEMENTS_CREATE =
        ANNOUNCEMENTS + ":create";
public static final String ANNOUNCEMENTS_UPDATE =
        ANNOUNCEMENTS + ":update";
public static final String ANNOUNCEMENTS_DELETE =
        ANNOUNCEMENTS + ":delete";
```

```java
PermissionNode.resource(
        ANNOUNCEMENTS, "公告", 30,
        PermissionNode.action(
                ANNOUNCEMENTS_READ, "查看公告", 10),
        PermissionNode.action(
                ANNOUNCEMENTS_CREATE, "创建公告", 20),
        PermissionNode.action(
                ANNOUNCEMENTS_UPDATE, "修改公告", 30),
        PermissionNode.action(
                ANNOUNCEMENTS_DELETE, "删除公告", 40))
```

## 租户权限

在 `TenantPermissions` 中增加独立资源：

```java
private static final String ANNOUNCEMENTS = MODULE + ":announcements";

public static final String ANNOUNCEMENTS_READ =
        ANNOUNCEMENTS + ":read";
public static final String ANNOUNCEMENTS_CREATE =
        ANNOUNCEMENTS + ":create";
public static final String ANNOUNCEMENTS_UPDATE =
        ANNOUNCEMENTS + ":update";
public static final String ANNOUNCEMENTS_DELETE =
        ANNOUNCEMENTS + ":delete";
```

```java
PermissionNode.resource(
        ANNOUNCEMENTS, "公告", 40,
        PermissionNode.action(
                ANNOUNCEMENTS_READ, "查看公告", 10),
        PermissionNode.action(
                ANNOUNCEMENTS_CREATE, "创建公告", 20),
        PermissionNode.action(
                ANNOUNCEMENTS_UPDATE, "修改公告", 30),
        PermissionNode.action(
                ANNOUNCEMENTS_DELETE, "删除公告", 40))
```

新租户的内置管理员角色需要获得这四个 ACTION 权限。其他租户角色可以只分配读取、编辑或删除中的一部分。平台权限与租户权限编码不同，不会因同一个 Subject 同时拥有两种 Principal 而混合。

## AnnouncementAccessCriteria

```java
public final class AnnouncementAccessCriteria
        implements AccessCriteria {

    private final Long tenantId;
    private final AccessAction action;
    private final Long targetAnnouncementId;

    public AnnouncementAccessCriteria(
            @Nullable Long tenantId,
            @NotNull AccessAction action,
            @Nullable Long targetAnnouncementId) {
        this.tenantId = tenantId;
        this.action = action;
        this.targetAnnouncementId = targetAnnouncementId;
    }

    public @Nullable Long getTenantId() {
        return tenantId;
    }

    public @NotNull Long requireTenantId() {
        if (tenantId == null) {
            throw new ResourceAccessDeniedException(
                    "当前公告范围不是租户范围");
        }
        return tenantId;
    }

    public void requireAction(@NotNull AccessAction required) {
        if (!action.permits(required)) {
            throw new ResourceAccessDeniedException(
                    "公告访问等级不足");
        }
    }

    public void requireTarget(@NotNull Long announcementId) {
        if (targetAnnouncementId == null
                || !targetAnnouncementId.equals(announcementId)) {
            throw new ResourceAccessDeniedException(
                    "公告资源 ID 与访问声明不一致");
        }
    }
}
```

tenantId 为 null 在这里不是“没有范围”，而是明确的平台公告范围。Criteria 不保存 QueryWrapper、Controller 或 Session。

## AnnouncementAccessPolicy

`AnnouncementResource` 保存 Cedar 判断和 Criteria 都需要的值：

```java
public final class AnnouncementResource {

    private final Long tenantId;
    private final Long announcementId;

    public AnnouncementResource(
            @Nullable Long tenantId,
            @Nullable Long announcementId) {
        this.tenantId = tenantId;
        this.announcementId = announcementId;
    }

    public @Nullable Long getTenantId() {
        return tenantId;
    }

    public @Nullable Long getAnnouncementId() {
        return announcementId;
    }

    public boolean isCollection() {
        return announcementId == null;
    }
}
```

PLATFORM Principal 可以进入平台公告 Repository；TENANT Principal 只能生成当前租户 Criteria。DEFAULT 和 SYSTEM 没有 permit Rule，Cedar 返回 Deny：

`src/main/resources/cedar/announcement-access/schema.cedar` 先定义 `Principal`、`AnnouncementResource` 和三个 AccessAction；Java DSL 只声明 Policy：

```java
@Component
public class AnnouncementAccessPolicy
        implements ResourceAccessPolicy<AnnouncementResource, AnnouncementAccessCriteria> {

    private static final Policy<AnnouncementResource> POLICY = Rules.policy(
            "announcement-access",
            AnnouncementResource.class,
            "/cedar/announcement-access/schema.cedar",
            List.of(
                    Rules.<AnnouncementResource>principal(PrincipalType.PLATFORM),
                    Rules.tenant(
                            AnnouncementResource::getTenantId,
                            AnnouncementResource::isCollection)),
            List.of());

    @Override
    public @NotNull Policy<AnnouncementResource> policy() {
        return POLICY;
    }

    @Override
    public @NotNull Class<AnnouncementAccessCriteria> criteriaType() {
        return AnnouncementAccessCriteria.class;
    }

    @Override
    public @NotNull AnnouncementResource resource(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @Nullable Object resourceId) {
        Long announcementId = null;
        if (resourceId != null) {
            try {
                announcementId = Long.valueOf(resourceId.toString());
            } catch (NumberFormatException exception) {
                throw new ResourceAccessDeniedException(
                        "公告资源 ID 无效");
            }
        }
        Principal principal = request.requirePrincipal();
        if (principal.getPrincipalType() == PrincipalType.PLATFORM) {
            return new AnnouncementResource(null, announcementId);
        }
        if (principal.getPrincipalType() == PrincipalType.TENANT
                && principal.getTenantId() != null) {
            return new AnnouncementResource(
                    principal.getTenantId(), announcementId);
        }
        return new AnnouncementResource(null, announcementId);
    }

    @Override
    public @NotNull AnnouncementAccessCriteria criteria(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @NotNull AnnouncementResource resource) {
        return new AnnouncementAccessCriteria(
                resource.getTenantId(), action, resource.getAnnouncementId());
    }
}
```

该示例的公告归属由 Repository SQL 判断，因此 `resource(...)` 不查询数据库。需要按 Owner、Membership 或 ACL 判断单资源时，`resource(...)` 应使用固定次数查询读取关系，`criteria(...)` 复用结果。目标公告是否属于 Criteria 中的 tenantId，仍由 Repository 的查询或原子 SQL 确认。

## 权限和范围如何配合

以租户删除公告为例：

```text
TENANT Principal 选择成功
  -> SessionPermission 包含 tenant:announcements:delete
  -> @Access 读取 AnnouncementResource(tenantId=1001, announcementId=9001)
  -> Cedar 允许 TENANT Principal 使用自己的 tenantId
  -> AnnouncementAccessCriteria(tenantId=1001, action=MANAGE, target=9001)
  -> Repository DELETE 条件包含
       id=9001 AND tenant_id=1001 AND version=? AND deleted=0
```

权限不足在进入业务方法前返回 403；公告不属于该租户时 SQL 或查询无法命中，对外返回不存在或并发冲突，不泄露其他租户数据。

## 记录操作日志

更新、状态变化和删除接口使用 `targetId = "#announcementId"`。创建接口从返回值读取主键：

```java
@Audit(
        module = "platform.announcements",
        action = "create",
        targetType = "announcement",
        targetId = "#result.data")
```

操作还必须加入审计目录：

```java
@Component
public class AnnouncementAuditOperations
        implements AuditOperationContributor {

    @Override
    public @NotNull List<AuditOperationDefinition> auditOperations() {
        return List.of(
                AuditOperationDefinition.standard(
                        "platform.announcements", 60, "create", 10),
                AuditOperationDefinition.standard(
                        "platform.announcements", 60, "update", 20),
                AuditOperationDefinition.standard(
                        "platform.announcements", 60, "change-status", 30),
                AuditOperationDefinition.standard(
                        "platform.announcements", 60, "delete", 40),
                AuditOperationDefinition.standard(
                        "tenant.announcements", 70, "create", 10),
                AuditOperationDefinition.standard(
                        "tenant.announcements", 70, "update", 20),
                AuditOperationDefinition.standard(
                        "tenant.announcements", 70, "change-status", 30),
                AuditOperationDefinition.standard(
                        "tenant.announcements", 70, "delete", 40));
    }
}
```

在 `i18n/audit.properties` 中添加默认简体中文消息：

```properties
audit.module.platform.announcements=平台公告
audit.module.tenant.announcements=租户公告
audit.summary.platform.announcements.create=创建平台公告
audit.summary.platform.announcements.update=修改平台公告
audit.summary.platform.announcements.change-status=变更平台公告状态
audit.summary.platform.announcements.delete=删除平台公告
audit.summary.tenant.announcements.create=创建租户公告
audit.summary.tenant.announcements.update=修改租户公告
audit.summary.tenant.announcements.change-status=变更租户公告状态
audit.summary.tenant.announcements.delete=删除租户公告
```

在 `i18n/audit_en_US.properties` 中添加英文消息：

```properties
audit.module.platform.announcements=Platform announcements
audit.module.tenant.announcements=Tenant announcements
audit.summary.platform.announcements.create=Create platform announcement
audit.summary.platform.announcements.update=Update platform announcement
audit.summary.platform.announcements.change-status=Change platform announcement status
audit.summary.platform.announcements.delete=Delete platform announcement
audit.summary.tenant.announcements.create=Create tenant announcement
audit.summary.tenant.announcements.update=Update tenant announcement
audit.summary.tenant.announcements.change-status=Change tenant announcement status
audit.summary.tenant.announcements.delete=Delete tenant announcement
```

`audit.action.create`、`update`、`change-status` 和 `delete` 已由项目提供，不重复添加。

`AuditAspect` 在 Controller 返回后计算注解表达式，并将当前 Subject、Account、Principal、traceId、目标 ID 和执行结果写入 `sys_log`。

## 管理端类型

```ts
export type AnnouncementStatus = "DRAFT" | "PUBLISHED" | "CLOSED";

export interface AnnouncementPageQuery extends PageQuery {
  search?: string;
  status?: AnnouncementStatus;
}

export interface AnnouncementManagementView {
  announcementId: Id;
  tenantId: Id | null;
  title: string;
  content: string;
  status: AnnouncementStatus;
  createdByPrincipalId: Id;
  publishedByPrincipalId: Id | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: Id;
}

export interface AnnouncementCreateInput {
  title: string;
  content: string;
  idempotencyKey: string;
}

export interface AnnouncementUpdateInput {
  title: string;
  content: string;
  version: Id;
}
```

## 管理端 API

平台和租户接口结构相同，可以复用同一个 API 工厂：

```ts
function createAnnouncementManagementApi(
  client: ApiClient,
  root: string
) {
  return {
    page(
      query: AnnouncementPageQuery,
      signal?: AbortSignal
    ): Promise<PageData<AnnouncementManagementView>> {
      return client.get(root, query, signal);
    },
    detail(id: Id): Promise<AnnouncementManagementView> {
      return client.get(`${root}/${id}`);
    },
    create(input: AnnouncementCreateInput): Promise<Id> {
      return client.post(root, input);
    },
    update(id: Id, input: AnnouncementUpdateInput): Promise<void> {
      return client.put(`${root}/${id}`, input);
    },
    changeStatus(
      id: Id,
      status: AnnouncementStatus,
      version: Id
    ): Promise<void> {
      return client.put(`${root}/${id}/status`, { status, version });
    },
    delete(id: Id, version: Id): Promise<void> {
      return client.request({
        path: `${root}/${id}`,
        method: "DELETE",
        body: { version }
      });
    }
  };
}

export function createPlatformAnnouncementApi(client: ApiClient) {
  return createAnnouncementManagementApi(
    client, "/mg/platform/announcements");
}

export function createTenantAnnouncementApi(
  client: ApiClient,
  tenantId: Id
) {
  return createAnnouncementManagementApi(
    client, `/mg/tenant/tenants/${tenantId}/announcements`);
}
```

工厂只复用完全相同的 HTTP 动作，不负责决定 Principal 或权限。API Client 的 token 对应 Session，后端仍按路径和 Controller 声明完成租户选择。

## 路由权限

平台工作区：

```ts
meta: {
  permission: "platform:announcements:read",
  principalType: "PLATFORM",
  workspace: true
}
```

租户工作区：

```ts
meta: {
  permission: "tenant:announcements:read",
  principalType: "TENANT",
  tenantParam: "tenantId",
  workspace: true
}
```

创建、编辑和删除按钮分别检查对应动作权限。前端判断只负责交互，后端注解始终保留。

## 发布事件

平台公告状态事件发送到 `announcements.global` Topic；租户公告发送到 `RealtimeTarget.tenant(tenantId)`。可靠消息统一使用 `announcement.published`，Payload 中的可空 tenantId 区分平台和租户公告。

## 检查结果

平台角色只能分配平台公告权限，租户角色只能分配租户公告权限。管理端在切换租户后使用新的 tenantId，请求失败、无权限和 409 都有可见反馈。发布公告后，正确租户的在线连接收到刷新事件，操作日志保存公告 ID。

按[测试与验收](./announcement-verification)检查跨租户读取、写入、消息和操作日志。
