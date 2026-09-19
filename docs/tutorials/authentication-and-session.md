# 接入认证与 Session

Sca4d Server 通过登录 attempt 收集认证结果，满足账号的认证要求后返回 Session Token。新增登录入口时复用 `AuthenticationAttempts` 和 `SessionLifecycle`；业务接口通过已恢复的 Session 选择 Principal。

## 完成一次密码登录

先按[第一次运行](/tutorials/local-start)启动服务。打开 Gateway 的 `http://localhost:8088/api/swagger-ui/index.html`，调用 `POST /login/attempts`，请求字段为：

```json
{
  "identifier": "<用户名或已配置的登录标识>",
  "password": "<账号密码>"
}
```

通过 Gateway 手动发送请求时，公开路径为 `POST /api/login/attempts`。初始账号和密码来自本地 `.env/iam.env` 与 `.env/auth.env`；不把这两个值写入脚本或提交到仓库。

响应 `data.authentication` 表示认证进度。`data.session` 有值时，登录已经建立 Session；为空时，根据返回进度继续向该 attempt 提交所需 proof。密码校验成功不一定已经满足多因素要求。

取得 Session Token 后，以 `Authorization: Bearer <sessionToken>` 调用 `GET /api/sessions/current`。响应返回当前 Session 和有效权限。注销使用 `DELETE /api/sessions/current`，该接口要求在线 Session Token。

请求绑定和响应转换分别位于 `AuthenticationController`、`PasswordLoginRequest` 与 `LoginResultView`；登录流程位于 `PlatformLoginService`。其他认证方法的路由见[登录与 Session 接口](/reference/http/sca4d-server#登录、verification-与-session)。

## 为敏感操作完成 verification

改密、删除凭据和删除账号等操作需要单独验证。先带当前 Session 创建 `POST /verifications`，按返回进度提交允许的 proof，再把得到的 verification 放入最终业务请求的 `X-Verification` 头。

verification 绑定当前 Session、Account 和动作编码，只能消费一次。完成 proof 后，密码或账号仍未修改，必须继续调用目标业务接口。动作编码、允许方法及请求字段以[应用 HTTP 参考](/reference/http/sca4d-server)中对应接口为准。

## 使用 SMS Code 登录或完成 verification

SMS Code 依赖三个条件：Account 已绑定规范 E.164 `PHONE` Identifier，`authentication.sms-code` 存在启用的 SMS Provider 绑定，Account 的手机号可以由 `SmsRecipientResolver` 解析。Provider 和绑定配置见[发布消息与通知](/tutorials/messaging-and-notification#配置一个阿里云-sms-provider)。

手机号登录先签发 Challenge：

```http
POST /api/login/sms-code/challenges
Content-Type: application/json

{"phoneNumber":"+12025550123"}
```

响应只返回 `challengeId`、脱敏接收人、`expiresAt` 和 `resendAt`。匿名接口对不存在或不可投递的手机号也返回相同形状的 Challenge，不据此暴露账号是否存在；这种 Challenge 不会发送短信，也不能完成登录。

收到六位验证码后，提交同一个 Challenge：

```http
POST /api/login/sms-code/proofs
Content-Type: application/json

{
  "challengeId": "<challengeId>",
  "code": "123456"
}
```

成功响应为 201，并在 `Location` 中给出登录 attempt。`data.session` 不为空时登录已经完成；仍为空时，根据 `data.authentication` 继续提交账号策略要求的其他 proof。验证码单次消费，过期、错误次数达到上限、Challenge 不存在或已经消费时都不能再次使用。

密码登录已经确定 Account，但策略继续要求 SMS Code 时，不再提交手机号。调用 `POST /api/login/attempts/{attemptId}/sms-code/challenges` 签发 Challenge，再把 `challengeId` 和 `code` 提交到 `POST /api/login/attempts/{attemptId}/sms-code/proofs`。

敏感操作的调用顺序相同。先创建 `POST /api/verifications`，再调用 `POST /api/verifications/{verificationId}/sms-code/challenges` 和 `POST /api/verifications/{verificationId}/sms-code/proofs`。这两个接口要求当前 Session；服务端还会确认 verification 对应的 Account 与当前 Session Account 相同。proof 完成后，把 verification 放入目标业务请求的 `X-Verification` 头。

当前 Profile 手机号还没有绑定为登录 Identifier 时，先以动作 `authentication.phone.verify` 完成 verification，再使用强制 Session Token 调用 `PUT /api/me/phone-identifier`。该接口读取当前 Profile 手机号，不接受客户端另传手机号；成功后才可使用这个号码从匿名 SMS Code 登录入口查找 Account。

Challenge 有效期、重发间隔和最大错误次数由 `auth.sms-code.challenge-ttl`、`auth.sms-code.resend-interval` 和 `auth.sms-code.maximum-attempts` 管理。修改这些 SystemSetting 只影响之后签发或校验的 Challenge，不应把验证码、手机号或 AccessKey 写入日志。

## 在应用中装载身份

登录确认 Account 后，`SessionLifecycle` 重新读取 Subject 与 Account，并调用应用注册的 Loader 建立 Session。业务入口使用 `PrincipalSelection` 从这份 Session 中选择身份，不能接受任意客户端 `principalId`。

## 会话建立与身份装载

具体应用完成登录认证后，把 Account 和成功的 Authentication 列表交给 SessionLifecycle。`sca4dj-auth` 会重新读取 Subject 和 Account，并调用应用安装的 Loader 建立同一份权威 Session。

### 选择完整对象还是紧凑 ID

使用 PrincipalLoader 完整装载数量较少、请求经常直接读取的 Principal。使用 PrincipalIdLoader 保存同一种类型的大量 Principal ID，例如一个账号加入很多项目，而每次项目请求只会选择一个 ProjectMemberPrincipal。

两种方式可以同时使用，但同一个 Principal ID 在整个 Session 中只能出现一次。仅保存 ID 的类型仍必须安装 PrincipalCodec；Session wire 使用 Codec 的 `type()` 返回值恢复对应的精确 Java 类型。

下面的类表示一种典型接入形状，其中 ProjectMemberPrincipal、ProjectMembershipRepository 和查询方法由具体应用定义：

```java
/** 把当前账号的有效项目成员身份作为紧凑 Principal ID 装入 Session。 */
@Component
public final class ProjectMemberPrincipalIdLoader
        implements PrincipalIdLoader<ProjectMemberPrincipal> {

    private final ProjectMembershipRepository memberships;

    public ProjectMemberPrincipalIdLoader(ProjectMembershipRepository memberships) {
        this.memberships = memberships;
    }

    /** 只为支持项目成员身份的账号装载 ID。 */
    @Override
    public boolean supports(@NotNull SessionIdentity identity) {
        return memberships.supports(identity.account().accountId());
    }

    /** 返回请求期对象必须精确匹配的 Principal 类型。 */
    @Override
    public @NotNull Class<ProjectMemberPrincipal> principalType() {
        return ProjectMemberPrincipal.class;
    }

    /** 返回账号当前全部有效项目成员身份的 Principal ID。 */
    @Override
    public @NotNull Collection<@NotNull Long> load(@NotNull SessionIdentity identity) {
        return memberships.findActivePrincipalIds(identity.account().accountId());
    }
}
```

Loader 返回普通 ID 集合即可。PrincipalIdLoaders 校验每种 Principal 类型只有一个来源，Session 校验正数、重复值以及完整 Principal 和紧凑集合之间的冲突，SessionCodec 再把每种类型编码成一个 Roaring64 集合。业务模块不直接构造位图。

请求进入 `/projects/{projectId}/...` 后，PrincipalSelection 可以按 Account 和 `projectId` 查询当前 ProjectMemberPrincipal。它返回完整候选对象，`Call.selectPrincipal(...)` 再校验该对象的精确 Java 类型和 ID 是否存在于 Session。查询业务表不能把 Session 集合外的 Principal 变成可信对象。

### 数字 Resource ID

ResourceIdLoader 可以为完整 Principal 批量返回 ResourceIdSet：

```java
return Map.of(
        principal.principalId(),
        List.of(new ResourceIdSet("project", List.of(1927273629L, 1927273630L))));
```

之后检查 `Resource.type() == "project"` 且 `Resource.id() == "1927273629"` 时可以直接命中该集合。Resource ID 必须使用没有前导零的正十进制文本。紧凑 Principal 不预装 Resource；它进入当前请求后的角色、操作和数据范围由业务 PermissionPolicy 判断。

### 刷新要求

建立和刷新 Session 都会重新调用完整 Principal、紧凑 Principal ID、完整 Resource 和数字 Resource ID 的 Loader。新增、停用或移除项目成员身份，以及修改需要随 Session 更新的静态资源后，业务模块必须调用 SessionControl 使目标 Account 的 Session 失效或刷新。

## Token 模式

`sca4dj.auth.token-mode` 支持 `SESSION` 和 `JWT`。两种模式使用同一份 Session wire，Sa-Token Redis Token-Session 始终保存权威 Session；JWT 只是该 wire 的短效签名投影。

SESSION 模式只向客户端发送不透明 Token，完整 Session 保存在 Redis。每次在线读取都会比较 Token-Session 保存的 Operation 目录指纹；具体 Operation 编码变化后，旧 Session 在下一次请求重新装载身份和静态 Resource，不需要部署时清空 Redis。

JWT 模式把完整 Session 放进 JWS，因此受 `sca4dj.auth.jwt.max-token-length` 限制，默认最多 6144 个字符，并且载荷只签名、不加密。普通 JWT 请求不会在线读取 Token-Session，也不会立即发现 Operation 目录变化；旧权限快照最多保留到短效 access Token 自身过期。要求立即读取权威 Session 的接口必须使用不透明 Session Token。

PrincipalIdSet 和 ResourceIdSet 在 wire 中使用 Roaring64 portable 编码。它不是按最大 ID 建立普通 `long[]` 位数组：高 32 位先分组，低位再根据分布选择数组、位图或连续区间容器。因此 `1927273629` 这样的 ID 不会单独制造约 230 MiB 的空位；连续或聚集 ID 通常会显著缩小。

压缩率仍取决于 ID 分布。大量随机、彼此很远的 ID 可能超过 JWT 长度上限，SessionCodec 也会在恢复时把集合展开为排序 `long[]`。具体应用需要按真实数据测量；集合规模大或分布不可预测时优先使用 SESSION 模式。脚手架的契约测试确认 10,000 个从 `1927273629` 开始的连续 Principal ID 可以在默认 JWT 上限内完成签发和恢复，这不代表任意 10,000 个 ID 都有相同大小。

完整配置见 [AuthProperties](/reference/runtime/sca4d-server)，在线状态和注销差异见[认证尝试与 Session 模块](/home/authentication-session)。
