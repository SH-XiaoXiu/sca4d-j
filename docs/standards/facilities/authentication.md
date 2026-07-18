# 认证规则

## 单一认证入口

HTTP 和 STOMP 必须通过同一个认证入口建立 Subject、Account、Session 和 CurrentRequest。业务 Controller 禁止自行解析 Token 或直接读取 Sa-Token Session 判断当前主体。

## Subject、Account 与 Principal

Subject 表示用户主档，Account 表示一个账号范围中的登录账号，Principal 表示本请求执行主体。三者禁止合并。Session 固定一个 Account；多个 Principal 必须经过 `PrincipalSelection` 校验 Account 可达性。

## 请求局部选择

当前 Principal 只存在于本次 CurrentRequest。禁止调用 `tokenSession().set(...)` 保存当前 Principal，禁止让一个请求改变另一个并发请求的工作上下文。

## 匿名入口

接口默认需要认证；只有明确允许匿名的入口使用 `@AnonymousAccess`。类级与方法级覆盖规则必须可预测，禁止以路径白名单建立第二套匿名体系。

## 凭据

凭据绑定、验证和变更使用 `CredentialScheme<C, P>` 与 `CredentialService`。登录、注册、绑定、敏感操作确认和账号恢复由 `AuthMethod<P>` 声明允许的认证目的。`AuthenticationProcessService` 使用 Flowable ProcessInstance 和 UserTask 检查当前允许提交的认证方式；密码、邮箱验证码、TOTP 验证码、恢复码、OAuth/OIDC Token、LDAP 密码和 WebAuthn 请求或响应不能写入 Flowable 表、日志、Session、审计 Payload、事件或响应。

认证定义使用 Flowable `BpmnModel` Java API 创建，不保存手写 BPMN XML。当前任务编码固定为 `primary-proof`、`second-factor-proof` 和 `completion`。认证方式实现只验证自身输入并返回 Subject ID、Account ID、Credential 或 ExternalIdentity ID 及版本号，不读取 Flowable API、任务编码或过程变量。

## 认证资源

公共认证接口使用以下资源：

| Method | Path | 行为 |
| --- | --- | --- |
| `GET` | `/authentication-methods` | 返回当前 Spring 容器已经注册的认证方式及允许的认证目的 |
| `POST` | `/authentications` | 创建绑定浏览器、认证目的和设备的短期认证 |
| `GET` | `/authentications/{authenticationId}` | 返回当前任务、Subject ID、Account ID 和过期时间 |
| `POST` | `/authentications/{authenticationId}/password-proofs` | 验证登录标识和密码 |
| `POST` | `/authentications/{authenticationId}/recovery-code-proofs` | 单次消费恢复码 |
| `POST` | `/authentications/{authenticationId}/password-replacements` | 使用已完成的 RECOVERY 认证替换密码并结束目标 Account 的全部登录会话 |
| `POST` | `/authentications/{authenticationId}/email-code-challenges` | 创建并投递邮件验证码 Challenge |
| `POST` | `/authentications/{authenticationId}/email-code-proofs` | 单次消费 Challenge 并验证邮件验证码 |
| `POST` | `/authentications/{authenticationId}/totp-proofs` | 原子消费 Account 的 TOTP 时间步 |
| `POST` | `/authentications/{authenticationId}/passkey-assertions` | 创建绑定认证和浏览器的 WebAuthn assertion challenge |
| `POST` | `/authentications/{authenticationId}/passkey-proofs` | 单次消费 assertion 并验证 WebAuthn 响应 |
| `POST` | `/authentications/{authenticationId}/oidc-authorizations` | 创建 OIDC Authorization Code + PKCE 请求 |
| `POST` | `/authentications/{authenticationId}/oauth2-authorizations` | 创建 OAuth2 Authorization Code + PKCE 请求 |
| `POST` | `/authentications/{authenticationId}/ldap-proofs` | 验证 LDAP Provider、目录账号和 LDAP 密码 |
| `POST` | `/sessions` | 单次完成认证并创建 Account-bound Session |
| `DELETE` | `/sessions/current` | 结束当前 Session |

接口不保留 `/auth/flows`、`/auth/login/password` 或 `/authentication` 兼容路由。Controller 只转换 Request 与 View；认证阶段检查、并发完成和过期处理只由 `AuthenticationProcessService` 调用 Flowable API。

## 前端术语与确认样式

登录过程的第二因子和 `STEP_UP` 认证在用户界面中统一称为“二步验证”。`STEP_UP`、`primary-proof`、`second-factor-proof` 等只属于接口、流程和源码术语，不得展示为“STEP_UP”“再次验证”“再次认证”或“二次验证”。按钮、标题、说明、成功提示和错误提示使用同一术语。

“敏感操作确认”只表示当前操作的风险上下文，不是另一种认证方式。删除 Passkey、删除 TOTP、解除外部身份关联、结束登录会话、关闭二步验证等破坏性操作，在二步验证对话框中使用独立警告说明区，明确对象、不可逆影响和会话失效范围，并使用危险操作按钮。添加或更换认证方式、启用二步验证等非破坏性操作仍执行二步验证，但使用中性说明和普通主按钮。

`/authentication-methods` 只返回当前 Spring 容器实际注册的 `AuthMethod`。配置关闭邮件验证码、身份源或其他可选方式时，对应方式不得出现在目录中；接口不能根据枚举全集补齐未注册方式。邮箱验证码、TOTP 和恢复码分别使用 `EMAIL_CODE`、`TOTP` 和 `RECOVERY_CODE`，不声明通用验证码类型。

匿名 LOGIN、REGISTRATION 和 RECOVERY 必须提交合法的 Account 作用域。GLOBAL 只提交 `scopeType=GLOBAL`；TENANT 提交 `scopeType=TENANT` 和当前租户业务编码 `tenantCode`。服务端确认租户为 ACTIVE 后取得内部 `tenantId`，Flowable 只保存解析后的主键。匿名接口不得返回或接收内部 `scopeId`。STEP_UP 和 LINKING 必须携带当前 Session，Subject、Account 和作用域全部来自该 Session；客户端不能通过作用域参数或其他主键切换目标 Account。未登录请求创建 STEP_UP 或 LINKING 认证时返回未认证错误。

匿名 OIDC、OAuth2 和 LDAP Provider 查询使用与 LOGIN 认证相同的 `scopeType + tenantCode`。账号安全页查询可关联 Provider 时，服务端按当前 Subject 名下的 Account 取得内部作用域，客户端不得把 Account 的 `scopeId` 传给匿名 Provider 查询接口。

邮件验证码 Challenge 只允许用于 `second-factor-proof`，或已经确定当前 Session Account 的 STEP_UP `primary-proof`。响应只包含 Challenge ID、脱敏收件人、有效期和可重发时间。邮件验证码、完整邮箱和浏览器摘要不得返回客户端。`EMAIL_CODE` 允许用于 STEP_UP，不能与 LOGIN 的第一种认证方式重复；创建 Session 或完成 STEP_UP 前必须重新确认 Challenge 的消费状态和版本。

PASSWORD 允许用于 LOGIN 和 STEP_UP。STEP_UP 的密码仍按 Account 作用域解析 Identifier，`AuthenticationProcessService` 必须确认返回的 Subject 与 Account 等于创建时绑定的当前 Session Account。已完成 STEP_UP 认证不能创建 Session；敏感操作必须调用 `AuthenticationProcessService.consumeStepUp(...)`，在 Subject 集群锁内复核 Credential、Subject、Account 和当前 Session Account 后单次完成 Flowable 任务。业务操作失败后不恢复已经完成的认证。

RECOVERY_CODE 只允许用于 RECOVERY。恢复码在当前认证的账号范围内解析 USERNAME 或 EMAIL Identifier；未知账号、格式错误、摘要不匹配和并发重复消费必须返回同一认证错误。每次请求固定执行十次 Argon2id 校验，并同时按认证 UUID 和来源地址使用 Redis 限流。数据库条件更新必须包含当前集合、集合版本和 `used_at IS NULL`。密码替换前在 Subject 集群锁内复核已使用记录、Account、Subject 和 PASSWORD Credential；成功后结束该 Account 的全部登录会话。失败时不恢复恢复码或认证。

TOTP 只允许用于 STEP_UP，可作为 LOGIN 的第二因子或当前 Session Account 的敏感操作确认。密钥使用 googleauth 生成，经带 Credential 主键用途编码的 AES-256-GCM keyring 加密后写入 `iam_totp`。验证码匹配当前时间前后一个 30 秒时间步；数据库只在 `last_accepted_step` 严格变大且 Credential、Account、Subject 均可认证时更新，因此同一时间步在多个节点并发提交时只有一次成功。TOTP 按来源地址和认证 UUID 限流，enrollment 首次验证码按来源地址和 enrollment 限流。Flowable 保存 Credential 主键和版本，不保存验证码、TOTP 密钥或时间步。

Passkey 允许用于 LOGIN 和 STEP_UP。注册必须要求 discoverable credential 和用户验证；assertion 必须校验 RP ID、精确 Origin、challenge、用户验证、签名计数和 backup eligibility。registration 与 assertion 使用独立 Redis key 和 Lua CAS 单次消费；客户端响应失败后重新创建 ceremony，旧 challenge 不恢复。`iam_passkey` 只保存 credential ID、COSE 公钥、signCount、AAGUID、备份标志、展示名和使用时间，私钥始终由认证器保管。每次成功 assertion 使用 Credential 版本、旧 signCount 和 backup eligibility 原子更新协议子表和公共 Credential 版本；并发或重放只有一个请求可以更新。

## IdentityProvider 管理资源

GLOBAL Provider 使用 `/mg/system/auth/providers`，TENANT Provider 使用 `/mg/tenant/tenants/{tenantId}/auth/providers`。Provider code 和协议创建后不可修改；名称、JIT 注册策略和可信邮箱自动关联开关使用强 `If-Match` 更新。创建结果固定为 DISABLED，启用前必须由对应 OIDC、OAuth2 或 LDAP 协议实现确认子表配置完整。通用 Provider Service 不读取协议端点、Client Secret、JSONPath、LDAP DN 或密码。

IdentityProvider 只能在停用且没有未删除 ExternalIdentity 时删除。Mapper 的删除 SQL 同时检查状态、版本、作用域和 `NOT EXISTS`，不能依赖先查询再无条件删除。GLOBAL Provider 只能关联 GLOBAL Account；TENANT Provider 只能关联相同 tenantId 的 TENANT Account。

## 账号安全资源

当前用户的账号安全接口使用 `/me/accounts`。查询 Account、登录会话、ExternalIdentity 和可关联 Provider 只允许访问当前 Subject 名下资源。结束会话和修改第二因子要求必须携带 `X-Authentication-Id` 与认证浏览器 Cookie。

TOTP 设置使用 `/{accountId}/totp/enrollments` 和 `/{accountId}/totp/credentials`。创建 enrollment 必须先单次消费 STEP_UP；enrollment 在 Redis 中保存 Account、浏览器摘要、过期时间和 AES-256-GCM 密文。二维码资源返回禁止缓存的 PNG，不返回明文密钥或 otpauth URI。首次验证码通过后先用 Redis CAS 消费 enrollment，再创建或替换 ACTIVE Credential；后续数据库失败不恢复 enrollment。替换和删除 Credential 必须使用强 `If-Match`，成功响应返回新 ETag。Credential 数据库写入和旧 Session 失效必须在同一 Subject 集群锁和数据库事务内执行；Session 失效失败时回滚 Credential 写入，数据库提交失败时允许旧 Session 已经结束。要求第二因子的 Account 删除 TOTP 后必须保留另一种当前已注册且资料可用的第二因素；协议专属删除接口必须校验 Credential 类型，不能删除 PASSWORD 等其他认证材料。

Passkey 设置使用 `/{accountId}/passkey/registrations` 和 `/{accountId}/passkey/credentials`。创建 registration 前单次消费 STEP_UP；完成 registration 时先用 Redis CAS 消费 challenge，再由 Yubico WebAuthn Server 验证 attestation。首次 registration 只生成候选 user handle，激活时必须在同一事务写入 Account user handle、公共 Credential 和 Passkey 协议子表；并发 registration 已选定不同 handle 时返回冲突。一个 Account 可以保存多个 Passkey，WebAuthn credential ID 全局唯一，Account 使用独立随机 user handle。删除必须提交强 `If-Match` 和 STEP_UP，SQL 同时校验 PASSKEY 类型、Account 和版本；新增或删除成功后结束该 Account 的全部旧 Session。

`PUT /me/accounts/{accountId}/second-factor/requirement` 使用 Account 版本执行乐观写入。启用前必须至少有一种当前已注册认证方式确认 Account 所需资料可用；邮件验证码要求当前已验证 EMAIL Contact，TOTP 要求 ACTIVE TOTP Credential，启用 Passkey 时可以使用 ACTIVE PASSKEY Credential。Account Service 只调用可用性接口，不识别具体认证协议。启用或对已启用 Account 再次提交时生成 10 个新恢复码并使旧集合失效；关闭时撤销当前恢复码集合。恢复码明文只在当前响应中出现，数据库只保存 Argon2id 摘要，普通查询只返回签发时间和剩余数量。第二因子要求变化后必须结束目标 Account 的全部旧登录会话。

## 会话变化

Subject、Account、Principal 或权限状态变化后必须失效相关会话/权限快照，并通过 ClientState 提示前端刷新。停用 Subject 时结束全部 Account Session；Account 安全材料变化时只结束该 Account Session。

身份对象关系见[IAM 身份与访问模型](/architecture/iam.md)，认证方式的组合见[认证过程与 MFA](/architecture/authentication-and-mfa.md)，具体接口见[认证组件参考](/reference/interfaces-annotations.md)。
