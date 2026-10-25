# 应用标识配置

本页说明具体应用使用的三类标识（后端应用标识、前端命名空间和产品展示名称），以及 Sca4d-J 公共名称。本页适用于部署和命名具体应用的开发者。读完后，开发者能确定每类名称的声明位置，以及修改后会产生什么结果。

具体应用使用三类名称区分后端运行时、前端浏览器存储和对外展示。三类名称各自负责不同对象，不能相互替代。Sca4d-J 公共名称由脚手架固定，具体应用不得改名。

| 名称 | 示例值 | 声明位置 | 用途 |
|---|---|---|---|
| 后端应用标识 | `<应用名>-server` | `spring.application.name` | 日志、Redis key、Quartz 命名空间、环境变量前缀和运行时命名空间 |
| 前端命名空间 | `<应用名>-admin` | 前端环境变量 `VITE_APP_NAMESPACE` | Store、偏好设置和浏览器缓存前缀 |
| 产品展示名称 | 具体应用定义的展示名 | 示例应用的产品设置 `PRODUCT:DISPLAY_NAME` 及前端标题配置 | OpenAPI 标题、浏览器标题和页面品牌 |
| Sca4d-J 公共名称 | `com.sca4d.*`、`com.sca4dj`、`sca4dj.*`、`META-INF/sca4dj` | Core、Modules、Gateway 与 Tools | 公共 Java 包、Gradle 插件、模块配置和资源约定 |

## 示例

下面的配置为一个文档协作应用声明后端和前端标识。
同一应用的所有实例使用相同的 `spring.application.name`。

```yaml
spring:
  application:
    name: docs-server
```

```dotenv
# JWT 模式：issuer 与 audience 必须显式设置，源码不以 spring.application.name 作为默认值
SCA4DJ_AUTH_JWT_ISSUER=docs-server
SCA4DJ_AUTH_JWT_AUDIENCE=docs-server
# 产品展示名称：环境变量前缀默认为 spring.application.name 规范化后的值（DOCS_SERVER）
DOCS_SERVER_PRODUCT_DISPLAY_NAME=文档协作平台
# 前端环境变量
VITE_APP_NAMESPACE=docs-admin
```

另一个应用共享同一数据库时，必须改用不同的值，例如 `workspace-server`。

## 后端标识

后端应用标识决定同一应用的多个实例是否共享运行时状态。同一应用的所有实例必须使用相同的应用标识。共享同一数据库的不同应用，必须使用不同的应用标识。Redis key、Quartz 命名空间和环境变量前缀都从应用标识派生，与其他模块从同一份应用声明派生运行时状态的做法一致。

修改已经运行的应用标识，会形成新的命名空间。

Quartz scheduler name 由 `spring.application.name` 派生。派生结果不得超过 `quartz.qrtz_*` 表 `sched_name` 的 120 字符限制。设置细节见[任务与调度配置](/capabilities/jobs-scheduling/quartz-configuration)。

## 产品设置

示例应用的 `ProductSettings` 把展示名称注册为产品设置，分组为 `PRODUCT`，键为 `PRODUCT:DISPLAY_NAME`。`PRODUCT` 分组未在 `core/`、`modules/` 或 `gateway/` 中定义。没有数据库覆盖值时，使用 Java 默认值。具体应用应在部署时通过 ENV 初始化。

ENV 名称由环境变量前缀与设置的环境变量后缀组成。前缀默认取 `spring.application.name` 规范化后的值（大写，`-` 替换为 `_`），例如 `docs-server` 的前缀为 `DOCS_SERVER`。设置读取和初始化规则见[字典与系统设置](/capabilities/data-configuration/dictionaries-and-settings)。

## 前端标识

`VITE_APP_NAMESPACE` 与前端版本、运行模式 `dev` 或 `prod` 共同组成浏览器存储前缀。修改命名空间后，浏览器不会继续读取旧前缀中的登录状态。

Vben 持久化 Store 的本地编码密钥会随前端代码交付。它不能作为后端密钥或 Token 签名密钥。

## JWT 标识

使用 JWT 时，issuer 和 audience 必须由具体应用设置，对应配置键为 `sca4dj.auth.jwt.issuer` 与 `sca4dj.auth.jwt.audience`，也可以通过 `SCA4DJ_AUTH_JWT_ISSUER` 与 `SCA4DJ_AUTH_JWT_AUDIENCE` 环境变量设置。

SESSION 模式不要求 JWT issuer、audience 或签名密钥。认证模式和密钥要求见 [Sca4d Server HTTP API](/reference/http/sca4d-server#登录与敏感-verification)。
