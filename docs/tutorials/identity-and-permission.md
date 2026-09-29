# 接入身份与权限

给接口接入权限，先选择当前 Principal，再声明固定入口 Operation；需要限制具体数据时，再定义 Resource 和 PermissionPolicy，并让 Repository 使用批准的数据范围。下面先列接入顺序，再说明各层何时使用 Subject、Account 或 Principal。

## 接入一个业务入口

1. 在应用的操作目录中声明稳定编码，参考 `SystemOperations` 或 `IotOperations`。角色只能授予目录中的动作节点。
2. Controller 使用 `@CurrentPrincipal` 和对应 `PrincipalSelection` 选择本次业务身份；声明 `@RequirePermission` 检查入口或目标 Resource。
3. 需要对象授权时，为目标提供稳定的 Resource type 和 id，注册对应 `ResourceResolver` 与 `PermissionPolicy`。Policy 根据当前 Principal、对象状态和业务关系判断。
4. 返回 ResourceScope 时，由 Repository 把批准的范围加入 SQL。写操作同样包含范围条件。
5. 用有权、无权和不属于当前 Session 的 Principal 验证接口。角色或身份关系变更后，检查受影响 Session 已刷新或失效。

对象关系见[身份与账号](/home/identity)，Resource、Operation 与调用顺序见[资源与权限](/home/permission)。下面说明不同层应该使用哪些身份参数。

## 适用范围

本规则用于决定 Controller、消息接入、Service、Policy、Repository、Mapper、任务和审计代码应该使用 Subject、Account 还是 Principal。

任何代码在使用这三个对象之前，都必须先明确当前参数表示接入者、认证账号、业务执行者，还是被管理的目标记录。

## 三个对象

```mermaid
flowchart LR
  Subject["Subject<br/>谁通过认证"] --> Account["Account<br/>使用哪个账号认证"]
  Account --> AccountPrincipal["多条 AccountPrincipal"]
  AccountPrincipal --> PrincipalSet["Principal 集合<br/>本次 Account 可以取得"]
  PrincipalSet --> Selected["当前 Principal<br/>本次业务以谁执行"]
```

### Subject

Subject 表示实际接入者，回答「谁完成了认证或建立了系统调用」。

```text
HUMAN       -> 自然人
APPLICATION -> 使用程序凭据接入的应用
DEVICE  -> 通过设备协议接入的设备
SYSTEM  -> 调度、Outbox 或维护任务
```

Subject 主要用于接入限制、认证安全、已验证 Contact、Session 失效和审计。普通业务代码不能根据 SubjectType 区分自然人、应用或设备。

### Account

Account 表示一组可以完成认证的账号数据，回答「当前 Subject 使用哪个账号和认证材料建立 Session」。

Account 关联 RealmKey、AccountIdentifier、Credential、MFA 策略和 Session。密码、Passkey、TOTP、外部身份和 Account 自身状态都按 Account 处理。

Account 不表示管理员、客户、骑手或租户成员，也不能作为业务资源的 owner、member 或 creator。

### Principal

Principal 表示业务执行身份，回答「当前业务操作以谁的身份执行」。

业务资料、Role、Operation、Owner、Member、Grant、消息发送者、文件使用者和数据创建者都使用 Principal。Principal 独立存在，不属于 Subject 或 Account；Account 通过多条 AccountPrincipal 记录取得 Principal 集合。

一个 Session 可以包含多个 Principal，但一次调用只能选择一个当前 Principal。

## 为什么普通业务只使用 Principal

脚手架在业务 Service 执行前完成以下处理：

```mermaid
sequenceDiagram
  participant Entry as HTTP / STOMP / MQTT / Task
  participant Auth as 认证与 Session
  participant Select as Principal 选择与 Operation 检查
  participant Business as 业务 Service
  participant Data as Repository / SQL

  Entry->>Auth: 认证输入或 Session
  Auth-->>Entry: Subject + Account + Principal 集合
  Entry->>Select: 当前路由、消息目标或设备主题
  Select-->>Entry: 已检查的当前 Principal
  Entry->>Business: Principal + 业务参数
  Business->>Data: principalId + 资源条件
```

HTTP、STOMP、MQTT 和任务接入负责恢复 Subject 与 Account、选择 Account 已获准的 Principal、检查接入方式的操作限制并建立 Call。

这些检查完成后，业务 Service 只需要知道当前 Principal。业务代码不重复判断调用者是 HUMAN、APPLICATION 还是 DEVICE，也不重新验证 Account 是否包含该 Principal。

如果同一个 Principal 被 HUMAN Subject 和 APPLICATION Subject 使用，两种入口可以使用不同的认证方式和操作白名单。进入业务 Service 前，最终 Operation 已经取 Principal RBAC 与入口白名单的交集。业务 Service 因此可以复用同一实现。

## 必须使用完整名称

文档正文、类型名、方法名和参数名必须使用 `Subject`、`Account`、`Principal` 完整名称，不能用单字母代替。

## 使用组合

| 组合 | 使用位置 | 示例 | 普通业务是否使用 |
| --- | --- | --- | --- |
| Subject | Subject 状态、已验证 Contact、Subject 下全部 Session | 停用 Subject、替换认证手机号 | 否 |
| Account | Credential、MFA、IdentityProvider 关联、Account Session | 修改密码、登记 Passkey、结束当前账号 Session | 否 |
| Principal | 业务资料、RBAC、资源访问、Owner、Member、Grant | 修改管理员资料、创建订单、发送消息、控制设备 | 是 |
| Subject + Account | 确认 Account 属于哪个 Subject，以及 Subject 管理自己的 Account | 查询本人账号安全信息、停用 Subject 下某个 Account | 否 |
| Account + Principal | AccountPrincipal 读写、Session 创建和 Principal 集合装载 | 给 Account 增加 Principal、检查关联、删除关联 | 否 |
| Subject + Principal | 接入来源检查和审计；不能作为普通业务 Service 的身份参数 | APPLICATION 使用某个人的 Principal、设备通过 MQTT 使用获准 Principal | 否 |
| Subject + Account + Principal | Call、Session 创建、接入日志和审计 | 记录谁通过哪个 Account、以哪个 Principal 执行 | 否 |

组合表不表示所有方法都要接收多个 ID。只有方法的实际判断需要某个对象时才使用它；审计需要三者不等于业务方法需要三个参数。

## IAM 与认证代码

IAM、Authentication、MFA 和 Session 管理根据实际操作使用完整名称：

```java
disableSubject(Long subjectId, long version)
changePassword(Long accountId, PasswordChange command)
grantPrincipal(Long accountId, Long targetPrincipalId)
createSession(Long accountId)
```

`createSession` 可以从 Account 读取 Subject 和 Principal 集合，不要求调用方提交三个互相信任的 ID。能够从已验证对象读取的数据，不作为独立客户端参数接收。

AccountPrincipal 管理中的 `targetPrincipalId` 是被授予 Account 的 Principal，不一定是执行该管理操作的当前 Principal。方法和 SQL 必须使用 `actorPrincipalId`、`targetPrincipalId` 等名称区分操作者与目标。

## 业务 Service 与 Policy

普通业务 Service 接收 Principal 或由 Principal 解析出的业务身份：

```java
PlatformOperatorProfile updateProfile(
        Principal principal,
        PlatformOperatorProfileUpdate command);

Order createOrder(
        Principal customer,
        OrderCreate command);
```

需要限制 PrincipalType 时，接入检查或业务入口 Policy 先把公共 Principal 转换成明确业务身份，例如 `PlatformOperatorActor`、`CustomerActor` 或 `TenantMemberActor`。后续业务方法使用该类型，不再接收 Subject 或 Account。

业务 Policy 只根据 Principal、目标 Resource、Action 和业务表作出判断。以下写法不符合本规则：

```java
updateProfile(principalId, accountId, principalId, command);
createOrder(principalId, accountId, customerPrincipalId, command);
```

如果业务方法确实需要修改 Account、Credential 或 Subject Contact，它已经在处理 IAM 或认证资源，不属于「普通业务只使用 Principal」的情况。

## Repository 与 SQL

IAM 与认证 SQL 使用表本身的关联字段：

```sql
SELECT * FROM iam_account
WHERE id = :accountId
  AND subject_id = :subjectId;

SELECT * FROM iam_account_principal
WHERE account_id = :accountId
  AND principal_id = :targetPrincipalId;
```

普通业务 SQL 使用 `principal_id` 识别执行者、Owner、Member、Creator 或 Grantee：

```sql
SELECT * FROM platform_operator
WHERE principal_id = :principalId;

SELECT device.*
FROM device_member
JOIN device ON device.id = device_member.device_id
WHERE device_member.principal_id = :principalId;
```

Tenant、Workspace、Device 等资源表仍然记录自己的 `tenant_id`、`workspace_id` 或 `device_id`。Principal 识别执行者，不代替资源 ID。

普通业务 SQL 禁止为了确认调用来源而关联 `iam_subject` 或 `iam_account`。HUMAN、APPLICATION、DEVICE 的入口限制必须在调用业务 Service 前完成。

## Call 与审计

Call 记录本次调用的 Subject、Account、当前 Principal、该 Principal 在 Session 中的 Resource 和权限判定。业务 Service 主要读取当前 Principal；认证安全和审计组件可以读取其他身份信息。

审计必须记录实际 Subject、认证 Account 和执行 Principal。审计组件直接从 Call 取得这些数据，不能要求每个业务方法为了审计增加 Subject 和 Account 参数。

SYSTEM 请求使用唯一 `Subject(id=1)`，Account 为空；Principal 使用启动时经数据库确认的领域身份，例如 MAIN 或 IOT。各实例共享同一身份的 Principal ID，实例 ID 单独记录执行位置。Spring 自主入口使用 `@SystemExecution("IOT")`，已有 Call 保持原身份。SYSTEM 仍然需要明确的 Operation 和 Resource Policy，见[系统、领域身份和实例](/home/identity#系统、领域身份和实例)。

## 注册应用调用注解

应用的额外调用规则通过 `CallLifecycle.Participant` 挂载；注解匹配使用 `CallLifecycleAdvisor.AnnotationBinding<A>`，非注解规则使用 `CallLifecycleAdvisor.MethodBinding`。下面的 `ProjectRisk` 注解和 `ProjectRiskVerifier` 由应用定义，Verifier 根据已经选定的 Principal 与方法实参判断，不创建独立 Aspect。

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@CallLifecycle.Intercepted
public @interface ProjectRisk {
}

@Component
public class ProjectRiskCallInterceptor implements CallLifecycle.Before,
        CallLifecycleAdvisor.AnnotationBinding<ProjectRisk> {
    public static final TreeCode CODE = TreeCode.of("PROJECT/RISK");

    private final ProjectRiskVerifier verifier;

    public ProjectRiskCallInterceptor(ProjectRiskVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Class<ProjectRisk> annotationType() { return ProjectRisk.class; }

    @Override
    public TreeCode code() { return CODE; }

    @Override
    public boolean supports(CallExecution execution) {
        return execution.declaration(ProjectRisk.class).isPresent();
    }

    @Override
    public void before(CallExecution execution) {
        verifier.verify(execution.call().principal().orElseThrow(), execution.arguments());
    }
}
```

应用在配置类中声明相对内置节点的位置，内置节点之间的顺序保持不变：

```java
@Bean
CallLifecycleConfiguration.Customizer projectRiskOrder() {
    return configuration -> {
        configuration.after(ProjectRiskCallInterceptor.CODE, PermissionCallInterceptor.CODE);
        configuration.before(ProjectRiskCallInterceptor.CODE, IdempotencyCallInterceptor.CODE);
    };
}
```

目标方法声明 `@ProjectRisk` 后由统一 Advisor 拦截。功能注解标记了 `@CallLifecycle.Intercepted` 却没有注册对应 `AnnotationBinding` 时，方法执行前明确失败；功能行为仍需由 `Participant` Bean 实现。业务模块不修改 Advisor 注解清单。接口方法、类型和参数声明都能通过注册进入执行范围；需要为某注解附着预提交条件时，绑定对象实现 `attach(execution, annotation)`。依赖排序、失败与重放规则见[注解与环绕处理](/home/permission#注解与环绕处理)。

## 验收要求

- Core Principal 通过 `principalId` 标识业务身份；不把 Subject、Account 或业务 Profile 数据复制到公共身份契约。
- 普通业务 Service 的身份参数只有 Principal 或明确的业务 Actor。
- 普通业务 Repository 不使用 Subject 或 Account 判断 Owner、Member、Role 或数据范围。
- AccountPrincipal 只在 IAM、Authentication、Session 和账号管理代码中读写。
- HTTP、STOMP、MQTT 和任务入口在调用业务 Service 前完成 Principal 选择与操作限制。
- 审计从 Call 记录 Subject、Account 和 Principal，不改变业务方法签名。
- 同一个业务 Service 可以被 HUMAN、APPLICATION、DEVICE 或 SYSTEM 入口复用，前提是入口已经完成对应检查。
