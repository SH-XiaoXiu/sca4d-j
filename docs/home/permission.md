# 权限与调用

权限检查用 Principal 确定谁在操作，用 Resource 确定操作目标，用 Action 确定要做什么。接入接口时，先选择当前 Principal，再用 `@RequirePermission` 声明要检查的权限；只有 PermissionPolicy 允许后，业务方法才执行。

Permission 将 Resource 与 Action 组成待检查的权限，PermissionPolicy 读取当前 Call 中的身份并返回 PermissionDecision。

```text
Permission = Resource + Action
授权判断 = 当前 Principal + Permission
```

平台角色读取接口检查 `operation + platform:roles:read + invoke`；订单读取接口检查 `order + orderId + read`。Operation 管固定入口，对象权限管具体数据，两者可以同时检查，接入步骤见[身份与权限教程](/tutorials/identity-and-permission)。

## 两类权限

| 对象 | 授权依据 | 典型结果 |
|---|---|---|
| 固定入口 | Principal 已获得的 Operation | 允许或拒绝进入接口 |
| 业务对象 | 对象状态、归属关系和当前 Principal | 允许、拒绝或返回数据范围 |

角色适合保存稳定职责。对象所属模块负责解释对象状态、组织关系和数据范围。两类权限可以同时作用于一次调用。

```mermaid
flowchart LR
    Principal[当前 Principal] --> Policy[PermissionPolicy]
    Resource[Resource] --> Permission[Permission]
    Action[Action] --> Permission
    Permission --> Policy
    Policy --> Decision[PermissionDecision]
    Decision --> Service[业务 Service]
```

## 固定入口

角色获得 `platform:roles:read` 后，Session 保存对应的 Operation。请求选择 Platform Principal 后，权限判断构造：

```java
new Permission(new Operation("platform:roles:read"), Action.INVOKE)
```

PermissionPolicy 在 Session 静态资源中找到目标 Operation 时返回允许；找不到时返回 `PERMISSION_DENIED`，业务方法不执行。角色关系变化通过 Session 失效或刷新生效。

## 业务对象

文件下载请求包含 `fileId` 时，文件模块建立 `file + fileId` Resource。策略读取 StoredFile 的状态、用途和关联资料，再判断当前 Principal 是否可以执行 `read`。

业务模块接入对象权限需要定义稳定的 Resource type 和 id，提供 ResourceResolver 和 PermissionPolicy，并让 Repository 消费允许结果中的 ResourceScope。ResourceScope 保存组织、区域或对象条件；Repository 将这些条件加入查询或写入条件。

```text
解析 Resource
→ PermissionPolicy 读取授权关系和对象状态
→ 返回 allow(ResourceScope) 或 deny
→ Repository 消费 ResourceScope
→ Service 返回业务结果
```

同一次调用中的同一项 Permission 只判断一次。目标 Resource 没有唯一策略、策略装配重复或策略返回非法范围时，调用在业务方法之前失败。

## 资源

资源（Resource）是权限判断和审计所指向的对象。一次调用选定当前 [Principal](/home/identity) 后，还要明确这个业务身份准备操作什么，以及要执行什么动作。

Sca4d-J 使用 [Resource](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/resource/Resource.html) 表示资源。每个 Resource 都由稳定的资源类型和该类型内唯一的资源标识共同确定：

```text
Resource = type + id
Permission = Resource + Action
```

例如，Principal #3001 读取文件 #9001 时，三个对象分别表达执行者、目标和动作：

| 对象 | 当前值 | 回答的问题 |
|---|---|---|
| Principal | #3001 | 谁在执行这次业务调用？ |
| Resource | `file + 9001` | 操作的目标是什么？ |
| Action | `read` | 准备对目标做什么？ |

[Permission](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/permission/Permission.html) 只把 Resource 和 Action 组成待判断权限。当前 Principal、Session 身份和已经装载的静态资源保存在 Call 中，PermissionPolicy 结合它们作出允许或拒绝结果。

### 资源由类型和标识共同确定

资源标识只需要在同一个资源类型内唯一。文件 #42、账号 #42 和角色 #42 可以同时存在，因为它们的资源类型不同。

| 业务对象 | Resource type | Resource id |
|---|---|---|
| Account #2001 | `account` | `2001` |
| StoredFile #9001 | `file` | `9001` |
| PlatformUserProfile #3001 | `platform:user:profile` | `3001` |
| 角色管理读取接口 | `operation` | `platform:roles:read` |

Resource type 是所属模块维护的稳定业务名称。类名、数据库表名和 HTTP 路由可以随实现调整，不能据此改变已经保存的权限和审计含义。例如，文件无论通过下载路由、头像路由还是内部任务读取，仍然是 `file + fileId`。

Resource id 必须在同一 type 内稳定且无歧义。数值主键通常使用十进制字符串；已有稳定业务编码时也可以直接使用。角色同时存在于多个 DomainScope 中，因此 Role 使用 `DomainScope:roleId` 作为资源标识，避免不同范围内的同号角色变成同一个 Resource。

Resource 的身份只取决于 `type()` 和 `id()`，不取决于具体 Java 类或对象实例。Session 和 Call 判断静态授权时也只比较这两个值，因此完整领域对象和只保存同一组 `type + id` 的引用可以表示同一个资源。

### 资源数据仍由业务模块管理

业务对象通过 Resource 向权限判断和审计暴露稳定身份，原有业务模型继续负责对象的数据和生命周期。文件、账号、角色、设备和订单可以使用不同的表、Repository 和领域类型；Core 只约定怎样通过 `type + id` 指向目标。

```mermaid
flowchart LR
    Module["所属业务模块"] --> Data["业务表、状态和生命周期"]
    Data --> Domain["领域对象"]
    Domain --> Identity["Resource type + id"]
    Identity --> Permission["Permission"]
    Identity --> Audit["审计目标"]
```

文件模块负责 StoredFile 的用途、大小、状态和存储位置；IAM 负责 Account 和 Role；具体应用负责 PlatformUserProfile。它们可以使用不同的表、Repository 和生命周期，只要在需要权限判断或审计时提供稳定的 Resource 身份。

动态判断业务资源时，所属模块根据 `type + id` 读取对象当前是否存在、状态是否允许目标 Action，以及当前 Principal 与对象之间的业务关系。进入 Session 的静态 Resource 是建立或刷新 Session 时的快照；对应授权发生变化时，负责修改授权的模块需要使相关 Session 失效或刷新。

业务数据是否实现 Resource，取决于它是否会成为一个需要明确命名的操作目标。一个订单可以同时关联顾客 Principal、门店和创建人，但只有权限或审计需要表达「正在读取订单 #8842」时，订单本身才需要作为 `order + 8842` Resource 参与调用。

权限声明和审计声明分别确定各自的目标 Resource。两者使用相同的 `type + id` 时，表示权限判断和审计记录指向同一个业务对象；一个业务过程涉及文件及其绑定的用户资料时，也可以分别指向两个 Resource。

### 领域对象与 ResourceReference

领域对象已经包含稳定标识，而且对象本身就是操作目标时，可以直接实现 Resource。当前 Account、StoredFile、Role 和 PlatformUserProfile 都采用这种方式。调用方取得完整对象后，可以直接把它交给权限判断或审计，不需要再创建一个只转发相同标识的包装类型。

调用方只有资源类型和标识，而且没有必要为了构造权限目标查询完整对象时，使用 [ResourceReference](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/resource/ResourceReference.html)：

```java
Resource file = new ResourceReference("file", "9001");
Permission read = new Permission(file, Action.READ);
```

ResourceReference 不保存领域字段，也不确认目标当前是否存在。负责该资源的 PermissionPolicy 或业务 Service 仍要从所属模块读取当前状态，再决定允许、拒绝或返回不存在结果。

同一个业务流程中还可能同时出现多个 Resource。StoredFile 本身使用 `file + fileId` 表示正在读写的文件；FileUse 另外保存文件场景 `profile-avatar` 和目标 Resource `platform:user:profile + 3001`。文件、文件用途和用户资料因此保持各自含义，权限与审计可以准确说明本次处理的是哪个对象。

### 接口也是资源

固定业务入口使用 [Operation](#操作) 参与权限判断和审计。Operation 实现 Resource，使用 `operation` type，并把稳定的 Operation code 作为 id。

```java
Resource operation = new Operation("platform:roles:read");
Permission invoke = new Permission(operation, Action.INVOKE);
```

接口和具体业务对象使用同一种 Permission 结构。接口通常判断 `operation + code + invoke`，具体业务对象可以判断 `file + fileId + read`、`account + accountId + update`，或者业务模块定义的其他 Action。这样，权限判断、Call 记录和审计都可以直接使用 Resource 和 Action。

Operation 数量有限，而且角色变化后可以按 Principal 预先计算。ResourceLoader 在 Session 建立或刷新时把当前 Principal 获得的 Operation 装入静态资源快照；调用选择 Principal 后，SessionOperationPermissionPolicy 直接判断快照中是否存在目标 Operation。

文件、设备、订单等业务资源数量可能很大，状态也会持续变化，通常不应全部放入 Session。调用根据当前参数确定具体 Resource，再由所属模块的 PermissionPolicy 读取最新授权数据并作出判断。

### 集合资源与 ResourceScope

有些调用保护的不是一个固定实例，而是一组业务对象，例如「读取当前 Principal 可以查看的设备集合」。模块可以为这组对象定义一个集合 Resource，再让权限策略返回 [ResourceScope](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/permission/ResourceScope.html)。

ResourceScope 保存 PermissionPolicy 在这次权限判断中批准的数据访问条件，例如当前 Principal 可以访问的组织、区域或设备集合。Repository 消费这些条件并转换成查询限制，使查询只返回本次调用允许访问的数据。

```mermaid
flowchart LR
    Principal["当前 Principal"] --> Policy["PermissionPolicy"]
    Permission["集合 Resource + READ"] --> Policy
    Policy --> Decision["PermissionDecision"]
    Decision --> Scope["ResourceScope"]
    Scope --> Repository["Repository 查询条件"]
```

ResourceScope 的 `resourceType()` 与 Permission 中 Resource 的 type 保持一致。Repository 在当前调用中取得对应范围，再把组织、区域或设备等业务条件加入查询。

### 两个完整场景

#### 读取角色管理接口

PlatformRoleController 的角色读取接口声明 Operation `platform:roles:read` 和 Action `invoke`。Platform 角色把这个 Operation 授予 Principal，PlatformRbacResourceLoader 在 Session 建立或刷新时把它装入该 Principal 的静态 Resource 快照。

请求进入后先选择当前 Platform Principal，Call 同时取得这个 Principal 的静态 Resource。PermissionCallAssessor 为接口建立 `operation + platform:roles:read + invoke` Permission，SessionOperationPermissionPolicy 检查 Call 是否持有目标 Operation。存在时执行接口；不存在时返回权限拒绝，Controller 不会开始读取角色。

#### 完成头像文件上传

文件上传完成接口收到文件 ID 后，FileResourceResolver 建立 `file + fileId` Resource。PermissionCallAssessor 再建立目标 Resource 与 Action `write` 组成的 Permission，并交给 PlatformFilePermissionPolicy。

PlatformFilePermissionPolicy 读取 StoredFile，确认它属于头像场景，并取得 FileUse 中的目标 PlatformUserProfile Resource。创建文件的 Principal 必须是当前 Principal，同时当前 Principal 必须是资料本人，或者拥有账号管理接口 Operation。两个条件都满足后，业务方法才确认文件已经上传完成。

这个接口同时使用 `@AuditTarget` 把方法参数中的文件 ID 解析为同一个 `file + fileId` Resource。AuditCallRecorder 在方法得到成功、拒绝或失败结果后，以当前 Subject、Account 和 Principal 作为执行者，以文件 Resource 作为目标写入审计记录。

Operation 与 Action 的区别见[操作](#操作)，Resource 的解析、判断和消费顺序见[调用生命周期](#调用生命周期)。具体接入步骤见[身份与权限教程](/tutorials/identity-and-permission)。

## 操作

操作（Operation）是可授予角色的固定业务入口。`platform:roles:read` 表示平台角色读取入口，编码同时作为资源类型为 `operation` 的 Resource 标识。

```text
Resource = operation + platform:roles:read
Action   = invoke
```

角色获得这个 Operation 后，关联的 Principal 才能通过该入口的权限检查。接口内部仍然可以检查文件、订单或其他业务 Resource。

### 操作目录

操作目录由功能模块声明，系统用它检查编码冲突，并向管理端提供可授予的动作节点。

| 层级 | 示例 | 含义 |
|---|---|---|
| 模块 | `platform` | 声明功能边界 |
| 资源 | `roles` | 声明入口处理的对象类别 |
| 动作 | `read`、`manage` | 声明可授予的操作 |
| 完整编码 | `platform:roles:read` | Operation 的稳定 id |

只有动作节点可以授予角色。模块和资源节点用于组织目录，不单独形成权限。

操作编码写入角色关系、权限数据和审计记录后，继续表示同一个入口。类名、数据库表名、页面路径和 HTTP 路由不参与编码。

### Session 和请求

角色关系保存 Operation。Session 建立或刷新时，IAM 将 Principal 获得的 Operation 写入静态资源快照。请求选择当前 Principal 后，调用生命周期检查对应的 `operation + code + invoke` Permission。

角色关系变化后，IAM 使受影响的 Session 失效或刷新。新的请求重新装载操作快照。

```mermaid
flowchart LR
    Catalog[Operation 目录] --> Role[角色关系]
    Role --> IAM[IAM]
    IAM --> Session[Session 静态资源]
    Session --> Check[Permission 检查]
    Check --> Endpoint[固定业务入口]
```

### Operation 和业务 Resource

Operation 表示固定入口，业务 Resource 表示入口内部处理的具体对象。例如角色列表接口检查 `platform:roles:read + invoke`，读取某个文件时再检查 `file + fileId + read`。Operation 通常随 Session 装载，业务 Resource 按请求中的标识读取当前状态。

需要独立授予、审计或撤销的入口应声明独立 Operation。页面菜单、按钮和 URL 可以引用 Operation 目录，但后端仍以 Permission 检查为准。

Resource 的身份设计见[资源](#资源)。

## 调用生命周期

`Call` 保存一次调用使用的 Session、当前 Principal 和已经完成的权限判断。`CallExecution` 保存一次方法执行的声明、参数、成功要求和结果。

一个 HTTP 请求只有一个 Call。Controller、Service 等被声明式规则拦截的方法分别建立 CallExecution，并共享该 Call 中的 Session、Principal 和权限判定。

```mermaid
flowchart TD
    Request[HTTP 请求] --> Call[Call]
    Call --> Controller[Controller]
    Call --> Service[Service]
    Controller --> ExecutionA[CallExecution]
    Service --> ExecutionB[CallExecution]
    ExecutionA --> Call
    ExecutionB --> Call
```

Session 保存跨请求的登录状态；Call 只在当前请求期间存在。

### HTTP 执行顺序

```text
CallFilter 创建 Call
→ 认证拦截器恢复 Session
→ Principal 选择器写入当前 Principal
→ CallLifecycle 创建 CallExecution
→ 检查认证、权限、限流、幂等和锁
→ 执行业务方法
→ 检查成功要求
→ 保存幂等结果并记录审计
→ 返回响应并清理 Call
```

匿名请求同样建立 Call，因此仍然具有调用标识和开始时间。需要 Session 的方法在认证未完成时返回 `AUTHENTICATION_REQUIRED`。需要权限或审计的接口必须先选择当前 Principal。

```mermaid
sequenceDiagram
    participant Request as HTTP 请求
    participant Call as CallFilter
    participant Auth as 认证
    participant Select as Principal 选择
    participant Life as CallLifecycle
    participant Method as 业务方法
    participant Audit as 审计
    Request->>Call: 创建 Call
    Call->>Auth: 恢复 Session
    Auth->>Call: 写入 Subject、Account
    Select->>Call: 写入 Principal
    Life->>Life: 评估声明和执行条件
    Life->>Method: 执行
    Method-->>Life: 返回或抛出异常
    Life->>Life: 检查成功要求
    Life->>Audit: 记录结果
```

### 方法声明

| 声明 | 作用 |
|---|---|
| `@RequirePermission` | 在方法执行前判断 Operation 或业务 Resource |
| `@RateLimited` | 在方法执行前取得共享限流额度 |
| `@Idempotent` | 保存首次成功结果，并重放相同请求 |
| `@Locked` | 在业务方法前取得分布式锁 |
| `@IfMatch` | 要求 Repository 消费请求版本条件 |
| `@AuditTarget` | 指定审计记录中的目标 Resource |

ResourceScope、版本条件和幂等结果绑定到声明它们的方法。嵌套方法不能代替外层方法消费这些条件。

### 结果

| 条件 | CallExecution 结果 | 后续动作 |
|---|---|---|
| 方法和成功要求全部通过 | `SUCCESS` | 提交事务、保存幂等结果、记录审计 |
| 命中已保存的幂等结果 | `REPLAYED` | 返回原结果，不执行方法 |
| 业务规则拒绝 | `DENIED` | 不保存成功结果，记录拒绝 |
| 异常或成功要求未满足 | `FAILED` | 调用失败，按事务规则回滚或恢复 |

审计写入属于调用成功条件。审计失败时，调用不能报告为成功。

排查一次失败时，按 Call 标识检查 Session、Principal、Permission、幂等与锁、Repository 对 ResourceScope 和版本条件的消费，以及事务、幂等结果和审计写入。

调用接口见 [CallLifecycle](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/call/CallLifecycle.html)，代码接入见[身份与权限教程](/tutorials/identity-and-permission)。
