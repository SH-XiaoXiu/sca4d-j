# Session、Principal 选择与请求身份

认证先确定 Subject 和 Account，再创建绑定该 Account 的 Session。业务请求从 Session 可达的 Principal 中选择当前执行主体，并在 `CurrentRequest` 中保存本次请求使用的身份、权限和资源访问条件。

Subject、Account、Credential、Membership 与 Principal 的关系见[IAM 身份与访问模型](./iam.md)，认证方式和第二因素见[认证过程与 MFA](./authentication-and-mfa.md)。

## Session

每次认证先确定一个 Account，再创建绑定该 Account 的 Subject Session。请求参数和 Header 不能在 Session 内切换 Account；切换账号必须重新认证。

认证成功后的工作空间选择只选择可达 Principal，不会创建另一个 Account Session：

| Session Account | 可以选择的 Principal |
| --- | --- |
| GLOBAL | DEFAULT、已有 PLATFORM Principal、已有 Membership 对应的 TENANT Principal |
| TENANT | DEFAULT、与 Account 同租户的 TENANT Principal |

Session 保存登录账号和可达 Principal 快照，不保存可变的“当前工作空间”。同一 Session 的两个并发请求可以分别选择不同 Principal，不会互相修改请求身份。

Subject 停用时结束其全部 Account Session。Account 的 Credential、第二因子要求或身份源关系发生安全变化时，只结束该 Account 的 Session。Principal、角色或权限状态变化后，服务端刷新相关 Session 权限快照，并提示客户端重新读取当前状态。

## Principal 选择

`Principal` 是一次业务操作代表的执行主体。DEFAULT、PLATFORM 和 TENANT Principal 分别用于个人、平台和租户工作空间；SYSTEM Principal 用于没有用户 Session 的后台任务。

`PrincipalSelection<P>` 根据请求输入和 Session 中的可达 Principal 校验选择结果，并把 Principal 写入当前 `CurrentRequest`。请求不能选择其他 Subject 的 Principal，也不能让 TENANT Account 进入其他租户。

角色、权限、资源访问、审计、通知和即时通信中的执行身份都以当前请求选中的 Principal 为准。Subject 表示登录用户，不能代替业务执行主体。

## 权限判断

`SessionPermission` 保存当前 Principal 的角色码和权限码。`StpUtil.hasRole(...)`、`StpUtil.hasPermission(...)` 与项目权限注解都读取同一份数据。

涉及具体资源时，`AccessAspect` 在 RBAC 通过后解析资源，调用 Cedar 判断 Owner、Membership、Grant、状态和时间规则，再把 `AccessCriteria` 写入 `CurrentRequest`。Repository 读取 Criteria 并拼接 SQL。Cedar 不管理 Session，也不替代 RBAC 或 Repository SQL。

权限和资源范围的完整说明见[权限与资源访问](./permission-and-access.md)。

## HTTP 请求过程

```text
token + Principal 选择输入
  -> 恢复 Account-bound Subject Session
  -> 校验 Account 可达的 Principal
  -> CurrentRequest 保存 Subject、Account、Principal
  -> SessionPermission 判断功能权限
  -> Cedar 判断具体资源
  -> Repository 按 AccessCriteria 查询或修改数据
```

认证请求在创建 Session 前执行另一段过程：

```text
scopeType + tenantCode + identifier + proof
  -> AuthMethod 解析当前方式所需的 Account 或上游身份
  -> CredentialScheme、IdentityProvider 或短期 Challenge 验证 Proof
  -> Authentication Process 保存认证方式和不透明 Proof 版本
  -> 创建 Account-bound Subject Session
```

完整认证过程见[认证过程与 MFA](./authentication-and-mfa.md)，`CurrentRequest` 的创建、异步限制和清理见[CurrentRequest](./current-request.md)。
