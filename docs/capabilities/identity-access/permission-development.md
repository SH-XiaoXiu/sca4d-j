# 操作与数据访问规则

本页规定操作目录、操作授权、Policy 和 Repository 查询条件的开发要求。本页适用于新增 Controller 操作、权限策略和数据范围的开发者。读完后，开发者能确定每个检查应放在哪一层，以及需要哪些验收测试。

## 操作目录

Controller 和消息处理方法使用的操作，必须进入 OperationCatalog。

新增编码使用 `<module>:<resource>:<action>` 格式。MODULE 锚点在应用中唯一。RESOURCE 和 ACTION 使用局部段，由 `OperationNode.module(...)` 展开为完整编码。

完整编译期常量由同一组模块、资源和 `Action` 段组成，并放在所属的 OperationModule 实现中。Controller 不得手写同义编码。

只有 `OperationNode.OperationAction` 才能直接授予角色。一个模块参与 RBAC 时，实现 RbacOperationModule，明确回答自己的 Operation 能否授予指定的 RbacDomain。不得在 Controller 或角色页面另建领域白名单。

## 操作与资源访问

固定 Operation 声明只接收操作编码，不接收角色名。多个编码必须全部满足。

业务 Resource 的 PermissionPolicy 和 ResourceScope 处理具体记录与集合范围。禁止把租户 ID 拼进操作编码。禁止把业务数据范围预装进 Session。

普通 Principal 的 Operation 检查，只读取当前 Principal 在 Session 中的 Resource。固定入口的权限来自 Session 中预装的 Operation，不随业务数据增长；业务数据范围不进入 Session，由 Policy 在请求中读取，并以 ResourceScope 传给 Repository。

SYSTEM Principal 的固定 Operation 检查必须拒绝。业务 Policy 可以根据资源类型和 Action 明确允许 SYSTEM。

## Policy

每个领域 Policy 必须明确 PLATFORM、TENANT、SYSTEM 和不支持的 Principal。Policy 返回 `PermissionDecision`：允许时附带 ResourceScope，拒绝时给出原因。源码中未定义 Criteria 类型。

异常、未知类型、空输入和空集合必须失败关闭。

## Repository 查询条件

所有受范围保护的读写，必须由 Repository 将 Criteria 放入 SQL。禁止查询后在内存中过滤。禁止 Controller 做一遍检查后，执行无范围的写入。

## 领域访问范围

范围模型必须由具体领域拥有，并显式失败关闭。

租户领域使用 PLATFORM Principal 专用 ACL，按 `READ/WRITE/MANAGE` 保存动作上限。缺少默认规则且授权集合为空，只能表示无范围。

范围修改必须更新版本，并触发客户端刷新。

## 前端显示

前端读取 `/me.permissions` 只控制按钮和页面是否显示。

后端每次请求仍必须检查登录状态、选择 Principal、operation code 和数据范围。

## 验收要求

- OperationCatalogTest 覆盖锚点展开、跨模块局部码复用、重复完整编码、非法段和领域 Operation 子集的筛选。
- PermissionSelectionRegistryTest 覆盖模块领域声明、同模块不同 Operation 的领域差异和无可授予领域的启动失败。
- 未登记 operation、未知资源类型和不支持的 Action 的启动校验，以及普通 Session、SYSTEM 拒绝 operation、业务 Policy 允许 SYSTEM 的调用期判断：源码中未定义对应的 ResourceCatalogBootstrapTest 与 AccessAspectTest，需要时另行新增。
- HTTP 路由或 View 改动后，从当前应用重新生成 OpenAPI，并运行管理端 `pnpm api:check`。

## 示例

下例为「工作区文档」声明资源范围、Policy 和 Repository 查询。资源类型为 `WORKSPACE:DOCUMENT`。Policy 读取当前 Principal 所属的工作区，把结果写入 PermissionDecision。Repository 通过 CurrentResourceScope 取得范围，并放入 SQL 条件。

资源范围保存当前 Principal 可以访问的工作区标识。

```java
public class WorkspaceDocumentScope implements ResourceScope {

    public static final TreeCode TYPE = TreeCode.ofColon("WORKSPACE:DOCUMENT");

    private final Set<Long> workspaceIds;

    public WorkspaceDocumentScope(Set<Long> workspaceIds) {
        this.workspaceIds = Set.copyOf(workspaceIds);
    }

    public Set<Long> workspaceIds() {
        return workspaceIds;
    }

    @Override
    public TreeCode resourceType() {
        return TYPE;
    }
}
```

Policy 只认领自己的资源类型。没有选定 Principal，或成员关系为空时，返回拒绝结果。

```java
@Component
public class WorkspaceDocumentPermissionPolicy implements PermissionPolicy {

    private final WorkspaceMembershipService memberships;

    public WorkspaceDocumentPermissionPolicy(WorkspaceMembershipService memberships) {
        this.memberships = memberships;
    }

    @Override
    public boolean supports(Resource resource) {
        return WorkspaceDocumentScope.TYPE.equals(resource.type());
    }

    @Override
    public PermissionDecision decide(Call call, Permission permission) {
        Optional<Principal> principal = call.principal();
        if (principal.isEmpty()) {
            return PermissionDecision.deny("尚未选择 Principal");
        }
        Set<Long> workspaceIds = memberships.workspaceIds(principal.get().principalId(), permission.action());
        if (workspaceIds.isEmpty()) {
            return PermissionDecision.deny("当前 Principal 不属于任何可访问的工作区");
        }
        return PermissionDecision.allow(new WorkspaceDocumentScope(workspaceIds));
    }
}
```

Controller 声明动态资源。`resource` 表达式返回正整数或非空文本标识时，必须同时声明 `resourceType`。动态资源必须声明 `action`。

```java
@GetMapping("/documents/{documentId}")
@RequirePermission(resource = "#documentId", resourceType = "WORKSPACE:DOCUMENT", action = Action.READ_VALUE)
public DocumentView get(@PathVariable long documentId) {
    return documentService.view(documentId);
}
```

Repository 读取当前范围，并把条件放入 SQL。不先查询全部记录再在内存中过滤。

```java
@Repository
public class DocumentRepository {

    private final CurrentResourceScope currentScope;

    private final DocumentMapper mapper;

    public DocumentRepository(CurrentResourceScope currentScope, DocumentMapper mapper) {
        this.currentScope = currentScope;
        this.mapper = mapper;
    }

    public Optional<Document> findReadable(Permission permission, long documentId) {
        WorkspaceDocumentScope scope = currentScope.scope(permission, WorkspaceDocumentScope.class);
        return mapper.selectByIdInWorkspaces(documentId, scope.workspaceIds());
    }
}
```

`CurrentResourceScope.scope(...)` 找不到本次调用中的范围时，抛出 `BusinessException`，错误码为 `PERMISSION_DENIED`。

## 扩展方式

- 应用把 PermissionPolicy 实现注册为 Spring Bean。`CorePermissionConfiguration` 以 `List<PermissionPolicy>` 收集全部实现，构造 PermissionAssessment。
- 每个 Resource 必须恰好由一个 Policy 认领。没有 Policy 或多个 Policy 同时认领时，权限判断失败。
- 操作目录的扩展方式见 [Operation 与权限声明](/capabilities/identity-access/operation-declaration)。RBAC 领域和 ACL 的扩展方式见[权限模型](/capabilities/identity-access/permission-models)。
