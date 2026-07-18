# 请求与认证

## CurrentRequest

| 包 | 类型 | 主要成员 | 说明 |
| --- | --- | --- | --- |
| `com.sca4d.common.request` | `CurrentRequest` | `requireSession()`、`requireSubject()`、`requireAccount()`、`requirePrincipal()`、`requireSessionPermission()`、`putAccess(...)`、`requireAccess(...)` | 保存一次 HTTP、STOMP 或 SYSTEM 调用的信息 |
| `com.sca4d.common.request` | `CurrentRequestHolder` | `currentOrNull()`、`current()`、`set(...)`、`clear()` | ThreadLocal 读写入口 |
| `com.sca4d.common.request` | `TraceIds` | `resolve(candidate)` | 接受合法上游 traceId，否则生成新值 |
| `com.sca4d.common.request.web` | `HttpRequestFacts` | `pathVariables(...)`、`headers(...)`、`queryParameters(...)` | 从 Servlet 请求提取允许进入 CurrentRequest 的字段 |
| `com.sca4d.common.request.web` | `CurrentRequestFilter` | Servlet Filter | 创建、记录并清理 HTTP CurrentRequest |

`CurrentRequestHolder.currentOrNull()` 在没有请求时返回 `null`；`current()` 在当前线程没有请求时抛出 `IllegalStateException`。认证信息使用 `CurrentRequest.requireXxx()` 读取。

## 认证模型

包：`com.sca4d.common.auth`

| 类型 | 内容 |
| --- | --- |
| `Subject` | subjectId、displayName |
| `Account` | accountId、scopeType、scopeId、status、secondFactorRequired |
| `Principal` | principalId、subjectId、principalType、tenantId、displayName |
| `PrincipalType` | `SYSTEM`、`PLATFORM`、`TENANT`、`DEFAULT` |
| `CredentialType` | 当前支持的凭据类型 |
| `PasswordCredential` | 密码凭据输入，不进入 Session 和响应 |
| `TotpCredential` | enrollment 激活调用栈中的 Base32 密钥和首次已验证时间步，写入数据库前加密 |
| `TotpProof` | 当前请求的六位验证码，不进入 Session、日志、审计或响应 |
| `SessionSnapshot` | sessionId、deviceId、terminalName、loginTime；不包含 token、权限和凭据 |
| `Session` | 当前登录会话接口 |
| `Authentication` | token 读取、登录、注销、踢出和 Session 恢复接口 |

认证接口的详细方法见[接口与注解](/reference/interfaces-annotations.md#认证与会话接口)。默认实现为 `SaTokenAuthentication` 和 `SaTokenSession`；认证不可用时使用 `AuthenticationUnavailable`。

## Principal 选择

| 包 | 类型 | 说明 |
| --- | --- | --- |
| `com.sca4d.common.auth` | `PrincipalSelection<P>` | 根据 CurrentRequest、允许类型和选择参数返回 Principal |
| `com.sca4d.common.auth` | `DefaultPrincipalSelection` | 默认请求内选择器 |
| `com.sca4d.common.auth.web` | `PrincipalSelectionInput` | principalType、principalId、tenantId、selectionKey |
| `com.sca4d.common.auth.web` | `PrincipalSelectionInputResolver` | 从路径、查询和注解参数构建选择输入 |
| `com.sca4d.common.auth.web` | `PrincipalSelectionInvoker` | 调用选择器并校验返回值 |
| `com.sca4d.common.auth` | `PrincipalInjection` | Controller 参数注入所需元数据 |
| `com.sca4d.common.auth.web` | `SubjectArgumentResolver` | 注入 `Subject` |
| `com.sca4d.common.auth.web` | `AccountArgumentResolver` | 注入 `Account` |
| `com.sca4d.common.auth.web` | `PrincipalArgumentResolver` | 注入 `Principal` |
| `com.sca4d.common.auth.messaging` | `MessagingSubjectArgumentResolver` | STOMP 方法注入 Subject |
| `com.sca4d.common.auth.messaging` | `MessagingAccountArgumentResolver` | STOMP 方法注入 Account |
| `com.sca4d.common.auth.messaging` | `MessagingPrincipalArgumentResolver` | STOMP 方法注入 Principal |

## HTTP 接入

| 类型 | 执行内容 |
| --- | --- |
| `AuthInterceptor` | 恢复登录状态并写入 Session、Subject、Account |
| `PrincipalInterceptor` | 执行 PrincipalSelection，检查权限并写入 CurrentRequest |
| `@AnonymousAccess` | 允许类或方法匿名访问 |
| `@AuthSubject` | 注入当前 Subject 或 subjectId |
| `@AuthAccount` | 注入当前 Session 固定的 Account 或 accountId |
| `@AuthPrincipal` | 声明允许的 Principal 类型、选择器并注入 Principal |
| `RequestFactMapAccessor` | 允许 SpEL 读取已建立的请求字段 |

注解参数见[接口与注解](/reference/interfaces-annotations.md#身份与授权注解)。

## 登录与当前用户

| 接口 | 默认实现 | 主要方法或结果 |
| --- | --- | --- |
| `AuthenticationProcessService` | `FlowableAuthenticationProcessService` | 创建 Flowable 认证实例、提交具体认证方式并单次创建 Session |
| `SessionService` | `DefaultSessionService` | 查询和撤销 Session、刷新会话权限 |
| `MeService` | `DefaultMeService` | 根据 Principal 选择返回 `MeSnapshot` |
| `MePrincipalSelection` | 同名类 | 处理 `GET /me` 的 Principal 选择 |

HTTP 输出类型：`LoginSessionView`、`MeView`、`MeSessionView`。内部读取模型：`MeSnapshot`、`AvailablePrincipal`。

## 凭据与生命周期

| 包 | 接口或类型 | 默认实现 |
| --- | --- | --- |
| `com.sca4d.iam.credential` | `CredentialScheme<C, P>` | `PasswordCredentialScheme` |
| `com.sca4d.iam.credential` | `CredentialService` | `DefaultCredentialService` |
| `com.sca4d.iam.subject` | `SubjectService` | `DefaultSubjectService` |
| `com.sca4d.iam.account` | `AccountService` | `DefaultAccountService` |
| `com.sca4d.iam.identifier` | `AccountIdentifierService` | `DefaultAccountIdentifierService` |
| `com.sca4d.iam.contact` | `ContactService` | `DefaultContactService` |
| `com.sca4d.iam.principal` | `PrincipalService` | `DefaultPrincipalService` |
| `com.sca4d.iam.subject` | `SubjectLock` | 使用 Subject ID 串行化凭据修改、认证完成前复核和 Session 创建 |
| `com.sca4d.iam.cleanup` | `SubjectCleanupTaskService` | 分批删除已进入删除状态的关系 |
| `com.sca4d.iam.cleanup` | `SubjectCleanupBatchService` | 单批清理接口 |
| `com.sca4d.iam.cleanup` | `SubjectCleanupJob` | Quartz 系统 Job |

密码验证失败使用 `KnownCredentialRejectedException`；未知 Subject、Account、Principal 或凭据状态使用对应 IAM 业务错误码。

## 认证失败类型

| 包 | 类型 | 结果 |
| --- | --- | --- |
| `com.sca4d.common.auth` | `UnauthenticatedException` | 未登录或凭据无效；默认映射为 401 |
| `com.sca4d.common.auth` | `MissingAuthException` | 请求缺少已经声明为必需的认证事实；映射为 403 |
| `com.sca4d.iam.credential` | `CredentialVerification` | 认证成功后的 Subject、Account、credentialId 和 credentialVersion |
| `com.sca4d.iam.system` | `SystemAccount` | SYSTEM Subject 与 Principal 的固定 ID 为 `1` |
| `com.sca4d.permission.satoken` | `PrincipalStpInterface` | 向 Sa-Token 提供当前 `SessionPermission` 中的权限码和角色码 |
