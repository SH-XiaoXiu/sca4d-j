# AuthProperties

源码：`modules/auth/sca4dj-auth/src/main/java/com/sca4d/auth/config/AuthProperties.java`

| 配置键 | 默认值 | 限制 |
|---|---|---|
| `sca4dj.auth.token.mode` | `SESSION` | `SESSION` 或 `JWT` |
| `AUTH:SESSION_TTL`（系统设置，环境变量后缀 `AUTH_SESSION_TTL`） | `P30D` | 必须大于零；由 `AuthSettings` 声明，不是 `sca4dj.auth` 配置键 |
| `sca4dj.auth.excluded-paths` | 空列表 | 不进入 AuthRequestInterceptor 的 Spring MVC 路径模式；不能包含空白模式 |
| `sca4dj.auth.jwt.active-key-id` | 无公共默认值 | JWT 模式必须存在于 keys |
| `sca4dj.auth.jwt.keys` | 空 Map | key id 到 Base64 HS256 密钥；解码后至少 32 字节 |
| `sca4dj.auth.jwt.issuer` | 无公共默认值 | JWT 模式必须非空且验证时精确匹配 |
| `sca4dj.auth.jwt.audience` | 无公共默认值 | JWT 模式必须非空且验证时精确匹配 |
| `AUTH:JWT:ACCESS_TTL`（系统设置，环境变量后缀 `AUTH_JWT_ACCESS_TTL`） | `PT5M` | 必须大于零；由 `AuthSettings` 声明，不是 `sca4dj.auth` 配置键 |
| `sca4dj.auth.jwt.max-token-length` | `6144` | 必须大于零，单位为字符 |

JWT 密钥独立于 `sca4dj.security.secret-keyring`。切换 active key 时，应保留仍可能用于验证未过期 JWT 的历史 key。超过最长 access TTL 后，可以从 keys 中删除旧 key。

`max-token-length` 限制完整 JWS 的字符数，不只限制 Session JSON。紧凑 Principal 和 Resource ID 使用 Roaring64 编码后，仍可能因 ID 分布而超过上限。

规模大或分布不可预测时，使用 `SESSION` 模式，或按真实数据测量后再调整限制。提高限制时，还要同时检查反向代理和应用服务器的请求头上限。

`SESSION` 模式不读取或校验 `active-key-id`、`keys`、`issuer` 和 `audience`。切换到 JWT 模式时，这四项必须由具体应用配置。脚手架不会用模块名、示例 App 名或其他固定值代替应用的签发者、受众和密钥。JWT 是同一份 Session 经签名后的序列化形式，因此切换模式不改变 Session 的内容。

`excluded-paths` 只跳过认证拦截器，不负责启用对应的 Controller 或第三方端点，也不提供权限检查。具体应用只能为当前运行环境确实允许匿名访问的路径配置排除模式。默认空列表使所有 Spring MVC Handler 继续执行认证检查。

## 示例

使用默认 `SESSION` 模式时，只在需要时配置排除路径：

```yaml
sca4dj:
  auth:
    token:
      mode: SESSION
    excluded-paths:
      - /workspaces/invitations/*/preview
```

切换到 JWT 模式时，应用提供签发者、受众和独立的 HS256 密钥：

```yaml
sca4dj:
  auth:
    token:
      mode: JWT
    jwt:
      active-key-id: k2
      keys:
        k1: ${WORKSPACE_JWT_KEY_K1}
        k2: ${WORKSPACE_JWT_KEY_K2}
      issuer: workspace-server
      audience: workspace-client
      max-token-length: 6144
```

`k1` 与 `k2` 的值是 Base64 编码、解码后至少 32 字节的密钥，由环境变量提供。
`k1` 保留到它签发的最后一个 JWT 过期之后，再从 `keys` 删除。
路径 `/workspaces/invitations/*/preview` 是示例，只有当前环境确实允许匿名访问时才配置。

## 扩展方式

`AuthProperties` 绑定 `sca4dj.auth` 前缀，应用只通过配置键调整，不需要提供 Bean。
JWT 模式的 `issuer`、`audience` 和 `keys` 必须由具体应用配置，脚手架不提供默认值。
配置值不满足上表限制时，`AuthProperties` 的校验会抛出 `IllegalArgumentException`，例如空白排除路径或不大于零的 JWT 最大长度。
