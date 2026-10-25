# 认证规则

本页的 HTTP 资源、Flowable 流程和 `AuthenticationProcessService`、`CredentialScheme<C, P>`、`AuthMethod<P>` 是认证流程的规则描述。
在 `core/`、`modules/`、`gateway/` 和 `apps/` 源码中没有这三个类型、这些 HTTP 路由或 Flowable 流程，它们不是当前已实现的行为。对应的已核对类型是 `AuthenticationPolicy`、`AuthenticationAttempts`、`AuthenticationMethod` 和 `SessionLifecycle`，见[认证尝试与 Session 模块](/capabilities/authentication-session/auth)。

## 单一认证入口

HTTP 和 STOMP 必须通过统一认证入口建立 Subject、Account、Session 和 Call。业务 Controller 禁止自行解析 Token，也禁止直接读取 Sa-Token Session 判断当前主体。

## 身份对象

认证代码必须遵守 [Subject、Account 与 Principal 使用规则](/capabilities/identity-access/development)。

认证确认 Account。由 Account 得到 Subject 和允许使用的 Principal 集合。业务身份不能从 SubjectType、RealmKey 或 Account 类型推导。

## 请求局部选择

当前 Principal 只存在于本次 Call。禁止调用 `tokenSession().set(...)` 保存当前 Principal。禁止让一个请求改变另一个并发请求选择的 Principal。Session 只保存认证结果。当前 Principal 只属于本次 Call，不写入 Session，因此并发请求可以选择不同的 Principal，互不影响。

## 匿名入口

接口默认需要认证。只有明确允许匿名的入口，才使用 `@AnonymousAccess`。类级与方法级覆盖规则必须可预测。禁止以路径白名单建立第二套匿名体系。

## 凭据

凭据的绑定、验证和变更由各认证方法模块实现，每个认证方法通过 `AuthenticationMethod` 接口接入引擎。`CredentialScheme<C, P>` 与 CredentialService 在源码中未定义。

以下内容不能写入日志、Session、审计 Payload、事件或响应：密码、邮箱验证码、TOTP 验证码、恢复码、OAuth/OIDC Token、LDAP 密码，以及 WebAuthn 请求或响应。

认证方式实现只验证自身输入。`AuthenticationMethod.authenticate` 返回 `VerifiedAuthentication`，其中包含 Account、Authentication、非秘密引用、版本和可选的最晚有效时间。

## 认证资源与流程管理

以下内容在 `core/`、`modules/`、`gateway/` 和 `apps/` 源码中未定义，本页不记录其行为：

- 公共认证 HTTP 资源（认证方式目录、认证创建与提交、Session 创建与注销等路由）；
- Flowable 认证流程、任务编码和 `AuthenticationProcessService`；
- 恢复码、Passkey 与 TOTP 的设置接口，以及 IdentityProvider 管理接口；
- 账号安全接口和第二因子要求的启用规则；
- 按账号作用域（GLOBAL、TENANT）解析认证目的的规则。

## 前端术语与确认样式

登录过程的第二因子和 `STEP_UP` 认证，在用户界面中统一称为「二步验证」。

`STEP_UP`、`primary-proof`、`second-factor-proof` 等只属于接口、流程和源码术语，不得展示为「STEP_UP」「再次验证」「再次认证」或「二次验证」。按钮、标题、说明、成功提示和错误提示使用同一术语。

「敏感操作确认」只表示当前操作的风险上下文，不是另一种认证方式。

删除 Passkey、删除 TOTP、解除外部身份关联、结束登录会话和关闭二步验证等破坏性操作，在二步验证对话框中使用独立的警告说明区。警告说明区需要明确对象、不可逆影响和会话失效范围，并使用危险操作按钮。

添加或更换认证方式、启用二步验证等非破坏性操作仍执行二步验证。这些操作使用中性说明和普通主按钮。

认证方法的 code 与源码常量一致，例如 `AUTHENTICATION/EMAIL_CODE`（`EmailCodeAuthenticationMethod`）和 `AUTHENTICATION/TOTP`（`TotpAuthenticationMethod`）。

## 会话变化

Subject、Account、Principal 或 Operation 变化后，必须结束或刷新受影响的 Session，并通过 ClientState 提示前端刷新。Account 安全材料变化时，只结束对应 Account 的 Session。

身份对象关系见 [IAM 身份关系](/capabilities/identity-access/iam)，认证方法与多因素判断见[认证方法、凭据与认证因素](/capabilities/authentication-session/authentication-methods)，具体 Java 接口见 [Java API 参考](/reference/#java-api-参考)。

## 示例

下面的 Controller 属于「工作区」应用。
邀请预览允许匿名访问，成员列表按默认规则要求认证，账号安全页要求在线 Session Token。

```java
@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    @AnonymousAccess
    @GetMapping("/invitations/{code}")
    public InvitationPreview previewInvitation(@PathVariable String code) {
        // 匿名入口，只返回邀请的公开信息。
        return invitations.preview(code);
    }

    @GetMapping("/{workspaceId}/members")
    public List<MemberView> members(@PathVariable long workspaceId) {
        // 默认需要认证。Controller 不解析 Token，当前主体由统一认证入口建立。
        return members.list(workspaceId);
    }

    @RequireSessionToken
    @DeleteMapping("/{workspaceId}/members/me")
    public void leave(@PathVariable long workspaceId) {
        // 要求在线 Session，注销或账号停用后的旧 JWT 不能调用。
        members.leave(workspaceId);
    }
}
```

`InvitationPreview`、`MemberView`、`invitations` 和 `members` 是应用自己的类型与服务。
`@AnonymousAccess` 位于 `com.sca4d.core.api.annotation`，`@RequireSessionToken` 位于 `com.sca4d.core.api.authentication`。
Controller 不调用 `tokenSession().set(...)`，当前 Principal 通过本次 Call 选择。

## 扩展方式

认证方式和 Provider 由应用或模块以 Spring Bean 提供，认证引擎只按容器中实际注册的 Bean 工作。
`AuthenticationMethod` 以 Bean 形式注册，`AuthenticationMethods` 通过 `List<AuthenticationMethod<?>>` 注入收集它们。
实现方式见[认证方法、凭据与认证因素](/capabilities/authentication-session/authentication-methods)。
