# 接入认证与 Session

本页以「一个 Account 加入多个工作区」为例，说明密码登录、敏感操作验证，以及把成员身份装入 Session 的方法。适合需要调用登录接口，或为应用增加新身份类型的开发者。

场景：用户用同一个 Account 登录管理端，并加入了数百个工作区（Workspace）。每个工作区中的成员（Member）身份是一个独立的 Principal。

需要解决三个问题：

- 怎样完成登录并取得 Token。
- 怎样为改密等敏感操作完成一次性验证。
- 怎样让 Session 包含全部成员身份，又不因身份数量过多而膨胀。

登录先创建认证尝试（attempt），再提交密码、验证码或 Passkey 等认证材料（proof）。要求满足后，服务端才返回 Token。普通请求携带 Token。敏感操作另外完成一次性验证（verification）。

新增登录入口时，复用 `AuthenticationAttempts` 和 `SessionLifecycle`。

| 需要完成的任务 | 入口 | 完成标志 |
|---|---|---|
| 登录管理端账号 | 下方密码登录或 SMS Code 登录 | `data.session` 非空 |
| 修改密码等敏感操作 | 创建 verification，再调用目标业务接口 | 目标业务请求成功，不能只看 proof 成功 |
| 增加自己的身份类型 | 身份 Loader 与 PrincipalSelection | Session 包含允许选择的身份，请求只能选择其中之一 |
| 决定请求是否在线读取 Session | Token 模式 | 明确接受 JWT 延迟失效，或使用在线 Session Token |

## 实现步骤

### 1. 完成一次密码登录

前置条件：已按[第一次运行](/tutorials/local-start)启动服务。

1. 打开 Gateway 的 `http://localhost:8088/api/swagger-ui/index.html`。
2. 调用 `POST /login/attempts`，请求字段如下：

```json
{
  "identifier": "<用户名或已配置的登录标识>",
  "password": "<账号密码>"
}
```

通过 Gateway 手动发送请求时，公开路径为 `POST /api/login/attempts`。初始账号和密码来自本地 `.env/iam.env` 与 `.env/auth.env`。不要把这两个值写入脚本，也不要提交到仓库。

响应状态为 201，`Location` 指向 `/login/attempts/{attemptId}`。响应中的 `data.authentication` 表示认证进度。

- `data.session` 有值时，登录已经建立 Session。
- `data.session` 为空时，根据返回的进度，继续向该 attempt 提交所需的 proof。密码 proof 的路径为 `POST /login/attempts/{attemptId}/password/proofs`。

密码校验成功，不一定已经满足多因素要求。

`data.session` 中包含 `tokenMode`、`accessToken`、`accessTokenExpiresAt`、`sessionId` 和 `sessionExpiresAt`。`sessionToken` 只在 JWT 模式下返回。SESSION 模式下，`accessToken` 本身就是不透明 Session Token。

取得 Token 后，以 `Authorization: Bearer <accessToken>` 调用 `GET /api/sessions/current`。响应返回当前 Session 和有效权限。

注销使用 `DELETE /api/sessions/current`。该接口要求不透明 Session Token，JWT 模式下使用 `sessionToken`。

请求绑定和响应转换位于 `AuthenticationController`、`PasswordLoginRequest` 与 `LoginResultView`，登录流程位于 `PlatformLoginService`。其他认证方法的路由见[登录与 Session 接口](/reference/http/sca4d-server#登录、verification-与-session)。

### 2. 为敏感操作完成 verification

改密、删除凭据和删除账号等操作需要单独验证。按以下顺序操作：

1. 带当前 Session 调用 `POST /verifications`，创建 verification。
2. 按返回的进度，提交允许的 proof，例如 `POST /verifications/{verificationId}/password/proofs`。
3. 把得到的 verification 放入最终业务请求的 `X-Verification` 头。

verification 绑定当前 Session、Account 和动作编码，只能消费一次。完成 proof 后，密码或账号仍未修改，必须继续调用目标业务接口。

动作编码、允许的方法及请求字段，以[应用 HTTP 参考](/reference/http/sca4d-server)中对应接口为准。

### 3. 使用 SMS Code 登录或完成 verification

SMS Code 依赖三个条件：

- Account 已绑定规范 E.164 `PHONE` Identifier。
- `AUTHENTICATION/SMS_CODE` 存在启用的 SMS Provider 绑定。
- Account 的手机号可以由 `SmsRecipientResolver` 解析。

Provider 和绑定配置见[发布消息与通知](/tutorials/messaging-and-notification)。

路由由具体应用的 Controller 暴露。下面的路径沿用脚手架约定，与邮件验证码的路径结构相同（`/login/email-code/...`）。使用前以应用的 HTTP 参考为准。

手机号登录先签发 Challenge：

```http
POST /api/login/sms-code/challenges
Content-Type: application/json

{"phoneNumber":"+12025550123"}
```

响应只返回 `challengeId`、脱敏接收人、`expiresAt` 和 `resendAt`。匿名接口对不存在或不可投递的手机号，也返回相同格式的 Challenge，不据此暴露账号是否存在。这种 Challenge 不会发送短信，也不能完成登录。

收到六位验证码后，提交同一个 Challenge：

```http
POST /api/login/sms-code/proofs
Content-Type: application/json

{
  "challengeId": "<challengeId>",
  "code": "123456"
}
```

成功响应为 201，并在 `Location` 中给出登录 attempt。

- `data.session` 不为空时，登录已经完成。
- `data.session` 仍为空时，根据 `data.authentication` 继续提交账号策略要求的其他 proof。

验证码只能使用一次。过期、错误次数达到上限、Challenge 不存在或已经消费时，都不能再次使用。

密码登录已经确定 Account，但策略继续要求 SMS Code 时，不再提交手机号。调用 `POST /api/login/attempts/{attemptId}/sms-code/challenges` 签发 Challenge。再把 `challengeId` 和 `code` 提交到 `POST /api/login/attempts/{attemptId}/sms-code/proofs`。

敏感操作的顺序相同：

1. 创建 `POST /api/verifications`。
2. 调用 `POST /api/verifications/{verificationId}/sms-code/challenges` 签发 Challenge。
3. 调用 `POST /api/verifications/{verificationId}/sms-code/proofs` 提交验证码。

这两个接口要求当前 Session。服务端还会确认 verification 对应的 Account 与当前 Session 的 Account 相同。proof 完成后，把 verification 放入目标业务请求的 `X-Verification` 头。

当前 Profile 手机号还没有绑定为登录 Identifier 时，先以动作 `AUTHENTICATION.PHONE.VERIFY` 完成 verification。然后使用在线 Session Token 调用 `PUT /api/me/phone-identifier`。该接口读取当前 Profile 手机号，不接受客户端另传手机号。只有这一步成功后，才能使用该号码从匿名 SMS Code 登录入口查找 Account。

Challenge 的有效期、重发间隔和最大错误次数由三项 SystemSetting 管理，定义位于 `SmsCodeSettings`：

| SystemSetting | 默认值 |
|---|---|
| `AUTH:SMS_CODE:CHALLENGE_TTL` | 5 分钟 |
| `AUTH:SMS_CODE:RESEND_INTERVAL` | 60 秒 |
| `AUTH:SMS_CODE:MAXIMUM_ATTEMPTS` | 5 |

修改这些 SystemSetting 只影响之后签发或校验的 Challenge。修改方法见[字典与设置](/tutorials/settings-and-dictionaries)。不要把验证码、手机号或 AccessKey 写入日志。

### 4. 把成员身份装入 Session

具体应用完成登录认证后，把 Account 和成功的 Authentication 列表交给 `SessionLifecycle.establish(...)`。`sca4dj-auth` 会重新读取 Subject 和 Account，并调用应用注册的 Loader，建立同一份权威 Session。

使用 `PrincipalLoader` 完整装载数量较少、请求经常直接读取的 Principal。使用 `PrincipalIdLoader` 保存同一种类型的大量 Principal ID。本例中一个 Account 加入很多工作区，而每次请求只选择一个 WorkspaceMemberPrincipal，因此选用后者。

两种方式可以同时使用，但同一个 Principal ID 在整个 Session 中只能出现一次。

下面的类表示典型的接入方式。`WorkspaceMemberPrincipal`、`WorkspaceMemberships` 和查询方法由具体应用定义。

```java
/** 把当前账号的有效成员身份作为紧凑 Principal ID 装入 Session。 */
@Component
public final class WorkspaceMemberPrincipalIdLoader
        implements PrincipalIdLoader<WorkspaceMemberPrincipal> {

    private final WorkspaceMemberships memberships; // 由具体应用定义

    public WorkspaceMemberPrincipalIdLoader(WorkspaceMemberships memberships) {
        this.memberships = memberships;
    }

    /** 只为支持成员身份的账号装载 ID。 */
    @Override
    public boolean supports(@NotNull SessionIdentity identity) {
        return memberships.supports(identity.account().accountId());
    }

    /** 返回请求期对象必须精确匹配的 Principal 类型。 */
    @Override
    public @NotNull Class<WorkspaceMemberPrincipal> principalType() {
        return WorkspaceMemberPrincipal.class;
    }

    /** 返回账号当前全部有效成员身份的 Principal ID。 */
    @Override
    public @NotNull Collection<@NotNull Long> load(@NotNull SessionIdentity identity) {
        return memberships.findActivePrincipalIds(identity.account().accountId());
    }
}
```

仅保存 ID 的类型仍必须注册 `PrincipalCodec`。Session 序列化时使用 Codec 的 `type()` 返回值，恢复对应的精确 Java 类型。

```java
@Component
public final class WorkspaceMemberPrincipalCodec implements PrincipalCodec<WorkspaceMemberPrincipal> {

    /** 写入 Session 后不能修改。 */
    public static final TreeCode TYPE = TreeCode.ofColon("WORKSPACE:MEMBER_PRINCIPAL");

    @Override
    public TreeCode type() {
        return TYPE;
    }

    @Override
    public Class<WorkspaceMemberPrincipal> valueType() {
        return WorkspaceMemberPrincipal.class;
    }

    @Override
    public byte[] encode(WorkspaceMemberPrincipal value) {
        return ByteBuffer.allocate(Long.BYTES * 2)
                .putLong(value.principalId())
                .putLong(value.workspaceId())
                .array();
    }

    @Override
    public WorkspaceMemberPrincipal decode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        return new WorkspaceMemberPrincipal(buffer.getLong(), buffer.getLong());
    }
}
```

`version()` 默认返回 1。未来修改 payload 结构时，提高版本并通过相邻版本的迁移步骤转换。

Loader 返回普通 ID 集合即可，业务模块不直接构造位图。`PrincipalIdLoaders` 校验每种 Principal 类型只有一个来源。Session 校验正数、重复值，以及完整 Principal 和紧凑集合之间的冲突。`SessionCodec` 再把每种类型编码成一个带 V1 Codec 版本的 Roaring64 集合。

请求进入 `/workspaces/{workspaceId}/...` 后，PrincipalSelection 按 Account 和 `workspaceId` 查询当前 WorkspaceMemberPrincipal，并返回完整候选对象。选择器写法见[接入身份与权限](/tutorials/identity-and-permission)。`Call.selectPrincipal(...)` 再校验该对象的精确 Java 类型，以及 ID 是否存在于 Session 中。查询业务表不能把 Session 集合之外的 Principal 变成可信对象。登录时装载的 Subject、Account、Principal 集合和静态 Resource 保存在 Session 中，请求期只能在这个集合内选择。

### 5. 预装数字 Resource ID（可选）

`ResourceIdLoader` 可以为完整 Principal 批量返回 `ResourceIdSet`。每个 `ResourceIdSet` 由 Resource 类型的 TreeCode 和非空 ID 集合构成。

```java
@Component
public final class WorkspaceDocumentIdLoader implements ResourceIdLoader {

    private final DocumentGrants grants; // 由具体应用定义

    public WorkspaceDocumentIdLoader(DocumentGrants grants) {
        this.grants = grants;
    }

    @Override
    public Map<Long, ? extends Collection<ResourceIdSet>> load(
            Collection<? extends Principal> principals) {
        Map<Long, List<ResourceIdSet>> result = new HashMap<>();
        for (Principal principal : principals) {
            List<Long> ids = grants.findDocumentIds(principal.principalId());
            if (!ids.isEmpty()) { // ResourceIdSet 不接受空集合
                result.put(
                        principal.principalId(),
                        List.of(new ResourceIdSet(Document.RESOURCE_TYPE, ids)));
            }
        }
        return result;
    }
}
```

之后检查 `Resource.type()` 为 `WORKSPACE/DOCUMENTS` 且 `Resource.id()` 为 `"1927273629"` 时，可以直接命中该集合。Resource ID 必须使用没有前导零的正十进制文本。

紧凑 Principal 不预装 Resource。它进入当前请求后，其角色、操作和数据范围由业务 PermissionPolicy 判断。

### 6. 在成员变化时刷新 Session

建立和刷新 Session 时，都会重新调用完整 Principal、紧凑 Principal ID、完整 Resource 和数字 Resource ID 的 Loader。

新增、停用或移除成员身份，以及修改需要随 Session 更新的静态资源后，业务模块必须调用 `SessionControl`，使目标 Account 的 Session 失效或刷新。

```java
// 在成员变更的事务提交后调用
sessionControl.invalidateAccount(accountId);
```

`SessionControl` 另有 `invalidateAccounts(...)`、`terminateAccount(...)` 和 `terminateOtherSessions(...)`。

### 7. 选择 Token 模式

`sca4dj.auth.token.mode` 支持 `SESSION` 和 `JWT`，默认为 `SESSION`。

```yaml
sca4dj:
  auth:
    token:
      mode: SESSION
    jwt:
      max-token-length: 6144
```

两种模式使用同一份 Session 序列化内容。Sa-Token Redis Token-Session 始终保存权威 Session。JWT 只是该序列化内容的短效签名版本。

SESSION 模式只向客户端发送不透明 Token，完整 Session 保存在 Redis 中。每次在线读取都会比较 Token-Session 保存的 Operation 目录指纹。具体 Operation 编码变化后，旧 Session 在下一次请求时重新装载身份和静态 Resource，不需要部署时清空 Redis。

JWT 模式把完整 Session 放进 JWS，因此受 `sca4dj.auth.jwt.max-token-length` 限制，默认最多 6144 个字符。载荷只签名、不加密。

普通 JWT 请求不会在线读取 Token-Session，也不会立即发现 Operation 目录变化。旧权限快照最多保留到短效 access Token 自身过期。要求立即读取权威 Session 的接口，必须使用不透明 Session Token。`POST /sessions/current/tokens` 使用当前不透明 Session Token 在线签发新的 access Token，不延长或轮换底层 Session。

`PrincipalIdSet` 和 `ResourceIdSet` 在序列化内容中使用 Roaring64 portable 编码。它不是按最大 ID 建立普通 `long[]` 位数组。高 32 位先分组，低位再根据分布选择数组、位图或连续区间容器。因此 `1927273629` 这样的 ID 不会单独制造约 230 MiB 的空位。连续或聚集的 ID 通常会显著缩小。

压缩率仍取决于 ID 分布。大量随机、彼此相距很远的 ID 可能超过 JWT 长度上限。`SessionCodec` 在恢复时，也会把集合展开为排序的 `long[]`。

具体应用需要按真实数据测量。集合规模大或分布不可预测时，优先使用 SESSION 模式。脚手架的接口约定测试确认，10,000 个从 `1927273629` 开始的连续 Principal ID 可以在默认 JWT 上限内完成签发和恢复。这不代表任意 10,000 个 ID 都有相同大小。

完整配置见 [AuthProperties](/reference/runtime/sca4d-server)。

## 验证

登录后检查 Session 内容：

```http
GET /api/sessions/current
Authorization: Bearer <accessToken>
```

预期响应：200，响应包含当前 Session 的公开身份和有效权限。

验证成员身份已经装入 Session：

1. 用加入两个工作区的 Account 登录。
2. 调用 `GET /api/workspaces/{workspaceId}/documents/{documentId}`，路径依次使用两个工作区的 ID，预期各自选择对应的 WorkspaceMemberPrincipal。
3. 使用 Account 未加入的工作区 ID，预期被拒绝。
4. 在应用中移除其中一个成员身份并调用 `SessionControl.invalidateAccount(...)`，再次请求原 Token，预期 Session 已失效或不再包含该身份。

验证 verification：

1. 带当前 Session 调用 `POST /api/verifications` 并完成 proof。
2. 携带 `X-Verification` 调用目标业务接口，预期成功。
3. 使用同一个 verification 再次调用，预期失败，因为 verification 只能消费一次。

## 失败结果与限制

登录与 verification 的错误码来自 `AuthErrorCode`：

| 错误码 | 含义 | HTTP 状态 |
|---|---|---|
| `2100` `AUTHENTICATION_FAILED` | 认证材料或账号状态不允许建立登录会话 | 401 |
| `2101` `AUTH_ATTEMPT_ID_CONFLICT` | 认证尝试标识冲突，需要重新发起 | 409 |
| `2102` `AUTH_ATTEMPT_EXPIRED` | 认证尝试不存在或已过期 | 409 |
| `2103` `AUTH_ATTEMPT_SATISFIED` | 认证尝试已经满足，不能继续提交 | 409 |
| `2104` `AUTH_ATTEMPT_INCOMPLETE` | 认证尝试尚未满足当前动作策略 | 409 |
| `2105` `AUTH_METHOD_UNAVAILABLE` | 认证方法当前不可用 | 503 |

- 调用需要 verification 的接口时缺少 `X-Verification` 头，示例应用的 `ActionAuthenticationInterceptor` 抛出 `AUTHENTICATION_REQUIRED`（`1001`），HTTP 状态为 401。
- 注销、刷新 Token 等声明 `@RequireSessionToken` 的接口，要求不透明 Session Token。
- 匿名 SMS Code 接口对不存在的手机号返回相同格式的 Challenge，不暴露账号是否存在。验证码过期、错误次数达到上限或已消费时，不能再次使用。
- 同一 Principal ID 在完整 Principal 与紧凑集合中同时出现、Loader 返回非正数或重复 ID、多个 `PrincipalLoader` 认领同一身份时，Session 建立失败，抛出 `IllegalArgumentException` 或 `IllegalStateException`。
- 同类 `PrincipalCodec` 使用重复的 Java 类型或序列化类型时，`SessionCodec` 初始化抛出 `IllegalArgumentException`。
- JWT 超过 `sca4dj.auth.jwt.max-token-length` 时无法签发。
- 成员变化后没有调用 `SessionControl`，旧 Session 继续携带旧的 Principal 集合。JWT 模式下，旧快照最多保留到 access Token 过期。

## 相关页面

- [接入身份与权限](/tutorials/identity-and-permission)
- [字典与设置](/tutorials/settings-and-dictionaries)
- [发布消息与通知](/tutorials/messaging-and-notification)
- [认证尝试与 Session 模块](/home/authentication-session)
- [Sca4d Server HTTP API](/reference/http/sca4d-server)
