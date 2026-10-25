# 组装应用

本页以 Workspace 应用为例。
应用要支持 Member 登录、Workspace 与 Document 管理、Attachment 上传，以及 Document 分享通知。
脚手架提供身份、认证、通知、文件、消息、Job 等可选模块。
问题是：选哪些模块，如何初始化数据库，以及应用自己的 Controller 和 Service 放在哪里。

本页以 `apps/<应用名>-server` 为起点，按下面的顺序选择模块、初始化数据库并验证启动。
后面的参考部分用于确认删减功能时还要处理哪些调用。

各模块维护自己的数据和公共规则。
具体应用维护自己的资料、流程与对外接口约定，并负责跨模块事务。

## 实现步骤

### 步骤 1：选择模块

以 `apps/<应用名>-server/build.gradle.kts` 为依赖示例，按业务需要保留模块。
Workspace 应用的最小组合如下：

```kotlin
plugins {
    id("com.sca4dj.build.java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.sca4dj.app")
}

dependencies {
    implementation(project(":core:sca4dj-core-spring"))
    implementation(project(":modules:iam:sca4dj-iam"))
    implementation(project(":modules:auth:sca4dj-auth"))
    implementation(project(":modules:auth:sca4dj-auth-password"))
    implementation(project(":modules:notification:sca4dj-notification"))
    implementation(project(":modules:notification:sca4dj-notification-email"))
    implementation(project(":modules:audit:sca4dj-audit"))
    implementation(project(":modules:web-realtime:sca4dj-web-realtime"))
    runtimeOnly(project(":modules:messaging:sca4dj-messaging"))
    runtimeOnly(project(":modules:job:sca4dj-job"))
    runtimeOnly(project(":modules:scheduling:sca4dj-scheduling-quartz"))
    runtimeOnly(project(":modules:storage:sca4dj-storage-file"))
    runtimeOnly(project(":modules:storage:sca4dj-storage-minio"))
}
```

上例只列出脚手架模块。
应用还需要自己声明 Spring MVC、Validation、Actuator、MyBatis、Flyway 和 PostgreSQL 驱动等依赖，见 `apps/<应用名>-server/build.gradle.kts`。
应用直接声明这些依赖，因此删除任一功能时可以从自己的依赖和配置中直接判断影响。
应用不依赖 Starter，也不依赖另一个应用来补齐模块。

下表列出各模块的职责。
`runtimeOnly` 列标出通常只在运行时引入的模块：

| 模块 | 职责 | 引入方式 |
|---|---|---|
| `core/sca4dj-core-spring` | Core API 的 Spring MVC、AOP、Redis、MyBatis 和自动配置接入 | `implementation` |
| `modules/iam/sca4dj-iam` | Subject、Account、Principal、Role、Operation 和授权关系 | `implementation` |
| `modules/auth/sca4dj-auth` | AuthenticationAttempts、认证方法目录、Session 与 Token | `implementation` |
| `modules/auth/sca4dj-auth-password` | Password Credential 和 proof | `implementation` |
| `modules/auth/sca4dj-auth-totp` | TOTP enrollment、Credential 和 proof | `implementation` |
| `modules/auth/sca4dj-auth-passkey` | Passkey ceremony、Credential 和 proof | `implementation` |
| `modules/auth/sca4dj-auth-email` | Email Code Challenge 和认证方法 | `implementation` |
| `modules/auth/sca4dj-auth-sms` | SMS Code Challenge、PHONE Identifier 和认证方法 | `implementation` |
| `modules/auth/sca4dj-auth-federation` | 外部身份提供方、授权流程和认证方法 | `implementation` |
| `modules/auth/sca4dj-auth-shared-secret` | 通用 Shared Secret Credential 和认证方法 | `implementation` |
| `modules/auth/sca4dj-auth-hmac` | 通用 HMAC Credential、防重放和认证方法 | `implementation` |
| `modules/auth/sca4dj-auth-x509` | 通用 X.509 Credential 和认证方法 | `implementation` |
| `modules/notification/sca4dj-notification` | 类型化通知、渠道路由与 Outbox 登记 | `implementation` |
| `modules/notification/sca4dj-notification-email` | SMTP 配置、Email 模板与投递 | `implementation` |
| `modules/notification/sca4dj-notification-inbox` | Account 站内信快照与已读状态 | `implementation` |
| `modules/notification/sca4dj-notification-sms` | SMS Provider、路由、绑定与投递尝试 | `implementation` |
| `modules/notification/sca4dj-notification-sms-aliyun` | 阿里云 SMS SDK 传输适配器 | `implementation` |
| `modules/audit/sca4dj-audit` | 审计写入与查询 | `implementation` |
| `modules/iot/sca4dj-iot` | Product、ThingModel、Device、当前状态、可靠下行和设备定时 | `implementation` |
| `modules/iot/sca4dj-iot-emqx` | EMQX HTTP 回调、MQTT 运行时、Topic 与协议转换 | `implementation` |
| `modules/iot/sca4dj-iot-timescale` | 独立 TimescaleDB 的 Telemetry 与事件历史 | `implementation` |
| `modules/web-realtime/sca4dj-web-realtime` | SSE、WebSocket 票据、STOMP 和节点路由 | `implementation` |
| `modules/messaging/sca4dj-messaging` | RabbitMQ 与 PostgreSQL Outbox | `runtimeOnly` |
| `modules/job/sca4dj-job` | 持久化 Job run 与 worker | `runtimeOnly` |
| `modules/scheduling/sca4dj-scheduling-quartz` | 代码计划与 Quartz 集群调度 | `runtimeOnly` |
| `modules/storage/sca4dj-storage-file` | 文件记录、短期传输授权和清理任务 | `runtimeOnly` |
| `modules/storage/sca4dj-storage-local` | 本地目录 backend | `runtimeOnly` |
| `modules/storage/sca4dj-storage-minio` | MinIO backend | `runtimeOnly` |
| `modules/openapi/sca4dj-openapi` | 开放 API 路由校验、Client 与 Token/HMAC 认证 | `implementation` |
| `modules/plugin/sca4dj-plugin-spring` | 插件运行时的 Spring 适配 | `implementation` |
| `gateway/sca4d-gateway` | 独立 Gateway 进程，不作为 App 依赖 | 独立工程 |

仓库的 `settings.gradle.kts` 当前没有 include `sca4dj-notification-inbox` 和 `sca4dj-notification-sms-aliyun`。
在应用中引用这两个模块前，先补充 include。

删除一项功能时，先查 App 中调用它的 Controller、Service 和配置，再一起移除页面与依赖。
例如删除 IoT 时，要同时处理设备接口、协议注册、EMQX 回调、TimescaleDB 配置和管理端设备入口。

### 步骤 2：设置应用名

给应用设置稳定的 `spring.application.name`。
同一应用的多个实例使用相同名称，不同应用使用不同名称。
Job、Quartz、Redis 和 RabbitMQ 的运行命名依赖它。

```yaml
spring:
  application:
    name: workspace-server
```

### 步骤 3：初始化数据库

新 App 尚无任何 Flyway 迁移时，应用 `com.sca4dj.app` 插件，按需配置 `sca4djApp`，然后运行该 App 的 `initFlyway`：

```kotlin
sca4djApp {
    migrationName.set("workspace")
    schemaFragment.set(layout.projectDirectory.file("src/main/resources/schema/app.sql"))
}
```

`migrationName` 未设置时，使用项目名规范化后的值。
需要应用表时才配置 `schemaFragment`，它指向 App 自己的表结构片段。
任务从运行依赖收集模块的结构片段，再加上 App 的片段，生成 `src/main/resources/db/migration/V1__init_<migrationName>.sql`。
设置 Gradle 属性 `sca4dj.init` 时，`processResources` 依赖 `initFlyway`。
具体配置见 [App Gradle 插件](/reference/build-plugins#app-gradle-插件)。

已有数据库通过新的 Flyway 迁移修改结构。
删减依赖不会自动删除数据库表，也不能通过重建 V1 更新已经运行的数据库。

### 步骤 4：配置外部服务并启动

配好 PostgreSQL、Redis、RabbitMQ 和保留功能需要的外部服务后，运行 App 的 `bootRun`。
按[第一次运行](/tutorials/local-start)检查启动、Gateway 注册和登录，再检查保留功能的主要接口。

外部服务的依赖关系：

- Redis 是在线 Session、失效代次、认证临时状态、分布式锁、Web Realtime 索引与票据、IoT HMAC 防重放所必需的运行依赖。
- RabbitMQ 是 Messaging 的必需依赖。Web Realtime 多实例节点路由或 STOMP relay 也可以使用它。
- 文件 backend 由 `sca4dj.storage.minio.enabled` 和 `sca4dj.storage.local.enabled` 选择，两者在模块中默认启用（`matchIfMissing = true`）。默认 backend 标识必须与已注册实现一致。切换到本地 backend 后可以不运行 MinIO，此时应关闭 MinIO backend。
- EMQX 和 TimescaleDB 只在对应 IoT 开关启用时需要，EMQX 回调由 `sca4dj.iot.emqx.enabled` 控制。

### 步骤 5：添加应用自己的业务代码

模块公开稳定的业务 API、数据规则和自动配置，不注册应用的业务 Controller。
Workspace 应用自己定义 Controller、Service 和 Repository：

```java
/** 绑定 HTTP 输入，声明权限，由具体应用定义。 */
@RestController
class DocumentController {

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping("/workspaces/{workspaceId}/documents/{documentId}/publish")
    @RequirePermission(operation = DocumentOperations.PUBLISH)
    ApiResponse<Void> publish(@PathVariable long workspaceId, @PathVariable long documentId) {
        documents.publish(workspaceId, documentId);
        return ApiResponse.ok();
    }
}
```

`DocumentService` 在一个事务中写入 `workspace_document` 表，并通过 `MessageBus.dispatch` 登记发布事件，见[发布消息与通知](/tutorials/messaging-and-notification)。
`DocumentOperations.PUBLISH` 由具体应用定义，声明方式见[接入身份与权限](/tutorials/identity-and-permission)。

职责划分：

- Controller 绑定 HTTP 输入、声明认证与 permission，并把结果转换为 Request/View。
- App Service 负责跨模块事务、应用保护规则和副作用。
- App 不读取 IAM、Auth 或 IoT 等模块的 Mapper 与内部表。模块也不反向依赖 App 的资料表。

## 扩展方式

增加一项能力，通常是增加一个 Gradle 依赖，再提供该模块需要的应用 Bean。
模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动配置，并用 `@ConditionalOnMissingBean` 留出应用替换的位置。
使用方以 `List<T>` 接收全部 Bean，在启动时校验缺失与重复。
事件消费者、Job、回调和订阅授权等其他扩展点也以 Spring Bean 接入，因此新增能力与组装模块的方式相同。

| 要接入的内容 | 应用实现 | 声明方式 | 框架发现与校验 |
|---|---|---|---|
| 模块 | 增加 Gradle 依赖 | `implementation` 或 `runtimeOnly` | 自动配置随依赖生效 |
| Email 收件地址 | `EmailRecipientResolver` | Spring Bean | Email 渠道装配时注入，缺失则启动失败 |
| 业务 Operation | `OperationModule` | Spring Bean | `OperationCatalog` 收集，编码重复时抛出 `IllegalArgumentException` |
| 业务事件消费者、通知类型和渠道 | `EventHandler`、`NotificationType` 等 | Spring Bean | 见[发布消息与通知](/tutorials/messaging-and-notification) |
| 周期计划和后台 Job | `Job`、`JobSchedule` | Spring Bean | 见[定义并触发后台 Job](/tutorials/jobs-and-scheduling) |
| 订阅授权 | `WebRealtimeSubscriptionPolicy` | Spring Bean | 见[推送浏览器事件](/tutorials/realtime-events) |
| 外部回调 | `GatewayCallback` | Spring Bean | 见[部署 Gateway 与接入回调](/tutorials/gateway-and-callbacks) |
| 开放接口 | `@OpenApiRoute` | Controller 方法 | 见[开放 API](/tutorials/external-api) |
| 插件 | `@PluginService`、`@PluginExtension`、`PluginConfiguration` | 注解与 Spring Bean | 见[为 App 开放插件 API](/tutorials/app-plugins) |

接入一个模块的步骤：

1. 在 `build.gradle.kts` 中增加依赖。
2. 查看该模块要求应用提供的 Bean，例如 Email 渠道要求 `EmailRecipientResolver`。
3. 如果模块有数据表，且数据库尚未初始化，重新运行 `initFlyway`。已有数据库增加新的 Flyway 迁移。
4. 启动应用，确认没有缺失 Bean 或编码重复的异常。

## 验证

1. 运行 `./gradlew :apps:<应用名>-server:bootRun`，确认应用启动，没有缺失 Bean 或编码重复的异常。
2. 检查 Gateway 注册和登录。
3. 调用保留功能的主要接口，例如发布一份 Document。
4. 删减功能后，确认 Controller、Service、配置和页面入口都已一并移除，应用仍能启动。
5. 运行 `./gradlew :apps:<应用名>-server:test`，必要时运行 `integrationTest`。集成测试可能需要 Docker/Testcontainers。

## 失败结果与限制

| 条件 | 结果 |
|---|---|
| 缺少模块要求的应用 Bean | 启动失败 |
| 通知类型、Handler、回调等编码重复 | 启动抛出 `IllegalStateException` 或 `IllegalArgumentException` |
| 数据库已有表，却再次运行 `initFlyway` 生成 V1 | 不能用于更新已经运行的数据库，应新增迁移 |
| 删除依赖后表仍存在 | 表不会自动删除 |
| 切换到本地文件 backend 后默认 backend 标识仍指向 MinIO | 默认 backend 标识与已注册实现不一致，文件功能不可用 |
| 同一应用多个实例使用不同的 `spring.application.name` | Job、Quartz、Redis 和 RabbitMQ 的运行命名被分开，实例之间不互相协作 |

- 依赖方向是 `apps -> modules -> core`。模块不能知道应用的资料表和业务概念。
- App 不能直接使用模块 Mapper、Repository、内部包或表来绕过公开 API。
- 复制示例建立具体应用时，应保留这种依赖方向，再按业务删除或替换流程，而不是把示例 Controller 移进公共模块。

### 数据归属

主 PostgreSQL 同时保存模块表和 App 表。
Flyway 的初始迁移按模块 fragment 后 App fragment 的顺序生成，后续版本继续向前迁移。
IAM、Auth、Audit、Storage、Messaging、Notification、Job、Quartz 和 IoT 模块各自拥有自己的表。
App 自有数据由 App 的 `schemaFragment` 和后续迁移维护，例如 Workspace、Member、Document 与 Attachment 的业务表。

### 参考应用的组合要点

Sca4d Server 是组合这些模块的参考应用。
以下是它在模块之外补充的规则，其他应用可以选择不同的做法：

- 登录与敏感动作：Password、TOTP、Passkey 和 Email Code proof 由对应认证方法模块验证，Auth 保存 attempt 进度。敏感动作使用独立 verification，绑定当前 Session、Account 和动作编码，最终业务请求通过 `X-Verification` 消费一次。
- Session：Auth 使用 Redis 保存在线 Session 和失效代次。默认 `SESSION` 模式直接使用不透明 Session Token，`JWT` 模式另发短效 access Token。账号停用、Credential 替换或删除等流程终止该账号的全部 Session，已签发的离线 JWT 只能等待其短 TTL 到期。
- 并发控制：账号角色集合和角色 permission 集合使用强 ETag。读取返回 ETag，替换时要求 `If-Match`，版本已变化就停止写入。
- 删除账号：单独流程，只接受在线 Session Token，并要求已满足 `account.delete` verification。IAM 隔离 Account 后，App 在同一事务中处理关联数据，事务提交后再次终止目标 Account Session。保护校验或任一写入失败时，事务不留下部分数据。
- 文件与头像：文件模块只管理文件状态、backend 和短期传输授权，「谁能把哪个文件作为谁的头像」由 App 决定。流程是申请上传、上传字节、完成、绑定资料、生成短期读取地址。
- 审计：App 为写操作声明稳定审计操作码。审计 payload 只选择追溯所需的状态、标识和字段名，不保存密码、验证码、proof、文件原名或资料实际值。操作日志查询本身不写审计。
- 系统管理：系统字典聚合 Java 代码项、动态字典和允许扩展的数据库项。系统 Setting 只管理 Spring 容器已经注册的 Setting，不能创建任意键。准确字段与并发限制见[系统管理](/reference/http/sca4d-server)。
- Email 管理：使用 `system:email:read` 和 `system:email:manage` 管理 SMTP 配置与 Email 模板。SMTP 密码只在创建或明确替换时进入请求，响应只返回 `passwordConfigured`。
- IoT：Product 创建后默认停用，只有停用 Product 可以修改技术定义或发布完整物模型。Device 创建要求 `Idempotency-Key`，十分钟窗口内相同 key 与相同输入重放同一个结果，不同输入返回冲突。EMQX 回调只在 `sca4dj.iot.emqx.enabled=true` 时存在，使用独立 Webhook Secret。`sca4dj-iot-timescale` 只保存 `iot_telemetry_sample` 和 `iot_event`。
- Messaging：业务事务写入 Outbox，提交后向 RabbitMQ 发布。Outbox 派发和清理由无记录 `JobSchedule` 触发，不创建 Job Run。
- Job 与 Quartz：Job 模块在 `PERSISTED` 模式下把运行、节点、尝试次数、租约和结果保存在 PostgreSQL。Worker 失联后，其他实例可以重新领取过期节点，因此业务动作必须能够处理重复执行。Quartz 对账成功后 Scheduler 才开始领取 trigger。
- 浏览器通知：Web Realtime 向当前在线浏览器发送允许丢失的通知，不使用 Outbox，也不回放断线期间的消息。默认管理端在登录或恢复 Session 后连接 `/events` SSE，收到 `authentication.session.changed` 后重新读取 Session、permission、菜单和动态路由。默认管理端不连接 `/events/ws`。

## 相关页面

- [第一次运行](/tutorials/local-start)
- [开发后端功能](/tutorials/backend-feature)
- [接入身份与权限](/tutorials/identity-and-permission)
- [发布消息与通知](/tutorials/messaging-and-notification)
- [定义并触发后台 Job](/tutorials/jobs-and-scheduling)
- [推送浏览器事件](/tutorials/realtime-events)
- [部署 Gateway 与接入回调](/tutorials/gateway-and-callbacks)
- [开放 API](/tutorials/external-api)
- [为 App 开放插件 API](/tutorials/app-plugins)
- [App Gradle 插件](/reference/build-plugins#app-gradle-插件)
