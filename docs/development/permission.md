# 添加权限和数据访问检查

以下步骤为租户成员接口添加功能权限，并限制当前 Principal 可以查询和修改的租户。

## 前置条件

- 接口已经有 Controller 和 Repository。
- 已确定哪些操作需要 read、create、update 或 delete 权限。
- 已确定 PLATFORM、TENANT 和 SYSTEM Principal 分别可以访问哪些记录。

## 1. 定义权限编码

权限类实现 `PermissionModule`，并使用 `<domain>:<resource>:<action>` 编码：

```java
@Component
public class TenantPermissions implements PermissionModule {

    public static final String TENANT_MEMBERS_READ =
            "tenant:members:read";
    public static final String TENANT_MEMBERS_CREATE =
            "tenant:members:create";
    public static final String TENANT_MEMBERS_UPDATE =
            "tenant:members:update";

    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module("tenant", "租户", 200,
                PermissionNode.resource("tenant:members", "成员", 100,
                        PermissionNode.action(
                                TENANT_MEMBERS_READ, "查看成员", 100),
                        PermissionNode.action(
                                TENANT_MEMBERS_CREATE, "新增成员", 200),
                        PermissionNode.action(
                                TENANT_MEMBERS_UPDATE, "修改成员", 300)));
    }
}
```

Spring 会收集全部 `PermissionModule`，`PermissionCatalog` 会检查重复编码。权限节点中的 `ACTION` 可以分配给角色，`MODULE` 和 `RESOURCE` 只用于目录显示。由 `db/seed/iam-defaults.sql` 标记为默认授予的节点不会出现在角色可分配目录中。

## 2. 在 Controller 检查功能权限

```java
@GetMapping
@RequirePermission(TenantPermissions.TENANT_MEMBERS_READ)
@Access(
        policy = TenantAccessPolicy.class,
        action = AccessAction.READ,
        resourceId = "#tenantId")
public ApiResponse<PageData<TenantMemberView>> page(
        @PathVariable Long tenantId,
        @Valid TenantMemberPageRequest request) {
    return ApiResponse.ok(TenantMemberView.from(
            tenantMemberService.page(tenantId, request)));
}
```

`@RequirePermission` 中的多个权限和角色按 AND 检查。不要使用 `*`，也不要在 Controller 中手工读取角色列表。

## 3. 定义查询条件类型

租户及其成员、角色、字典等租户归属资源统一使用 `TenantAccessCriteria`。Criteria 保存 Repository 写入 SQL 所需的数据：

```java
public class TenantAccessCriteria implements AccessCriteria {
    private final boolean allData;
    private final AccessAction action;
    private final Set<Long> tenantIds;
    private final Set<Long> excludedTenantIds;
    private final Long targetTenantId;

    public TenantAccessCriteria(
            boolean allData,
            AccessAction action,
            Set<Long> tenantIds,
            Set<Long> excludedTenantIds,
            Long targetTenantId) {
        this.allData = allData;
        this.action = action;
        this.tenantIds = Set.copyOf(tenantIds);
        this.excludedTenantIds = Set.copyOf(excludedTenantIds);
        this.targetTenantId = targetTenantId;
    }

    public boolean isAllData() {
        return allData;
    }

    public AccessAction getAction() {
        return action;
    }

    public Set<Long> getTenantIds() {
        return tenantIds;
    }

    public Set<Long> getExcludedTenantIds() {
        return excludedTenantIds;
    }

    public Long getTargetTenantId() {
        return targetTenantId;
    }
}
```

`allData=true` 表示默认规则覆盖全部租户，`excludedTenantIds` 保存当前动作级别不足的覆盖租户。`allData=false` 时只允许 `tenantIds`，空集合表示不能访问任何租户。

## 4. 声明 Policy 和 ResourceAccessPolicy

常见关系使用 `Rules` 声明。下面的 Policy 允许 SYSTEM；允许 TENANT 访问自己的租户或经过 Criteria 过滤的集合；允许具有租户 ACL 的 PLATFORM Principal：

先在 `src/main/resources/cedar/tenant-access/schema.cedar` 定义 `Principal`、`TenantResource`、READ/WRITE/MANAGE Action 和 Context，再让 DSL 引用该模型：

```java
private static final Policy<TenantResource> POLICY = Rules.policy(
        "tenant-access",
        TenantResource.class,
        "/cedar/tenant-access/schema.cedar",
        List.of(
                Rules.<TenantResource>principal(PrincipalType.SYSTEM),
                Rules.tenant(TenantResource::getTenantId, TenantResource::isCollection),
                Rules.all(
                        Rules.<TenantResource>principal(PrincipalType.PLATFORM),
                        Rules.grant(TenantResource::isGranted))),
        List.of());
```

Schema 在 `static final Policy` 初始化时读取、校验并缓存，不进入请求期 IO。业务 Policy 实现 `policy()`、`resource(...)`、`criteria(...)` 和 `criteriaType()`。`resource(...)` 使用固定次数查询读取目标资源、Membership、Owner 或 ACL；`criteria(...)` 复用该资源，不再次查询。完整实现见 `com.sca4d.tenant.policy.TenantAccessPolicy`。

`TenantAccessPolicy` 是租户 ACL 的唯一请求级入口。PLATFORM Principal 从 `TenantAccessService` 读取当前动作对应的 ACL；TENANT Principal 只能使用自己的 tenantId；SYSTEM 由 Rule 明确允许。业务子资源不再定义平行的成员、角色或字典租户策略。

需要 Cedar Entity 层级、扩展类型或显式 forbid 文件时，实现 `CedarPolicy<R>`，把 Schema 和 Policy 放在 classpath。每条静态策略必须声明唯一的 `@id`，并通过 `Policy.cedar(...)` 创建 Policy。不要在 Controller、Service 或 Repository 中拼接 Cedar 字符串。

## 5. 为单条接口传入资源 ID

```java
@PutMapping("/{principalId}/status")
@RequirePermission(TenantPermissions.TENANT_MEMBERS_UPDATE)
@Access(
        policy = TenantAccessPolicy.class,
        action = AccessAction.WRITE,
        resourceId = "#tenantId")
public ApiResponse<Void> changeStatus(
        @PathVariable Long tenantId,
        @PathVariable Long principalId,
        @Valid @RequestBody TenantMemberStatusRequest request) {
    tenantMemberService.changeStatus(
            tenantId, principalId, request.getStatus(), request.getVersion());
    return ApiResponse.ok();
}
```

`resourceId` 使用 Spring Expression Language。成员是租户的嵌套子资源，因此 ACL 绑定父资源 `tenantId`；Repository 再用 `(tenantId, principalId)` 约束具体成员。表达式引用的方法参数必须由 `-parameters` 编译选项保留名称；根构建已经启用该选项。

## 6. 在 Repository 中使用 Criteria

```java
@Repository
@RequiredArgsConstructor
public class TenantMemberRepository {

    private final ResourceAccess resourceAccess;
    private final TenantMemberMapper mapper;

    public PageData<TenantMember> page(
            Long tenantId, TenantMemberPageRequest request) {
        TenantAccessCriteria access = resourceAccess.require(
                TenantAccessCriteria.class);
        if (!access.getAction().permits(AccessAction.READ)
                || !tenantId.equals(access.getTargetTenantId())) {
            throw new ResourceAccessDeniedException();
        }
        return mapper.page(tenantId, request);
    }
}
```

Repository 先确认 `targetTenantId`、访问等级和租户包含/排除集合，再把 tenantId 与具体子资源主键共同放入查询或原子写条件。更新和删除语句也要使用相同租户条件；只在 Service 中先查询一次，然后执行不带范围的 UPDATE，会在并发修改时产生错误结果。

## 7. 刷新用户状态

角色、权限、Principal 状态或领域访问范围修改后，相关 Session 权限缓存需要失效。当前请求使用以下 Header 通知管理端重新请求 `/me`：

```text
X-Client-State-Refresh: me
X-Client-State-Version: <version>
```

`ClientState.refresh(AuthenticationClientStateTargets.ME)` 不会同时输出 `X-Client-State-Stale`。无法指定刷新目标时单独调用 `ClientState.stale()`。受影响 Subject 的其他在线连接通过 `authentication.session.changed` 重新读取 `/me`。

## 8. 添加测试

至少覆盖：

- 有功能权限且租户在访问范围中；
- 没有功能权限；
- 有功能权限但 ACL 只允许更低动作；
- PLATFORM Principal 的默认规则覆盖全部租户；
- 默认规则下的单租户低等级覆盖；
- 没有默认规则且租户授权集合为空；
- TENANT Principal 路径 tenantId 与当前租户不一致；
- SYSTEM 允许和拒绝；
- 修改角色或租户 ACL 后 `/me` 返回新权限和菜单。

运行：

```bash
./gradlew :sca4d-server:test \
  --tests 'com.sca4d.tenant.*Access*Test'
```

## 运行结果

有功能权限的 Principal 仍只能查询和修改 ACL 为当前动作生成的 Criteria 所允许的数据；空授权返回空结果，跨租户或动作等级不足的请求被拒绝。

## 失败处理

接口意外返回空数据时检查 Policy 生成的 Criteria 和 Mapper SQL；接口意外返回全部数据时立即检查空集合处理和查询条件是否遗漏。

## 下一步

写接口需要防止并发覆盖或重复执行时，继续[添加并发控制和操作日志](./concurrency-and-audit.md)。
