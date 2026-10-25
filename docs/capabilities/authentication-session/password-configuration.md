# PasswordProperties

源码：`modules/auth/sca4dj-auth-password/src/main/java/com/sca4d/auth/password/config/PasswordProperties.java`

PasswordProperties 读取 `sca4dj.auth.password` 配置：

| 配置键 | 默认值 | 含义 |
|---|---|---|
| `account-realm` | `platform` | Password 登录标识和 bootstrap 用户名所在 Realm |
| `login-identifier-types` | `USERNAME` | 登录时交给 AccountDirectory 解析的标识类型列表 |
| `bootstrap.enabled` | `true` | 是否为 bootstrap Account 补充初始 Password Credential |
| `bootstrap.username` | `admin` | bootstrap Account 的用户名 |
| `bootstrap.password` | 无 | 只在初始化 Credential 时使用的原始密码 |

`login-identifier-types` 必须至少包含一个非空白值，且不能重复。Password 模块只按这些稳定类型查询 AccountDirectory。它不依赖负责登记标识的具体模块。配置只描述 Password 如何向 `AccountDirectory` 查询 Account，不描述标识由谁登记，使 Password 模块与登记标识的模块保持解耦。

## 示例

以下配置让工作区成员同时用用户名和邮箱登录，并关闭 bootstrap 初始密码。
标识类型的取值必须是应用的 `AccountDirectory` 能解析的类型，下例的取值以应用实际登记的类型为准。

```yaml
sca4dj:
  auth:
    password:
      account-realm: platform
      login-identifier-types:
        - USERNAME
        - EMAIL
      bootstrap:
        enabled: false
```

保留 bootstrap 时，需要同时提供密码，且只在初始化 Credential 时读取。

```yaml
sca4dj:
  auth:
    password:
      bootstrap:
        enabled: true
        username: admin
        password: ${WORKSPACE_ADMIN_INITIAL_PASSWORD}
```

## 扩展方式

- 应用通过 `login-identifier-types` 选择标识类型，并保证 `AccountDirectory` 能解析这些类型。
- `PasswordProperties` 由 `PasswordAutoConfiguration` 绑定。`bootstrap.enabled` 为 `true` 或缺省时，才会注册 bootstrap 相关 Bean（`@ConditionalOnProperty`）。
