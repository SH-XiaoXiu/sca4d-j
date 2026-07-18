# HTTP 接口

## 响应格式

成功响应不返回消息：

```json
{
  "code": 0,
  "messageKey": null,
  "messageArgs": [],
  "message": null,
  "data": {},
  "traceId": "6b6f2d2c7d5f4a34",
  "timestamp": 1780000000000
}
```

失败响应使用对应的 HTTP 状态，`code` 为业务错误码，`messageKey` 和 `messageArgs` 用于识别错误，`message` 使用当前请求语言，`traceId` 用于查询日志。

## 响应 Header

| Header | 说明 |
| --- | --- |
| `X-Trace-Id` | 请求 traceId |
| `Content-Language` | `message` 使用的语言 |
| `Idempotency-Replayed` | 当前响应来自幂等记录 |
| `Retry-After` | 限流或请求处理中建议等待的秒数 |
| `X-Client-State-Stale` | 客户端保存的状态整体过期；只由 `ClientState.stale()` 输出 |
| `X-Client-State-Refresh` | 需要重新请求的精确目标，例如 `me`、`menus`、`tenant-access` |
| `X-Client-State-Version` | 客户端状态版本 |

## 登录和当前用户

| Method | Path | 认证 | 返回数据 |
| --- | --- | --- | --- |
| GET | `/authentication-methods` | 匿名 | `AuthMethodView[]` |
| POST | `/authentications` | LOGIN、REGISTRATION、RECOVERY 匿名；STEP_UP、LINKING 已登录 | 匿名请求使用 `scopeType + tenantCode`；返回 `201` 和 `AuthenticationView`，同时写入 HttpOnly 浏览器 Cookie |
| GET | `/authentications/{authenticationId}` | 浏览器 Cookie | `AuthenticationView` |
| POST | `/authentications/{authenticationId}/password-proofs` | 浏览器 Cookie | `AuthenticationView` |
| POST | `/authentications/{authenticationId}/recovery-code-proofs` | RECOVERY 认证 + 浏览器 Cookie | 单次消费恢复码并返回 `AuthenticationView` |
| POST | `/authentications/{authenticationId}/password-replacements` | 已完成的 RECOVERY 认证 + 浏览器 Cookie | 替换 PASSWORD Credential、结束该 Account 全部登录会话并返回 `204` |
| POST | `/authentications/{authenticationId}/email-code-challenges` | 浏览器 Cookie | `201` 和 `EmailCodeChallengeView` |
| POST | `/authentications/{authenticationId}/email-code-proofs` | 浏览器 Cookie | `AuthenticationView` |
| POST | `/authentications/{authenticationId}/totp-proofs` | 浏览器 Cookie | 原子消费 TOTP 时间步并返回 `AuthenticationView` |
| POST | `/authentications/{authenticationId}/passkey-assertions` | 浏览器 Cookie | 创建 WebAuthn assertion 并返回 `navigator.credentials.get()` options |
| POST | `/authentications/{authenticationId}/passkey-proofs` | 浏览器 Cookie + assertion | 单次消费 assertion、验证 Passkey 并返回 `AuthenticationView` |
| POST | `/sessions` | 已完成的 LOGIN 或 REGISTRATION 认证 + 浏览器 Cookie | 请求体提交 `authenticationId`，返回 `201` 和 `LoginSessionView` |
| DELETE | `/sessions/current` | 已登录 | `204` |
| GET | `/me` | 已登录 | `MeView` |
| PUT | `/me/avatar` | 已登录 | 替换当前 Subject 的 DEFAULT Profile 头像；提交 fileId、uploadGrant、profileVersion |
| DELETE | `/me/avatar` | 已登录 | 清空当前 Subject 的 DEFAULT Profile 头像；提交 profileVersion |

外部身份源接口：

| Method | Path | 认证 | 返回数据 |
| --- | --- | --- | --- |
| GET | `/auth/oidc/providers` | 匿名 | 使用 `scopeType + tenantCode` 查询可用 OIDC Provider |
| GET | `/auth/oauth2/providers` | 匿名 | 使用 `scopeType + tenantCode` 查询可用 OAuth2 Provider |
| GET | `/auth/ldap/providers` | 匿名 | 使用 `scopeType + tenantCode` 查询可用 LDAP Provider |
| POST | `/authentications/{authenticationId}/oidc-authorizations` | 浏览器 Cookie | 创建包含 state、nonce 和 PKCE challenge 的 OIDC 授权请求 |
| GET | `/auth/oidc/callback` | 匿名 + 浏览器 Cookie | 校验 OIDC callback 并使用 `303` 返回管理端完成页 |
| POST | `/authentications/{authenticationId}/oauth2-authorizations` | 浏览器 Cookie | 创建包含 state 和 PKCE challenge 的 OAuth2 授权请求 |
| GET | `/auth/oauth2/callback` | 匿名 + 浏览器 Cookie | 换取 Token、读取 Profile 并使用 `303` 返回管理端完成页 |
| POST | `/authentications/{authenticationId}/ldap-proofs` | 浏览器 Cookie | 使用目录搜索和用户 bind 提交 LDAP 账号与密码 |

OIDC 和 OAuth2 callback 只提交当前认证的外部身份验证结果，URL 不包含 Session Token、授权码或上游 Token。LOGIN 验证完成后仍需调用 `POST /sessions`；LINKING 完成后不会创建 Session。本项目不提供面向下游应用的 OAuth2 或 OIDC Authorization Server。

匿名作用域参数不包含数据库主键。GLOBAL 请求只提交 `scopeType=GLOBAL`；TENANT 请求同时提交 `scopeType=TENANT` 和租户业务编码 `tenantCode`。服务端在创建认证或查询 Provider 时解析当前 ACTIVE 租户，并把内部 `tenantId` 保存为 Flowable 变量。

`GET /me` 接受可选查询参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `principalType` | `DEFAULT`、`PLATFORM`、`TENANT` | 缺省时使用 `DEFAULT` |
| `tenantId` | `long` | `principalType=TENANT` 时必填 |

### 账号安全

根路径：`/me/accounts`

| Method | 子路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| GET | `/` | 已登录 | 查询当前 Subject 名下 Account、Identifier、Credential、Passkey 非敏感资料、2FA 和恢复码剩余数量 |
| GET | `/{accountId}/sessions` | 已登录 | 查询一个 Account 的活跃登录会话，不返回 Token |
| GET | `/{accountId}/external-identities` | 已登录 | 查询一个 Account 已关联的外部身份，不返回上游 Token |
| GET | `/{accountId}/external-identities/providers` | 已登录 | 按 Account 内部作用域查询可以关联的 ACTIVE Provider，不返回 Provider 主键或管理配置 |
| DELETE | `/{accountId}/external-identities/{identityId}` | 已登录 + STEP_UP + `If-Match` | 解除关联；不能删除当前 Account 最后一个可登录方式 |
| PUT | `/{accountId}/second-factor/requirement` | 已登录 + STEP_UP | 启用、重新签发或关闭第二因子要求，并结束目标 Account 的全部旧登录会话 |
| DELETE | `/{accountId}/sessions/{sessionId}` | 已登录 + STEP_UP | 结束目标 Account 的一个登录会话，返回 `204` |
| POST | `/{accountId}/totp/enrollments` | 已登录 + STEP_UP | 创建浏览器绑定的短期 TOTP enrollment |
| GET | `/{accountId}/totp/enrollments/{enrollmentId}/qr-code` | 已登录 + 浏览器 Cookie | 返回禁止缓存的 `image/png` 二维码 |
| POST | `/{accountId}/totp/credentials` | 已登录 + enrollment | 使用首次验证码创建 ACTIVE TOTP Credential |
| PUT | `/{accountId}/totp/credentials/{credentialId}` | 已登录 + enrollment + `If-Match` | 使用首次验证码替换指定版本 TOTP Credential，返回新 ETag |
| DELETE | `/{accountId}/totp/credentials/{credentialId}` | 已登录 + STEP_UP + `If-Match` | 删除指定版本 TOTP Credential 并结束目标 Account 全部登录会话 |
| POST | `/{accountId}/passkey/registrations` | 已登录 + STEP_UP | 创建浏览器绑定的 WebAuthn registration，返回 `navigator.credentials.create()` options |
| POST | `/{accountId}/passkey/credentials` | 已登录 + registration | 单次消费 registration 并创建 Passkey Credential，返回强 ETag |
| DELETE | `/{accountId}/passkey/credentials/{credentialId}` | 已登录 + STEP_UP + `If-Match` | 删除指定版本 Passkey Credential 并结束目标 Account 全部登录会话 |

STEP_UP 操作必须同时提交 `X-Authentication-Id` 和创建认证时写入的 HttpOnly Cookie。认证创建时固定当前 Session Account；客户端不能使用请求参数切换账号。恢复码只在启用或重新签发响应中返回一次，后续查询只返回签发时间和剩余数量。

账号安全响应使用 `credentials` 返回所有 Credential 的公共类型、状态和版本；`passkeyCredentials` 单独返回 Passkey 展示名称、备份状态和最近使用时间。两个集合通过 `credentialId` 对应。响应不包含 WebAuthn credential ID、公钥、AAGUID 或 user handle。

RECOVERY 认证必须提交 GLOBAL 或 TENANT Account 作用域。恢复码输入同时提交该作用域内的 USERNAME 或 EMAIL Identifier；格式错误、账号不存在、恢复码无效和恢复码已使用返回相同认证错误。每个认证 UUID 和来源地址在五分钟内最多提交五次恢复码。密码替换失败后，已消费的恢复码和认证不能再次使用。

## IdentityProvider 通用管理

GLOBAL 根路径：`/mg/system/auth/providers`。TENANT 根路径：`/mg/tenant/tenants/{tenantId}/auth/providers`。

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 查询精确作用域中的 Provider，不返回协议秘密 |
| POST | `/` | 创建 code 和协议不可变、默认停用的 Provider |
| GET | `/{providerId}` | 查询 Provider 通用字段并返回 ETag |
| PUT | `/{providerId}` | 使用 `If-Match` 修改名称、注册策略和邮箱关联开关 |
| PATCH | `/{providerId}` | 使用 `If-Match` 启用或停用；协议配置不完整时不能启用 |
| DELETE | `/{providerId}` | 使用 `If-Match` 删除停用且没有 ExternalIdentity 的 Provider |

协议配置使用 Provider 下的协议子资源：

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/{providerId}/oidc` | 查询 OIDC 配置，不返回 Client Secret |
| PUT | `/{providerId}/oidc` | 使用父 Provider `If-Match` 创建或完整替换 OIDC 配置 |
| POST | `/{providerId}/oidc/discoveries` | 读取并校验当前 issuer 的 Discovery 文档 |
| GET | `/{providerId}/oauth2` | 查询 OAuth2 端点和 JSONPath 映射，不返回 Client Secret |
| PUT | `/{providerId}/oauth2` | 使用父 Provider `If-Match` 创建或完整替换 OAuth2 配置 |
| POST | `/{providerId}/oauth2/previews` | 使用已保存映射预览示例 Profile，不请求外部端点 |
| GET | `/{providerId}/ldap` | 查询 LDAP 搜索和属性映射，不返回服务账号密码 |
| PUT | `/{providerId}/ldap` | 使用父 Provider `If-Match` 创建或完整替换 LDAP 配置 |
| POST | `/{providerId}/ldap/tests` | 使用已保存配置建立目录连接并完成服务账号 bind |

协议秘密只写。提交空秘密会在替换配置时保留现有密文；响应、OpenAPI、日志和审计均不返回明文、key-id、nonce 或 ciphertext。

## 系统目录

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/system/permissions` | 查询权限目录 |
| GET | `/system/error-codes` | 查询错误码目录 |

## 文件

| Method | Path | 说明 |
| --- | --- | --- |
| POST | `/files/signatures` | 批量申请上传票据或读取地址 |
| PUT | `/files/uploads/{fileId}/content` | PROXY 模式上传文件内容 |
| PUT | `/files/uploads/{fileId}/completion` | 幂等确认上传完成 |
| GET | `/files/{fileId}/content` | 使用短期 token 读取文件 |

## 实时通信

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/realtime/subscription` | 查询当前连接可以使用的订阅信息 |
| GET | `/realtime/sse` | 建立 SSE 连接 |

两个客户端状态事件为 `authentication.session.changed` 和 `system.menus.changed`。WebSocket endpoint 为 `/realtime/ws`。默认 STOMP 应用前缀为 `/app`，Broker 前缀为 `/topic` 和 `/queue`，用户前缀为 `/user`。

## 通知

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/notifications` | 通知分页 |
| GET | `/notifications/unread-count` | 未读数量 |
| PUT | `/notifications/{notificationId}/read-status` | 幂等标记一条通知已读 |
| PUT | `/notifications/read-status` | 幂等标记全部通知已读 |

## 即时通信

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/im/conversations` | 会话分页 |
| POST | `/im/conversations` | 创建单聊或群聊 |
| GET | `/im/conversations/{conversationId}/messages` | 消息分页 |
| POST | `/im/conversations/{conversationId}/messages` | 创建文本或图片消息，返回 `201` 和 `ImMessageView` |
| PUT | `/im/messages/{messageId}/read-status` | 幂等更新已读位置 |
| DELETE | `/im/messages/{messageId}` | 撤回当前 Principal 自己发送的消息 |

## Account 与操作员

GLOBAL Account 根路径：`/mg/platform/accounts`；租户 Account 根路径分别为 `/mg/platform/tenants/{tenantId}/accounts` 和 `/mg/tenant/tenants/{tenantId}/accounts`。Account 只管理登录标识、认证方式、MFA、恢复码和 Session，不返回角色。

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 账号分页 |
| POST | `/` | 创建 GLOBAL Account，并生成 Subject、DEFAULT Principal 和 DEFAULT Profile |
| GET | `/{accountId}/security` | 认证方式、MFA 与恢复码摘要 |
| GET | `/{accountId}/sessions` | 活跃 Session，不返回 Token |
| PUT | `/{accountId}/second-factor/requirement` | 使用二步验证和 Account `If-Match` 调整 MFA 要求 |
| POST | `/{accountId}/credentials/password` | 使用二步验证和 Account `If-Match` 绑定密码 |
| PUT | `/{accountId}/credentials/{credentialId}/password` | 使用二步验证和 Credential `If-Match` 重置密码 |
| DELETE | `/{accountId}/sessions/{sessionId}` | 使用二步验证和 Account `If-Match` 撤销指定 Session |

平台操作员根路径：`/mg/platform/operators`。PLATFORM Principal 管理平台角色和租户 ACL，不修改 Account 认证材料。

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 操作员分页 |
| POST | `/` | 基于既有 GLOBAL Account 创建 PLATFORM Principal |
| GET | `/{principalId}/assignable-roles` | 查询可绑定的平台角色 |
| PATCH | `/{principalId}` | 修改 PLATFORM Principal 状态 |
| PUT | `/{principalId}/roles` | 替换平台角色 |

默认头像源文件只接受 JPEG、PNG、WebP 且最大 5 MB，管理端裁剪上传为 512×512 WebP。DEFAULT Profile 替换和清空使用 `profileVersion` 乐观锁；版本冲突返回 409。头像字段为 `FileSignatureRefView`，客户端通过 `/files/signatures` 批量换取短期读取地址。

## 平台租户

根路径：`/mg/platform/tenants`

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 租户分页 |
| GET | `/{tenantId}` | 租户详情 |
| POST | `/` | 创建租户 |
| PUT | `/{tenantId}` | 更新租户资料 |
| PATCH | `/{tenantId}` | 修改租户状态 |
| DELETE | `/{tenantId}` | 删除租户 |

## 角色

平台角色根路径为 `/mg/platform/roles`。平台代管租户角色根路径为 `/mg/platform/tenants/{tenantId}/roles`。租户工作区角色根路径为 `/mg/tenant/tenants/{tenantId}/roles`。

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 角色列表 |
| GET | `/permissions` | 可分配权限 |
| POST | `/` | 创建角色 |
| GET | `/{roleId}` | 角色详情 |
| PUT | `/{roleId}` | 更新角色 |
| PUT | `/{roleId}/permissions` | 替换角色权限 |
| GET | `/{roleId}/usage` | 查询角色使用数量 |
| DELETE | `/{roleId}` | 删除角色 |

## 租户成员

平台代管根路径为 `/mg/platform/tenants/{tenantId}/members`。租户工作区根路径为 `/mg/tenant/tenants/{tenantId}/members`。

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/assignable-roles` | 查询成员可以绑定的启用角色集合 |
| GET | `/` | 成员分页 |
| GET | `/{principalId}` | 成员详情 |
| POST | `/` | 创建用户并加入租户 |
| PATCH | `/{principalId}` | 修改成员 Principal 状态 |
| PUT | `/{principalId}/roles` | 替换成员角色 |

## 平台租户授权

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/mg/platform/principals/{principalId}/tenant-access-grants` | 查询目标 PLATFORM Principal 的默认动作与租户授权集合 |
| PUT | `/mg/platform/principals/{principalId}/tenant-access-grants` | 使用 Principal 版本和幂等键原子替换租户 ACL |

RBAC 决定主体是否拥有某类功能权限；租户 ACL 决定该主体对每个租户允许的最高 `READ`、`WRITE` 或 `MANAGE` 动作。两者必须同时成立。

## 字典

系统字典根路径为 `/mg/system/dictionaries`。平台代管租户字典根路径为 `/mg/platform/tenants/{tenantId}/dictionaries`。租户工作区字典根路径为 `/mg/tenant/tenants/{tenantId}/dictionaries`。

管理视图通过 `source` 区分 `CODE` 与 `CONFIGURATION`。`CODE_ENUM + CODE` 内容只读；`BUSINESS_CODE + CODE` 允许增加普通项，代码保留项只能修改展示属性。完整声明和同步规则见[系统字典](/infrastructure/dictionaries)。

| Method | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 字典列表 |
| POST | `/` | 创建字典 |
| GET | `/{dictionaryId}` | 字典详情 |
| PUT | `/{dictionaryId}` | 更新字典 |
| DELETE | `/{dictionaryId}` | 删除字典 |
| GET | `/{dictionaryId}/items` | 字典项列表 |
| POST | `/{dictionaryId}/items` | 创建字典项 |
| PUT | `/{dictionaryId}/items/{itemId}` | 更新字典项 |
| DELETE | `/{dictionaryId}/items/{itemId}` | 删除字典项 |
| GET | `/options` | 查询输入选项 |
| GET | `/order` | 查询完整字典排序列表 |
| PUT | `/order` | 原子保存完整字典顺序和各项版本 |
| GET | `/{dictionaryId}/items/order` | 查询完整字典项排序列表 |
| PUT | `/{dictionaryId}/items/order` | 原子保存完整字典项顺序和各项版本 |

## 系统菜单

根路径：`/mg/system/menus`

| Method | 子路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/` | `system:menus:read` | 查询完整菜单树 |
| GET | `/route-options` | `system:menus:read` | 查询代码注册的前端路由 |
| POST | `/` | `system:menus:manage` | 创建菜单分组或路由菜单 |
| PUT | `/{menuId}` | `system:menus:manage` | 更新父菜单、路由、标题、图标和显示状态 |
| PUT | `/order` | `system:menus:manage` | 原子保存同一父菜单下的完整顺序和各项版本 |
| DELETE | `/{menuId}` | `system:menus:manage` | 删除菜单及其下级节点 |

创建请求字段：

| 字段 | 类型 | 要求 |
| --- | --- | --- |
| `parentId` | `long \| null` | 根菜单使用 `null` |
| `routeCode` | `string \| null` | 路由菜单使用已注册路由编码；分组可以为空 |
| `titles` | `Record<string, string>` | 必须包含语言目录中标记为 required 的标题；单个标题 1～128 字符 |
| `icon` | `string` | Lucide 导出名称，例如 `LayoutDashboard` |
| `visible` | `boolean` | 必填 |
| `idempotencyKey` | `string` | 创建时使用 |

创建的菜单追加到同级末尾。更新请求使用相同字段，并增加必填 `version`。删除请求包含 `version` 和 `idempotencyKey`。排序请求包含 `parentId` 和完整 `{menuId, version}` 顺序，不接受客户端排序数字。

## 邮件发送配置

根路径：`/mg/system/mail-senders`

| Method | 子路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/` | `system:mail-senders:read` | 查询全部 SMTP 配置，不返回密码加密数据 |
| GET | `/{senderId}` | `system:mail-senders:read` | 查询一项 SMTP 配置，返回强 `ETag` |
| POST | `/` | `system:mail-senders:manage` | 使用 `Idempotency-Key` 创建默认停用配置，返回 201、ETag 和完整表示 |
| PUT | `/{senderId}` | `system:mail-senders:manage` | 使用强 `If-Match` 更新完整配置；password 为空时保留当前密码 |
| PATCH | `/{senderId}` | `system:mail-senders:manage` | 使用强 `If-Match` 启用或停用配置 |
| POST | `/{senderId}/connection-tests` | `system:mail-senders:manage` | 建立并关闭 SMTP 连接，返回 204，不发送邮件 |
| DELETE | `/{senderId}` | `system:mail-senders:manage` | 使用强 `If-Match` 删除已经停用的配置，返回 204 |

创建或更新认证连接时 username 与 password 必须同时存在；改为匿名连接时清空 username，并通过 `clearPassword=true` 清除旧密码。缺少 `If-Match` 返回 428，无效或旧版本返回 412。

## 邮件模板

邮件模板接口位于 `/mg/system`：

| Method | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/mail-templates` | `system:mail-templates:read` | 查询全部未删除模板及变量名 |
| GET | `/mail-templates/{templateId}` | `system:mail-templates:read` | 查询模板完整表示，返回强 `ETag` |
| POST | `/mail-templates` | `system:mail-templates:manage` | 使用 `Idempotency-Key` 创建模板，返回 201、ETag 和完整表示 |
| PUT | `/mail-templates/{templateId}` | `system:mail-templates:manage` | 使用强 `If-Match` 更新模板完整内容 |
| PATCH | `/mail-templates/{templateId}` | `system:mail-templates:manage` | 使用强 `If-Match` 启用或停用模板 |
| DELETE | `/mail-templates/{templateId}` | `system:mail-templates:manage` | 使用强 `If-Match` 删除停用模板，返回 204 |
| POST | `/mail-template-previews` | `system:mail-templates:manage` | 渲染未保存草稿并返回清洗后的 HTML |
| POST | `/mail-templates/{templateId}/test-messages` | `system:mail-templates:manage` | 使用已保存模板发送测试邮件，返回 204 |

模板由 `family + locale` 唯一确定，只允许 `{{name}}` 变量。Section、Partial、未转义变量和反射属性名均被拒绝；HTML 在预览和发送前使用同一清洗规则。

## 操作日志

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/mg/system/operation-logs/catalog` | 查询操作目录 |
| GET | `/mg/system/operation-logs/operator-options` | 查询操作者选项 |
| GET | `/mg/system/operation-logs` | 操作日志分页 |
| GET | `/mg/system/operation-logs/{id}` | 操作日志详情 |

操作日志的 `actorReference` 和 `targetReference` 使用统一资源引用结构。`label` 是可空业务名称，`navigationMessage` 是不可导航原因。已删除资源存在审计名称快照时同时返回历史名称和删除原因；旧记录没有快照时 `label` 为空。

## 业务调度

平台 Schedule 使用 PLATFORM Principal。

| Method | Path | 权限 | 结果与请求头 |
| --- | --- | --- | --- |
| GET | `/mg/platform/schedules` | `platform:schedules:read` | Schedule 分页；支持 keyword、scheduleType、status、taskType、page、size |
| GET | `/mg/platform/schedules/{scheduleId}` | `platform:schedules:read` | 返回完整 Schedule 和 `ETag` |
| POST | `/mg/platform/schedules` | `platform:schedules:manage` | 必须提供 `Idempotency-Key`；返回 201、ETag 和完整 Schedule |
| PUT | `/mg/platform/schedules/{scheduleId}` | `platform:schedules:manage` | 必须提供单个强 `If-Match`；返回新 ETag |
| DELETE | `/mg/platform/schedules/{scheduleId}` | `platform:schedules:manage` | 必须提供单个强 `If-Match`；返回 204 |
| GET | `/mg/platform/schedules/{scheduleId}/executions` | `platform:schedules:read` | 执行记录分页 |
| GET | `/mg/platform/schedules/{scheduleId}/executions/{executionId}` | `platform:schedules:read` | 单次执行记录 |
| POST | `/mg/platform/schedules/{scheduleId}/executions` | `platform:schedules:manage` | 必须提供 `Idempotency-Key`；返回 202 和 PENDING 执行记录 |
| GET | `/mg/platform/schedule-task-types` | `platform:schedules:read` | 当前已注册任务类型 |

缺少 `If-Match` 返回 428。弱 ETag、`*`、多个 ETag、非数字或旧版本返回 412。

## 系统调度任务

| Method | Path | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/mg/system/scheduled-jobs` | `system:scheduled-jobs:read` | 查询系统 Job、Trigger 状态和最近运行结果 |
| GET | `/mg/system/scheduled-jobs/{jobGroup}/{jobName}` | `system:scheduled-jobs:read` | 查询指定系统 Job |

## 健康检查和 OpenAPI

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/actuator/health` | 总体健康状态 |
| GET | `/actuator/health/liveness` | 进程存活状态 |
| GET | `/actuator/health/readiness` | 是否可以接收流量 |
| GET | `/v3/api-docs` | OpenAPI JSON；由 `SCA4DJ_OPENAPI_ENABLED` 控制 |
| GET | `/swagger-ui/index.html` | Swagger UI；由 `SCA4DJ_OPENAPI_ENABLED` 控制 |
