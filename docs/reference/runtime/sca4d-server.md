# Sca4d Server 运行配置

部署前先配好必需的 PostgreSQL、Redis、RabbitMQ 和对外地址，再按启用功能补充存储、认证与 IoT 配置。模块默认值、Sca4d Server 覆盖值和数据库 Setting 分别按本页说明设置。首次运行按[本地启动](/tutorials/local-start)操作；部署时按[构建、部署与排障](/tutorials/build-deploy-troubleshoot)准备环境。

| 配置目标 | 查阅位置 |
|---|---|
| 选择基础设施、启动本地容器 | [基础设施](#先判断需要哪些基础设施)、[本地 Compose](#本地-compose) |
| 修改浏览器和 API 地址 | [浏览器与 Gateway Origin](#浏览器与-gateway-origin) |
| 初始化数据库中的设置 | [Setting 初始化节点](#setting-初始化节点) |
| 登录 Token、密码与联邦登录 | [Session 与 JWT](#authproperties)、[密码认证](#passwordproperties)、[联邦登录](#具体应用联邦登录配置) |
| 文件上传与存储 | [Storage](#storage) |
| 消息、任务与调度 | [Messaging](#messagingproperties)、[Job](#jobproperties)、[Quartz](#quartzschedulingproperties) |
| 多实例浏览器推送 | [Web Realtime](#web-realtime)、[参数表](#webrealtimeproperties) |
| 设备接入与历史数据 | [IoT 与 EMQX](#iot-与-emqx)、[TimescaleDB](#可选-timescaledb) |
| API 转发、审计与日志采集 | [Gateway](#sca4d-gateway-运行规格)、[审计](#auditproperties)、[集中日志](#集中日志运行规格) |

环境变量通过 `apps/sca4d-server/src/main/resources/application.yml` 映射到 Spring 配置。文中的 `*Properties` 表列出模块默认值；Sca4d Server 的覆盖值另行说明。Setting 的环境变量还需遵守初始化规则，不能当作普通 Spring 配置使用。

## 先判断需要哪些基础设施

| 基础设施 | 何时需要 | 本 App 保存或处理什么 |
|---|---|---|
| PostgreSQL 17 | 始终需要 | App 与模块业务表、Audit、Outbox、Job、Quartz；Flyway 启动迁移 |
| Redis 7 | 始终需要 | Session、认证临时状态、失效代次、Bucket4j 限流、幂等状态、分布式锁、实时连接索引/票据、IoT HMAC 防重放 |
| RabbitMQ 4 | 始终需要 | Messaging 可靠业务消息；`RABBIT` 节点传输和 `BROKER_RELAY` STOMP 模式也使用它 |
| MinIO | 默认需要 | 默认文件 backend；改为本地 backend 且不读取历史 MinIO 文件时可关闭 |
| 本地目录 | 仅 `sca4dj.storage.local.enabled=true` | App 进程可访问的文件对象目录 |
| EMQX 5.8.9 | 仅 `sca4dj.iot.emqx.enabled=true` | MQTT CONNECT、Topic、上下行报文和当前连接快照 |
| TimescaleDB 2.29 / PostgreSQL 17 | 仅 `sca4dj.iot.timescale.enabled=true` | Telemetry 样本，以及物模型、ACK 和设备定时执行事件 |

PostgreSQL、Redis 和 RabbitMQ 的连接配置需在启动前准备好。MinIO、EMQX 和 TimescaleDB 按表中的启用条件配置。


## 名称、App 隔离与实例身份

配置名称时先区分应用、实例与系统身份：

| 要确定什么 | 使用什么 | 配置原则 |
|---|---|---|
| 哪些进程属于同一业务集群 | `spring.application.name` | 同集群相同，不同应用归一化后仍须不同 |
| 本次由哪个进程执行 | `sca4dj.instance.id` | 同时运行的实例各不相同 |
| 系统自主操作以哪个身份执行 | `sca4dj.iam.system-principal-ids` | 同一系统各实例共享稳定编码和 ID |
| 实例开放哪些入口、运行哪些工作线程 | `sca4dj.instance.capabilities` | 按部署职责选择，不能代替业务权限 |

Name 是不可变字符串值对象。Code 表达代码中声明的稳定编码，TreeCode 保存稳定节点链并提供层级操作；点、斜杠、冒号只属于输入或输出格式，同一节点链按相同领域类型比较时相等。Key 用稳定 Code 与动态值定位内部序列化资源；Redis key、MQ exchange、queue 和 routing key 使用 Key。动态文本保留大小写和字符区别，使用 `V_` 加 UTF-8 大写十六进制；动态数字使用十进制。固定节点中的保留标记和数字会转义，不能与动态值冲突。

`AppIsolation` 从启动时发布的全局 App 名获取隔离依据。Code 默认输出逻辑编码；Key 在序列化边界自动输出 App 前缀。逻辑业务资源类型、IAM 身份和能力编码不自动加前缀。Redis Cluster 同槽标记由模块声明，pipeline 与 Lua 使用模板实际安装的 KeyRedisSerializer，不自行拼接物理前缀。

`spring.application.name` 必须是 ASCII 字母、数字、下划线或连字符组成的固定名称；启动后转大写并将连字符替换为下划线，不能运行中改变。同一进程只发布一个 App 名。不存在独立 `key-prefix` 配置。

`sca4dj.instance.id` 是执行实例标识，默认启动时固定 UUID；`sca4dj.instance.capabilities` 未配置时启用全部声明，空集合启用零项，未知编码拒绝启动。能力影响 API、监听器和工作线程，不改变权限或行级数据范围。Gateway 根据完整路径要求筛选健康实例，没有候选返回 503。

`sca4dj.iam.system-subject-id` 保存整个业务系统共享的 SYSTEM Subject ID，默认 1；`sca4dj.iam.system-principal-ids` 保存 MAIN、IOT 等系统身份的人工 Principal ID，MAIN 默认 1，IOT 配置 2。数据库确认并提交后才能发布身份常量并启动后台任务；占用 ID 提示换 ID，历史绑定不同或状态异常要求人工迁移。所有实例共享同一 SYSTEM Subject ID 和领域 Principal ID，审计另外记录执行实例 ID。

`Sca4djMapper` 通过 `@Sca4djJson` 供脚手架持久化、Session 与内部消息使用；业务和 HTTP 使用自己的 `ObjectMapper`。`NameJacksonModule` 只注册名称值类型，不猜测 JSON 字符串是否需要隔离。

### 名称升级

升级前备份主库、Redis、MQ 与文件记录，停止旧实例，发送完 Outbox 并排空旧队列。V11 向前迁移 Principal 类型、Job 的 App 名和 Quartz scheduler 名称，归一化冲突立即失败；迁移失败回滚数据库事务。已持久化文件地址保持原值，旧对象不能删除。

IoT latest reported 是非过期数据，不能当缓存丢弃。在新旧实例均停止写入时，使用 `ReportedStateKeyMigration.verify(oldAppName)` 检查，再执行 `copy(oldAppName)`；它复制 Hash 与 Set，保留旧键，不覆盖不同内容，复制后可以再次 verify。恢复时使用备份及保留的旧键。

Session wire 使用版本 2，旧版本 1 的 Session 与 JWT 快照拒绝恢复，客户端必须重新登录。旧 Challenge、票据和短期缓存不双读；旧 key 在确认恢复窗口结束前保留。改变 App 名同样需要检查资源归属，不能依靠改配置自动迁移。

## 默认数据库与附加数据库

`spring.datasource.dynamic` 使用 dynamic-datasource Boot 3 starter 4.5.0。`primary: main` 指定主库，`strict: true` 禁止未知数据源退回主库；`datasource.main` 与 `datasource.iot-history` 分别保存主库和 IoT 历史库连接。默认 `DataSource` 是路由器，普通模块使用主库，历史入口通过 `@DS(IotTimescale.DATASOURCE)` 选择 `iot-history`。

公共 Repository 的集合 SQL 默认每批最多 1000 项和 30000 个绑定参数，分别由 `SCA4DJ_PERSISTENCE_BATCH_MAX_ITEMS` 与 `SCA4DJ_PERSISTENCE_BATCH_MAX_BIND_PARAMETERS` 调整。Redis pipeline 默认每批最多 1000 条命令，由 `SCA4DJ_REDIS_PIPELINE_MAX_COMMANDS` 调整。这些配置只改变内部批次，不截断公共查询结果。

TimescaleIotAutoConfiguration 使用 `@IotTimescale` 限定历史库的 Flyway、SqlSessionFactory、SqlSessionTemplate 和 Mapper。Flyway 直接使用物理 `iot-history` 数据源；MyBatis 使用共享路由器。历史入口先选择数据源，再通过 `@Transactional(propagation = REQUIRES_NEW)` 开启独立事务。已有事务的连接不能靠切换路由改变；历史库事务内不能把 IAM、Audit 或 Outbox 当作同库读写，不承诺跨库原子提交。

```mermaid
flowchart LR
    DefaultConfig[spring.datasource.dynamic.datasource.main] --> DefaultDataSource[默认 DataSource]
    DefaultDataSource --> DefaultFlyway[默认 Flyway]
    DefaultDataSource --> DefaultFactory[默认 SqlSessionFactory]
    DefaultFactory --> DefaultMappers[classpath mapper]

    HistoryConfig[sca4dj.iot.timescale] --> HistoryDataSource[@IotTimescale DataSource]
    HistoryDataSource --> HistoryFlyway[@IotTimescale Flyway]
    HistoryDataSource --> HistoryFactory[@IotTimescale SqlSessionFactory]
    HistoryFactory --> HistoryMappers[META-INF/sca4dj/mybatis/iot-history]
```

默认会话工厂加载标准 `classpath*:/mapper/**/*.xml`，IoT 历史会话工厂只加载 `classpath*:META-INF/sca4dj/mybatis/iot-history/*.xml`。两套会话工厂不能注册对方的 Mapper。IoT 历史库使用 [AdditionalMyBatisSessionFactoryBuilder](/javadoc/core/sca4dj-core-spring/com/sca4d/core/spring/persistence/mybatis/AdditionalMyBatisSessionFactoryBuilder.html) 继承公共 MyBatis 配置、插件和类型处理器，但 Mapper 位置与事务始终独立。

附加数据库配置不完整、迁移失败、Mapper XML 缺失或 Mapper 解析失败时，应用停止启动。默认数据库与 IoT 历史数据库没有跨库事务；TimescaleDB 写入失败不能回滚已经提交到主 PostgreSQL 的数据。

## 浏览器与 Gateway Origin

`.env/application.env` 定义两个通常需要一起使用的基础地址：

```properties
SCA4DJ_FRONTEND_ORIGIN=http://localhost:5666
SCA4DJ_GATEWAY_ORIGIN=http://localhost:8088
```

Passkey、Web Realtime 和 MinIO CORS 默认引用 `SCA4DJ_FRONTEND_ORIGIN`，Storage 文件传输地址与联邦登录回调默认引用 `SCA4DJ_GATEWAY_ORIGIN` 的 `/api` 入口。部署地址变化时通常只修改这两个值；只有某个入口确实使用不同地址时，才分别覆盖 `SCA4DJ_AUTH_PASSKEY_ORIGINS`、`SCA4DJ_WEB_REALTIME_ALLOWED_ORIGINS`、`SCA4DJ_MINIO_CORS_ALLOW_ORIGIN`、`SCA4DJ_STORAGE_TRANSFER_BASE_URL` 或 `SCA4DJ_AUTH_FEDERATION_CALLBACK_URI`。

Server 通过 `sca4dj.gateway.registration` 接入 Gateway。`SCA4DJ_GATEWAY_REGISTRATION_ENABLED` 控制是否注册，`SCA4DJ_GATEWAY_BASE_URL` 是控制 API 地址，`SCA4DJ_SERVER_BASE_URL` 是 Gateway 可以访问的当前实例地址，`SCA4DJ_INSTANCE_ID` 在同时运行的 Server 实例之间必须唯一，`SCA4DJ_GATEWAY_SECRET` 必须与 Gateway 完全相同。续租和请求超时分别由 `SCA4DJ_GATEWAY_RENEW_INTERVAL` 与 `SCA4DJ_GATEWAY_REQUEST_TIMEOUT` 配置。准确默认值和失败行为见 [GatewayRegistrationProperties](/javadoc/core/sca4dj-core-spring/com/sca4d/core/spring/gateway/GatewayRegistrationProperties.html)。

## Setting 初始化节点

Setting 默认读取数据库覆盖值或 Java 默认值，不把普通运行节点上的 ENV 直接当作覆盖值。只有设置 `SCA4DJ_BOOTSTRAP_ENABLED=true` 的初始化节点解释每个 SystemSetting 声明的 `<ENV>`、`<ENV>_INIT` 和 `<ENV>_SYNC`。

| 环境声明 | 启动结果 |
|---|---|
| `<ENV>_INIT=true` 和非空 `<ENV>` | 数据库尚无该 Setting 时写入一次；以后可由管理端修改或恢复默认值 |
| `<ENV>_SYNC=true` 和非空 `<ENV>` | 每次启动写入数据库并标记只读；管理端不能修改或恢复 |
| `<ENV>_SYNC=false` | 解除已有同步只读标记，不写新值 |
| 只有 `<ENV>`、INIT 与 SYNC 同时出现、标记不是 true/false、标记缺少值 | 初始化事务回滚，节点启动失败 |

所有 Setting 在同一初始化锁和数据库事务中处理。任一 ENV、Codec 或 SecretKeyring 失败都不留下部分更新。敏感 Setting 仍需有效 SecretKeyring，管理响应不返回明文。

## Messaging 与 RabbitMQ

RabbitMQ 连接默认值来自 `application.yml`：主机 `localhost`、端口 `5672`、账号与密码均为 `sca4dj`。App 固定启用 correlated publisher confirm 和 publisher returns；这些示例凭据只适合本地。

`sca4dj.messaging` 通过 `SCA4DJ_MESSAGING_*` 配置 confirm timeout、派发间隔、批量、租约、初始/最大退避、已发送保留期和清理批量。完整变量示例在 `.env/rabbitmq.env.example`。

业务事务把消息先写入主 PostgreSQL Outbox。派发失败会按退避持续重试，没有发布侧最大次数或 `DEAD` 状态；发布成功后由 RabbitMQ 持久保存。Consumer 默认不重试，失败进入独立 DLQ；声明 HandlingPolicy 后按该策略重试，耗尽或明确拒绝才进入 DLQ。死信由运维明确重发或删除。

## Job 与 Quartz

Job 和 Quartz 使用主 PostgreSQL DataSource。Flyway V7 建立 `job.*` 和 `quartz.qrtz_*`，`spring.quartz.jdbc.initialize-schema=never`，避免 Quartz 自己创建另一份表。Quartz 初始 `auto-startup=false`，代码计划完成对账后才开始获取 trigger。

`SCA4DJ_JOB_*` 配置 worker 数量、领取批量、轮询间隔、租约、续租间隔以及成功历史的保留时间、清理间隔和批量；`SCA4DJ_QUARTZ_*` 配置线程数与集群 check-in。SUCCEEDED run 默认保留 30 天，FAILED、QUEUED 和 RUNNING 不按该窗口自动删除。参数限制见 [JobProperties](#jobproperties) 与 [QuartzSchedulingProperties](#quartzschedulingproperties)。

Quartz scheduler name 通过 Key 输出为 `<APP_NAME>:SCHEDULING:QUARTZ`；Job run 将同一 App 隔离名称写入 `app_name`。同一 App 的多个实例必须使用相同 `SCA4DJ_APPLICATION_NAME`。App 名转大写并将连字符替换为下划线；共享中间件的 App 必须避免归一化后重名。修改已运行名称需要迁移已有资源，不自动接管其他名称的数据。

文件清理由 `JobSchedule` 触发，需要时创建可恢复的 Job Run。Outbox 派发与清理、Job 成功历史清理、IoT 到期扫描与数据清理、EMQX 连接对账和 TimescaleDB 保留期清理由各自模块声明无记录 `JobSchedule`，高频空扫描不写 Job Run。任务自己的数据库行使用状态、租约或 `version` 条件防止旧实例写回，需要跨事务串行的对象在事务前取得 Redis 分布式锁。Web Realtime 租约续约只维护当前 JVM 的连接，继续使用本机定时入口。

## Web Realtime

默认配置为：

```properties
SCA4DJ_FRONTEND_ORIGIN=http://localhost:5666
SCA4DJ_WEB_REALTIME_NODE_TRANSPORT=LOCAL
SCA4DJ_WEB_REALTIME_STOMP_MODE=NODE_ROUTED
SCA4DJ_WEB_REALTIME_ALLOWED_ORIGINS=${SCA4DJ_FRONTEND_ORIGIN}
```

`LOCAL` 只在单个 App 实例内投递。多实例部署把 node transport 改为 `RABBIT`，各实例声明 RabbitMQ 临时节点队列；这不改变 Messaging，可靠业务消息仍经 Outbox 和 RabbitMQ。STOMP 默认 `NODE_ROUTED`；改为 `BROKER_RELAY` 时还要为 RabbitMQ STOMP relay 配置主机、端口、client/system 登录、virtual host 和启动连接超时。

其余 `SCA4DJ_WEB_REALTIME_*` 变量控制连接租约、路由批量、节点消息 TTL、heartbeat、SSE timeout、连接队列、票据 TTL、STOMP CONNECT timeout、帧大小和发送限制。完整示例在 `.env/web-realtime.env.example`，默认值和限制见 [WebRealtimeProperties](#webrealtimeproperties)。

生产环境必须把 allowed origins 改为实际管理端完整 Origin。反向代理必须允许 `/events` 长连接；接入 WebSocket 时还要允许 `/events/ws` Upgrade，并从访问日志中删除票据查询参数。

默认 Vben 管理端会在登录或恢复 Session 后使用 backing Session Token 连接 `/events` SSE。收到 `authentication.session.changed` 后，它重新读取 Session、permission、菜单和动态路由，再使用最新 Token 建立 SSE。默认管理端不申请 WebSocket 票据，也不连接 `/events/ws`。

## Storage

### App 默认组合

`application.yml` 与 `.env/storage.env.example` 的默认组合是：

```properties
SCA4DJ_GATEWAY_ORIGIN=http://localhost:8088
SCA4DJ_STORAGE_BACKEND_DEFAULT=minio
SCA4DJ_STORAGE_LOCAL_ENABLED=false
SCA4DJ_STORAGE_MINIO_ENABLED=true
SCA4DJ_STORAGE_TRANSFER_BASE_URL=${SCA4DJ_GATEWAY_ORIGIN}/api
```

StorageRegistry 在启动时收集所有已启用 FileStorage。`SCA4DJ_STORAGE_BACKEND_DEFAULT` 必须精确等于其中一个唯一 backend ID；没有已启用实现、默认 ID 不存在或 backend ID 重复都会使装配失败。默认 backend 只决定新文件写到哪里；数据库中已有文件仍按自身 `backend_id` 查找，所以迁移期间必须继续启用仍有历史文件的 backend。

FileProperties 还配置清理间隔、删除租约和失败重试间隔。上传 URL TTL、读取 URL TTL、完成但未绑定文件保留期与全局文件大小上限是 Setting，环境变量分别为 `SCA4DJ_STORAGE_UPLOAD_TTL`、`SCA4DJ_STORAGE_ACCESS_TTL`、`SCA4DJ_STORAGE_READY_RETENTION` 和 `SCA4DJ_STORAGE_MAX_SIZE_BYTES`；需要按前述 Setting 初始化规则写入数据库，不能把它们误当成普通 Spring 属性覆盖。

### MinIO backend

MinIO 默认启用，backend ID 为 `minio`。App 内部端点和浏览器公开端点默认都是 `http://localhost:9000`，Region 为 `us-east-1`，Bucket 为 `sca4dj`，本地启动允许自动创建缺失 Bucket。内部端点由 App 请求对象存储，公开端点写入预签名 URL；两者都必须是 HTTP(S) Origin，不得带用户信息、业务路径、查询或 fragment。

`SCA4DJ_MINIO_ACCESS_KEY` 和 `SCA4DJ_MINIO_SECRET_KEY` 默认 `minioadmin`，只适合本地。浏览器直接请求预签名 URL 时，MinIO CORS 需要允许管理端 Origin；本地 `.env/storage.env.example` 的 `SCA4DJ_MINIO_CORS_ALLOW_ORIGIN` 默认引用 `SCA4DJ_FRONTEND_ORIGIN`。更新该变量后，必须使用传入该环境文件的 Compose 命令更新 MinIO 容器，浏览器的预检请求才会返回该 Origin。生产环境关闭 `SCA4DJ_STORAGE_MINIO_CREATE_BUCKET` 后，Bucket 必须在 App 启动前存在且凭据可访问；否则 MinIO backend 启动检查失败。

### 本地目录 backend

本地 backend 默认关闭。切换到本地目录的最小组合是：

```properties
SCA4DJ_STORAGE_BACKEND_DEFAULT=local
SCA4DJ_STORAGE_LOCAL_ENABLED=true
SCA4DJ_STORAGE_LOCAL_ROOT=docker-data/files
SCA4DJ_STORAGE_MINIO_ENABLED=false
SCA4DJ_STORAGE_TRANSFER_BASE_URL=https://api.example.com
```

backend ID 默认 `local`，container 默认 `files`。root 是 App 进程直接读写的路径，多实例若使用各自本地磁盘就不能互相读取对象；需要共享文件时应挂载同一可靠目录或使用 MinIO。

`transfer-base-url` 必须是浏览器可访问且包含协议和主机的绝对地址。FileService 在它下面签发 `/files/transfers/uploads` 和 `/files/transfers/objects` 短期 URL。两个路由虽然匿名且从 OpenAPI 隐藏，但仍必须验证绑定方法、对象、内容条件、运行命名空间和到期时间的 token；它们不是通用文件 API。反向代理日志应删除 `token` 查询参数。

## IoT 与 EMQX

基础 IoT 模块始终安装，管理 API 和主库 Device 状态可以在 EMQX 关闭时存在。设置 `SCA4DJ_IOT_EMQX_ENABLED=true` 后，App 才创建 MQTT runtime、健康检查，以及编码为 `IOT.EMQX.AUTHENTICATE` 的 Gateway 回调。

启用时必须提供：

- 内部 MQTT listener 的 Client ID：可以显式配置唯一的 `SCA4DJ_IOT_EMQX_CLIENT_ID`，也可以留空，由自动配置使用 Key 生成 `<APP_NAME>:IOT:EMQX:CLIENT:V_<UUID的UTF8十六进制>`；
- 内部 MQTT listener 主机、端口与 TLS，默认 `localhost:1884`、非 TLS；
- 内部 Runtime 固定使用 `sca4dj-iot-runtime` username 作为系统 Topic ACL 身份，不发送密码；
- shared subscription group；
- EMQX Management API 地址、Key 和 Secret；
- 非空 `SCA4DJ_IOT_EMQX_WEBHOOK_SECRET`。

Server 内部 Client ID 是进程连接 EMQX 的实例标识，不是设备的 MQTT Client ID。未显式配置时，每个进程启动生成一次新的 UUID，并在自动重连时继续使用同一个值；当前 Clean Start 和零 Session Expiry 不要求它跨进程重启保持不变。显式配置时，多个同时运行的实例必须分别使用不同值。

设备 listener 默认由 Compose 绑定 `127.0.0.1:1883`，使用 Compose 声明的 HTTP Authenticator；内部 listener 是 `1884`，供 Server MQTT runtime 使用，不配置设备认证。Webhook Secret 只校验 `/callbacks/IOT.EMQX.AUTHENTICATE`，不能复用设备 Secret、Gateway Secret 或 Management API Secret。

::: warning EMQX 回调安全边界

[GatewayCallbackDispatcher](/javadoc/core/sca4dj-core-spring/com/sca4d/core/spring/gateway/GatewayCallbackDispatcher.html) 声明 `@AnonymousAccess`，使 Auth 拦截器不要求用户 Session；这不表示回调无需认证。`EmqxAuthenticationCallback` 以固定时间比较 `X-Iot-Emqx-Secret` 和 `SCA4DJ_IOT_EMQX_WEBHOOK_SECRET`，错误或缺失返回 401。EMQX 只能持有这个专用共享 Secret，不能使用 Gateway Secret 或管理端用户的 Session Token。

:::

IoT 定时扫描间隔、ACK timeout 和连接 TTL 来自 `SCA4DJ_IOT_*`；HMAC 时间窗、nonce 和失败限制来自 `SCA4DJ_AUTH_HMAC_*`。`SCA4DJ_IOT_ACKNOWLEDGEMENT_TIMEOUT` 在创建即时、desired 或定时下行消息时写入该消息，单位由 Spring `Duration` 解析。配置必须至少为 1 毫秒；已经创建的消息继续按保存的等待时长计算 ACK 超时时间。

设备 HMAC 或 MQTT 认证由 Sca4d Server 组合通用 Auth 方法与 SessionLifecycle；Session 有效期使用 `SCA4DJ_AUTH_SESSION_TTL`。

`SCA4DJ_IOT_MAX_REPORTED_PROPERTIES`、`SCA4DJ_IOT_MAX_DESIRED_PROPERTIES` 和 `SCA4DJ_IOT_MAX_EVENTS_PER_MESSAGE` 分别限制单条 reported 覆盖属性、单次 desired 修改属性和单条上行离散事件数量。三项默认都是 100，必须位于 1 到 1000。EMQX 完整连接对账使用 watchdog 分布式锁，由 `SCA4DJ_IOT_EMQX_RECONCILIATION_INTERVAL` 控制触发间隔。

MQTT 业务上行默认使用 4 个 Topic 分片，每个分片最多等待 64 条消息；未明确拒绝的失败最多处理 3 次，相邻处理间隔 5 秒。分别通过 `SCA4DJ_IOT_EMQX_INBOUND_WORKER_COUNT`、`SCA4DJ_IOT_EMQX_INBOUND_QUEUE_CAPACITY`、`SCA4DJ_IOT_EMQX_INBOUND_MAXIMUM_ATTEMPTS` 和 `SCA4DJ_IOT_EMQX_INBOUND_RETRY_DELAY` 调整。MQTT Receive Maximum 等于单个分片的等待容量，为 ACK 后立即补发的热点 Topic 消息保留队列位置。

Sca4d Server 只在 `application.yml` 声明各模块的运行间隔。`sca4dj-iot`、`sca4dj-iot-emqx` 与 `sca4dj-iot-timescale` 分别声明无记录 DirectJob 和 JobSchedule；App 不调用模块内部 Worker、Repository 或清理服务。Quartz 使用同一个 App 隔离名称在集群中触发这些计划。

设备定时计划默认按 `sca4dj.iot.schedule-tick=PT0.2S` 触发，DeviceScheduleTask 每次最多处理 `SCA4DJ_IOT_DEVICE_SCHEDULE_BATCH_SIZE` 条，默认 1000。Product 完整目录扫描使用 `SCA4DJ_IOT_PRODUCT_CATALOG_SCAN_BATCH_SIZE`，默认 1000；终态下行清理使用 `SCA4DJ_IOT_DOWNLINK_CLEANUP_BATCH_SIZE`，默认 10000。三项范围都是 1 到 10000，只控制一次后台扫描或清理的工作量，不截断 Service 查询结果。设备定时任务普通查询一条到期候选，取得 `iot:schedule:<scheduleId>` Redis 分布式锁后再开启独立事务，并在事务中重新检查 version、状态和计划时间；不使用显式行锁，也不创建 Redis 到期索引或处理租约。`iot_device_schedule` 保存规则、版本、下一次触发时间和最近一次执行结果。

Device 创建幂等结果只允许在十分钟窗口内重放。App 自己声明该表的无记录 `JobSchedule`，默认每十分钟按 `expires_at` 索引清理，每批最多 10000 条，每轮最多执行 500 个有界删除事务；删除 SQL 不使用显式行锁，多个实例重复选择同一记录时由 `DELETE` 的实际结果自然竞争。App 只通过 `SCA4DJ_IOT_DEVICE_CREATION_CLEANUP_INTERVAL` 声明触发间隔，批量上限由任务实现固定。

DeviceScheduleTask 到期时不会通过 Connection 或 EMQX 直接发送。它在主库事务中调用 DeviceCommandService 重新校验当前物模型，创建带 `expiresAt` 的 PENDING 下行，并通过通用 Messaging Outbox 登记派发消息。DeviceDownlinkDispatchConsumer 随后通过 DeviceDownlinkRouter 选择 Transport 发布。低精度、长周期业务预约使用 JobSchedule，不进入设备定时规则。

IoT Realtime 使用共享 topic `iot.device.<deviceId>` 和 STOMP destination `/topic/iot.device.<deviceId>`。事件允许丢失，只通知浏览器哪些数据区域已经失效；客户端必须重新读取 HTTP API。Realtime 不发布完整 Device 状态或 Telemetry 样本。

`docker-compose.iot.yml` 通过 `EMQX_AUTHENTICATION` 直接声明唯一 HTTP Authenticator，不创建 Rule Engine、HTTP Connector 或 Action。Server 的内部 MQTT Client 订阅登记协议的业务上行 Topic，以及 `$SYS/brokers/+/clients/+/connected` 和 `disconnected`；`docker/emqx/acl.conf` 只向 `sca4dj-iot-runtime` 放行这两个系统 Topic。MqttMessageObserver 只能看到业务订阅实际收到的消息。`$SYS` 事件触发单 Client Management API 查询并实时刷新 Device 在线结果，完整快照只负责首次恢复和周期修复，不使用 HTTP 连接事件回调。

## 阿里云 SMS

`sca4dj.notification.sms.aliyun.credentials.<credentialRef>` 保存一个阿里云账号的部署凭据。每个引用包含 `access-key-id`、`access-key-secret`、`endpoint` 和 `region-id`；默认 Endpoint 是 `dysmsapi.aliyuncs.com`，默认 Region ID 是 `cn-hangzhou`。数据库 Provider 只保存同名 `credentialRef`，AccessKey 不进入 Settings、数据库、HTTP 响应或日志。

`sca4dj.auth.sms-code.account-realm` 选择 PHONE Identifier 所属 Account Realm，默认 `platform`。Challenge 有效期、重发间隔和错误次数上限分别由 SystemSetting `auth.sms-code.challenge-ttl`、`auth.sms-code.resend-interval` 和 `auth.sms-code.maximum-attempts` 管理。

## 可选 TimescaleDB

设置 `SCA4DJ_IOT_TIMESCALE_ENABLED=true` 后，历史模块创建前述 `@IotTimescale` Bean。连接配置位于 `spring.datasource.dynamic.datasource.iot-history`；`sca4dj.iot.timescale` 保存保留期、清理间隔和查询上限。Flyway 只读取 `classpath:META-INF/sca4dj/timescale/migration`，历史 MyBatis 工厂只读取 IoT 历史 Mapper。具体应用的其他历史表使用自己的迁移和 Mapper 资源。

最小配置如下，其他参数使用 `application.yml` 中的默认值：

```properties
SCA4DJ_IOT_TIMESCALE_ENABLED=true
SCA4DJ_IOT_TIMESCALE_URL=jdbc:postgresql://localhost:5433/sca4dj_iot
SCA4DJ_IOT_TIMESCALE_USERNAME=sca4dj
SCA4DJ_IOT_TIMESCALE_PASSWORD=sca4dj
SCA4DJ_IOT_TIMESCALE_MAX_QUERY_RESULTS=1000
```

默认清理间隔为 `PT1H`，Telemetry 与事件都保留 `P90D`；三个时长启用时都必须为正数。TimescaleDB 只保存 `iot_telemetry_sample` 和 `iot_event`。主 PostgreSQL 保存 Product、Device、reported/desired、可靠下行当前状态、最新 ACK 和设备定时规则；通用 Messaging Outbox 保存可靠事件、下行派发、ACK timeout 和断连消息。Telemetry 在主事务提交后直接发布，声明为 `RELIABLE` 的物模型事件和 IoT 状态机事件通过通用 Outbox 发布。Timescale Consumer 使用独立事务写入历史库，不能拿它代替主库业务状态。

## 本地 Compose

根 `docker-compose.yml` 提供 PostgreSQL 17、Redis 7.2.14、RabbitMQ 4 Management、MinIO 和 Mailpit。默认都只绑定环回地址：PostgreSQL `127.0.0.1:5432`、Redis `127.0.0.1:6379`、RabbitMQ AMQP/Management/STOMP 为 `5672`/`15672`/`61613`、MinIO API/Console 为 `9000`/`9001`、Mailpit SMTP/UI 为 `1025`/`8025`。主库、Redis、RabbitMQ 和 MinIO 数据写到 `docker-data/`；Mailpit 仅作为本地测试收件箱。

叠加 `docker-compose.iot.yml` 才启动 EMQX 与 TimescaleDB。必须保留以下宿主机目录材料：

- `docker-data/iot/emqx/bootstrap`：生成的 EMQX bootstrap API key 文件；
- `docker-data/iot/emqx/data`：EMQX 数据；
- `docker-data/iot/timescaledb`：TimescaleDB 数据。

`iot-emqx-api-key` 是一次性初始化容器。它生成 Management API bootstrap 文件，并把 `docker-data/iot/emqx/data` 根目录设置为 EMQX 镜像用户 `1000:1000` 可写；完成后以状态码 0 退出是正常结果。如果跳过该容器直接启动 Broker，宿主机首次创建的 `root:root` bind mount 会使 EMQX 无法创建 `/opt/emqx/data/configs`，Broker 会以状态码 1 退出。

Compose 只读挂载 `docker/emqx/base.hocon` 和 `docker/emqx/acl.conf`，并通过 `host.docker.internal:8088` 调用宿主机上的认证回调。这里使用 bind directory，不使用 Docker named volume。示例中的 `local-...-change-me` 密钥只能用于本机。

容器可连接不代表 App 已可用。最小启动验收还要确认主库 Flyway 成功、MinIO 默认 backend 检查成功、RabbitMQ 与 Redis 可用、Actuator health 正常且登录接口可访问；启用 EMQX 或 TimescaleDB 时，再检查对应 health、回调和独立 Flyway。

## Session 与 JWT {#authproperties}

源码：`modules/auth/sca4dj-auth/src/main/java/com/sca4d/auth/config/AuthProperties.java`

| 配置键 | 默认值 | 限制 |
|---|---|---|
| `sca4dj.auth.token.mode` | `SESSION` | `SESSION` 或 `JWT` |
| `sca4dj.auth.session-ttl` | `P30D` | 必须大于零 |
| `sca4dj.auth.excluded-paths` | 空列表 | 不进入 AuthRequestInterceptor 的 Spring MVC 路径模式；不能包含空白模式 |
| `sca4dj.auth.jwt.active-key-id` | 无公共默认值 | JWT 模式必须存在于 keys |
| `sca4dj.auth.jwt.keys` | 空 Map | key id 到 Base64 HS256 密钥；解码后至少 32 字节 |
| `sca4dj.auth.jwt.issuer` | 无公共默认值 | JWT 模式必须非空且验证时精确匹配 |
| `sca4dj.auth.jwt.audience` | 无公共默认值 | JWT 模式必须非空且验证时精确匹配 |
| `sca4dj.auth.jwt.access-ttl` | `PT5M` | 必须大于零 |
| `sca4dj.auth.jwt.max-token-length` | `6144` | 必须大于零，单位为字符 |

JWT 密钥独立于 `sca4dj.security.secret-keyring`。切换 active key 时，应保留仍可能验证未过期 JWT 的历史 key；超过最长 access TTL 后可以从 keys 删除旧 key。

Sca4d Server 默认把 `active-key-id` 设为 `primary`，`issuer` 和 `audience` 设为 `spring.application.name`。启用 JWT 时仍必须设置 `SCA4DJ_AUTH_JWT_PRIMARY_KEY`；模块本身没有这些应用默认值。

`max-token-length` 限制完整 JWS 字符数，不只限制 Session JSON。紧凑 Principal 和 Resource ID 使用 Roaring64 编码后仍可能因 ID 分布而超过上限；规模大或分布不可预测时使用 `SESSION` 模式，或先按真实数据测量再调整限制。提高限制时还要同时检查反向代理和应用服务器的请求头上限。

SESSION 模式不读取或校验 `active-key-id`、`keys`、`issuer` 和 `audience`。切换到 JWT 模式时，这四项必须由具体应用配置；脚手架不会用模块名、示例 App 名或其他固定值代替应用的签发者、受众和密钥。

`excluded-paths` 只跳过认证拦截器，不负责启用对应的 Controller 或第三方端点，也不提供权限检查。具体应用只能为当前运行环境确实允许匿名访问的路径配置排除模式；默认空列表使所有 Spring MVC Handler 继续执行认证检查。

## 密码认证 {#passwordproperties}

源码：`modules/auth/sca4dj-auth-password/src/main/java/com/sca4d/auth/password/config/PasswordProperties.java`

PasswordProperties 读取 `sca4dj.auth.password` 配置：

| 配置键 | 默认值 | 含义 |
|---|---|---|
| `account-realm` | `platform` | Password 登录标识和 bootstrap 用户名所在 Realm |
| `login-identifier-types` | `USERNAME` | 登录时交给 AccountDirectory 解析的标识类型列表 |
| `bootstrap.enabled` | `true` | 是否为 bootstrap Account 补充初始 Password Credential |
| `bootstrap.username` | `admin` | bootstrap Account 的用户名 |
| `bootstrap.password` | 无 | 只在初始化 Credential 时使用的原始密码 |

`login-identifier-types` 必须至少包含一个非空白值且不能重复。Password 模块只按这些稳定类型查询 AccountDirectory，不依赖负责登记标识的具体模块。

Sca4d Server 将 `login-identifier-types` 覆盖为 `USERNAME,EMAIL`，因此默认接受用户名或已绑定邮箱。通过 `SCA4DJ_AUTH_PASSWORD_LOGIN_IDENTIFIER_TYPES` 可以修改应用配置。

## 具体应用联邦登录配置

联邦登录需要配置服务端接收授权结果的回调地址，以及验证完成后跳转的前端地址。Provider 的协议参数由系统管理接口写入数据库，见[Provider 协议配置](#federatedproviderconfiguration)。

### 配置键

```yaml
app:
  authentication:
    federated-login:
      callback-uri: https://api.example.com/api/login/federated-authorizations/callback
      completion-uri: https://admin.example.com/#/login
```

| 配置键 | 默认值 | 作用 |
|---|---|---|
| `app.authentication.federated-login.callback-uri` | `http://localhost:8088/api/login/federated-authorizations/callback` | 必须与所有第三方 Provider 注册的 callback 完全一致 |
| `app.authentication.federated-login.completion-uri` | `http://localhost:5666/#/login` | callback 验证成功后，服务端 302 跳转的前端登录地址 |

`FederatedLoginConfiguration` 只装配 `FederatedLoginProperties`。应用启动类只启用它自己的应用配置，不把联邦配置类型塞进 `Sca4dApplication` 的 `@EnableConfigurationProperties`，避免启动类直接知道可选模块的配置。

### 安全限制

- 生产环境 callback 和 completion 应使用 HTTPS；callback 是协议配置的一部分，不能由浏览器请求参数覆盖。
- completion 地址只接受应用预先配置的 URI；服务端把一次性 completion id 作为查询参数追加，不追加 access token、refresh token 或第三方资料。
- 浏览器绑定摘要来自服务端 HttpSession，Redis 只保存摘要，不保存 Session ID。

## Provider 协议配置 {#federatedproviderconfiguration}

`FederatedProviderConfiguration` 保存一个第三方 Provider 的协议设置。它由 `FederatedProviderService` 校验，由 Repository 加密保存 Secret；HTTP 配置查询使用脱敏投影，协议交换才读取解密后的 Secret。

### 协议字段

| 字段 | OIDC | OAuth2 | 说明 |
|---|---|---|---|
| `issuer` | 必填 | 不使用 | 必须是 HTTPS URL；用于 Discovery 和 ID Token `iss` 校验 |
| `clientId` | 必填 | 必填 | 第三方注册的 Client ID |
| `clientSecret` | 条件必填 | 条件必填 | 只在保存或 Token 交换时短暂存在；不能返回 HTTP |
| `authorizationEndpoint` | Discovery | 必填 | 必须是 HTTPS URL |
| `tokenEndpoint` | Discovery | 必填 | 必须是 HTTPS URL |
| `userInfoEndpoint` | Discovery 可选 | 必填 | 必须是 HTTPS URL |
| `jwksEndpoint` | Discovery 必填 | 不使用 | OIDC 签名密钥地址 |
| `clientAuthentication` | `CLIENT_SECRET_POST`、`CLIENT_SECRET_BASIC` 或 `NONE` | 同左 | Secret 认证方式；默认是 `CLIENT_SECRET_POST` |
| `scopes` | 必须包含 `openid` | 由 Provider 要求 | 授权请求发送的作用域 |
| `profileMapping` | 不使用 | `subject` 必填 | 允许 `subject`、`displayName`、`email`、`emailVerified`、`avatar` 五个键，值必须是 `$` 开头的 JSONPath |
| `authorizationParameters` | OAuth2 白名单字段 | OAuth2 白名单字段 | 允许 `prompt`、`login_hint`、`ui_locales`、`max_age`、`acr_values`、`hd`、`access_type`、`audience`、`resource` |

`client_secret` 不与 Provider 表重复保存。配置表只保存 `provider_id`，Repository 在 Provider 仍存在且 Version 匹配的事务中替换配置；协议始终从 Provider 表读取，因此不会在两张表中分别保存协议。

### 配置示例

下面是 Google 的最小配置。`clientSecret` 只在写入请求中出现，读取接口不会返回它。

```json
{
  "issuer": "https://accounts.google.com",
  "clientId": "123456789.apps.googleusercontent.com",
  "clientSecret": "从环境变量或 Secret 管理服务读取的值",
  "clientAuthentication": "CLIENT_SECRET_POST",
  "scopes": ["openid", "profile", "email"],
  "profileMapping": {},
  "authorizationParameters": {}
}
```

OAuth2 Provider 示例：

```json
{
  "clientId": "github-client-id",
  "clientSecret": "仅写入请求时提交",
  "authorizationEndpoint": "https://github.com/login/oauth/authorize",
  "tokenEndpoint": "https://github.com/login/oauth/access_token",
  "userInfoEndpoint": "https://api.github.com/user",
  "clientAuthentication": "CLIENT_SECRET_POST",
  "scopes": ["read:user", "user:email"],
  "profileMapping": {
    "subject": "$.id",
    "displayName": "$.name",
    "email": "$.email",
    "emailVerified": "$.verified",
    "avatar": "$.avatar_url"
  },
  "authorizationParameters": {}
}
```

### 失败结果

- issuer、端点不是 HTTPS URL，返回 `2606`（配置无效）。
- OIDC 作用域缺少 `openid`，返回 `2606`。
- OAuth2 映射包含未知键、不是 JSONPath 或缺少 `subject`，返回 `2606`。
- 使用 `CLIENT_SECRET_BASIC` 或 `CLIENT_SECRET_POST` 却没有已保存 Secret 时，启用 Provider 返回 `2606`。
- 查询配置只返回 `clientSecretConfigured`，不会返回 Secret、密钥标识或密文。

## 消息派发参数 {#messagingproperties}

源码：`modules/messaging/sca4dj-messaging/src/main/java/com/sca4d/messaging/config/MessagingProperties.java`

`MessagingProperties` 绑定 `sca4dj.messaging`，配置 publisher confirm 与 Outbox 派发。

| 属性 | 默认值 |
|---|---:|
| `confirmTimeout` | `PT10S` |
| `dispatchInterval` | `PT1S` |
| `dispatchBatchSize` | `100` |
| `leaseDuration` | `PT1M` |
| `initialBackoff` | `PT1S` |
| `maximumBackoff` | `PT5M` |
| `sentRetention` | `P7D` |
| `cleanupInterval` | `PT1H` |
| `cleanupBatchSize` | `500` |

所有值必须为正数。`confirmTimeout`、`leaseDuration`、`initialBackoff`、`maximumBackoff` 和 `sentRetention` 按毫秒使用，不能短于 1 毫秒或超过 Java `long` 的毫秒表示范围。dispatchInterval 和 cleanupInterval 定义无记录 `JobSchedule` 的固定间隔；每次触发直接扫描 Outbox，不创建 Job Run。退避只用于 Outbox 发布失败，不是 Consumer 重试；派发没有最大尝试次数和 `DEAD` 状态。

## 任务执行参数 {#jobproperties}

源码：`modules/job/sca4dj-job/src/main/java/com/sca4d/job/config/JobProperties.java`

JobProperties 配置当前应用每个实例的 JobWorker 容量、租约和成功历史清理。前缀为 `sca4dj.job`。

| 配置键 | 默认值 | 限制与作用 |
|---|---:|---|
| `sca4dj.job.worker-count` | `4` | 大于零；每个实例同时执行业务节点的线程上限 |
| `sca4dj.job.claim-batch-size` | `10` | 大于零；每轮最多领取数，实际还受空闲 worker 数限制 |
| `sca4dj.job.poll-interval` | `PT0.5S` | 正数；无节点和领取完成后的固定轮询间隔 |
| `sca4dj.job.lease-duration` | `PT1M` | 正数；worker 失联后其他实例可接管的等待时间 |
| `sca4dj.job.lease-renew-interval` | `PT20S` | 正数且严格小于 leaseDuration |
| `sca4dj.job.succeeded-retention` | `P30D` | 正数；从 `completed_at` 起保留成功根运行 |
| `sca4dj.job.history-cleanup-interval` | `PT1H` | 正数；相邻两次成功历史清理的间隔 |
| `sca4dj.job.history-cleanup-batch-size` | `500` | 大于零；单次最多删除的成功根运行数 |

```yaml
sca4dj:
  job:
    worker-count: 4
    claim-batch-size: 10
    poll-interval: PT0.5S
    lease-duration: PT1M
    lease-renew-interval: PT20S
    succeeded-retention: P30D
    history-cleanup-interval: PT1H
    history-cleanup-batch-size: 500
```

workerCount 是单个实例的容量；三个使用相同 App 隔离名称的实例各配置 4 个 worker 时，理论上最多同时执行 12 个节点。claimBatchSize 大于 workerCount 不会让一个实例超出线程池容量。

leaseDuration 应覆盖正常的短暂停顿和数据库延迟，leaseRenewInterval 应留出多次续租机会。租约过短会使仍在执行的节点被其他实例重复领取；租约过长会延迟失联恢复。无论怎样配置，运行语义仍是至少一次，不能依靠租约参数获得仅一次外部操作。

历史清理只选择当前 App 隔离名称中、`completed_at` 早于 succeededRetention 的 SUCCEEDED 根运行。清理 SQL 明确删除这些运行的 attempt、依赖、节点和根记录。FAILED、QUEUED 和 RUNNING 不按该窗口自动删除；删除后的运行标识按不存在处理。

配置在 JobWorker Bean 创建前校验。数量、间隔、保留时间无效或续租间隔不小于租约时长时，应用启动失败。运行和表状态见[任务与调度](/tutorials/jobs-and-scheduling)。

## Quartz 集群参数 {#quartzschedulingproperties}

源码：`modules/scheduling/sca4dj-scheduling-quartz/src/main/java/com/sca4d/scheduling/quartz/QuartzSchedulingProperties.java`

QuartzSchedulingProperties 配置当前实例的 Quartz 工作线程和 JDBC 集群签到间隔。前缀为 `sca4dj.scheduling.quartz`。

| 配置键 | 默认值 | 限制与作用 |
|---|---:|---|
| `sca4dj.scheduling.quartz.thread-count` | `4` | 大于零；Quartz 获取并提交 trigger 的工作线程数 |
| `sca4dj.scheduling.quartz.cluster-checkin-interval` | `PT15S` | 正数；实例写入 JDBC JobStore scheduler state 的间隔 |

```yaml
sca4dj:
  scheduling:
    quartz:
      thread-count: 4
      cluster-checkin-interval: PT15S
```

没有 `scheduler-name` 配置项。scheduler 名称由 Key 使用全局 App 名隔离：

```text
<APP_NAME>:SCHEDULING:QUARTZ
```

具体应用必须配置 `spring.application.name`。同一应用的多个实例使用相同名称并共享 Quartz 集群；同一 PostgreSQL 中的不同应用使用不同名称。派生结果超过 `quartz.qrtz_*` 表 `sched_name` 的 120 字符限制时，自动配置在创建 Scheduler 前报告实际长度并使启动失败。

模块固定使用主 DataSource、PostgreSQLDelegate、LocalDataSourceJobStore、集群模式、`useProperties=true` 和 `quartz.qrtz_` 表前缀。Spring Boot 自动建表和自动启动保持关闭：具体 App 的 Flyway 负责建表，QuartzJobScheduler 完成代码计划对账后启动 Scheduler。

threadCount 控制 Quartz 触发提交的并发数，JobProperties.workerCount 控制业务节点执行的并发数。配置无效时应用启动失败。计划登记和触发见[任务与调度](/tutorials/jobs-and-scheduling)。

## 实时连接参数 {#webrealtimeproperties}

源码：`modules/web-realtime/sca4dj-web-realtime/src/main/java/com/sca4d/web/realtime/config/WebRealtimeProperties.java`

`WebRealtimeProperties` 绑定 `sca4dj.web-realtime`。

下表为模块默认值。Sca4d Server 将 `allowedOrigins` 覆盖为 `SCA4DJ_FRONTEND_ORIGIN`，应用默认地址是 `http://localhost:5666`。

| 属性 | 默认值 |
|---|---:|
| `nodeTransport` | `LOCAL` |
| `leaseRenewInterval` | `PT30S` |
| `leaseDuration` | `PT90S` |
| `routingBatchSize` | `500` |
| `nodeMessageTtl` | `PT30S` |
| `heartbeatInterval` | `PT25S` |
| `sseConnectionTimeout` | `PT30M` |
| `connectionQueueCapacity` | `64` |
| `ticketTtl` | `PT10S` |
| `stompConnectTimeout` | `PT10S` |
| `inboundFrameMaxBytes` | `65536` |
| `allowedOrigins` | `http://localhost:5173` |
| `stomp.mode` | `NODE_ROUTED` |
| `stomp.sendBufferMaxBytes` | `262144` |
| `stomp.sendTimeLimit` | `PT10S` |

`nodeTransport` 接受 `LOCAL` 或 `RABBIT`。`LOCAL` 只投递当前 JVM 的连接，不能用于多个 App 实例；`RABBIT` 直接投递本机连接，并通过 RabbitMQ 临时节点队列投递其他实例。时长和数量必须为正数，Origin 列表不能为空且不能包含空白项。`leaseDuration`、`heartbeatInterval`、`sseConnectionTimeout` 和 `ticketTtl` 按毫秒使用，不能短于 1 毫秒或超过 Java `long` 的毫秒表示范围。`nodeMessageTtl`、`stompConnectTimeout`、`stomp.sendTimeLimit` 和 `stomp.relay.startupConnectTimeout` 最终传给 Spring、RabbitMQ 或 Socket 的 int 毫秒参数，必须在 1 至 2147483647 毫秒之间。

`NODE_ROUTED` 使用 Spring simple broker；`BROKER_RELAY` 使用 `stomp.relay.host`、`port`、`clientLogin`、`clientPasscode`、`systemLogin`、`systemPasscode`、`virtualHost` 和 `startupConnectTimeout` 连接 RabbitMQ STOMP plugin。生产 App 必须配置实际完整 Origin，relay 凭据不能写入版本库。

## Sca4d Gateway 运行规格

源码：`gateway/sca4d-gateway`

`gateway:sca4d-gateway` 是独立 Spring Boot WebFlux 进程。它使用 Spring Cloud Gateway 和 Spring Cloud LoadBalancer 接收外部请求，保存 Server 租约，主动检查 readiness，并把请求转发给一个可用实例。它不托管前端文件，不读取应用数据库，也不执行回调业务。

### 路由

| 外部路径 | 转发行为 |
|---|---|
| `/api/events/ws` | 移除 `/api`，使用 WebSocket 负载均衡路由 |
| `/api/events` | 移除 `/api`，使用无响应超时的 SSE 路由 |
| `/api/**` | 移除 `/api`，转发普通 HTTP API |
| `/callbacks/{code}` | 保留路径，只选择声明该回调编码的实例 |
| `POST /internal/logging/push` | 启用集中日志时验证独立 Basic Auth，转发到固定 Loki push API |
| `/actuator/health` | Gateway 自身 Actuator，不转发 |

普通 API、SSE、WebSocket 和回调都只选择租约未过期且最近一次 readiness 检查返回 2xx 的实例。回调还要求实例注册资料包含对应回调编码。候选实例按实例 ID 稳定排序并轮询；一次请求只选择一次，不自动换实例重试。

Server 返回以 `/` 开头且不含 scheme/host 的 `Location` 时，普通 `/api/**` 响应会补上 `/api`。已经以 `/api/` 开头或使用绝对 URI 的 Location 保持不变。

日志写入路由不读取 Server 注册表，也不执行负载均衡。Gateway 验证写入凭据后删除 `Authorization` 和自己的 `X-Gateway-*` Header，把路径固定改为 `/loki/api/v1/push`。其他 Loki 路径不会注册到 Gateway；Grafana 通过中心端内部网络查询 Loki。

### 注册控制 API

所有控制请求必须在 `X-Gateway-Secret` 提交配置相同的 Secret；错误或缺失返回 401。

`POST /internal/gateway/registrations` 接受：

```json
{
  "instanceId": "server-1",
  "baseUrl": "http://server-1:8080",
  "readinessPath": "/actuator/health/readiness",
  "callbackCodes": ["IOT.EMQX.AUTHENTICATE"]
}
```

成功返回 201 和新租约 ID。实例 ID 必须匹配 `[A-Za-z0-9._-]{1,128}`；Server 基础地址必须是 HTTP(S) Origin，包含 host，path 只能为空或 `/`，不能包含 user info、query 或 fragment；readiness 路径必须以 `/` 开头且不包含 query、fragment 或控制字符；回调编码遵守 [GatewayCallbackCode](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/gateway/GatewayCallbackCode.html)。输入不合法返回 400。

`PUT /internal/gateway/registrations/{instanceId}/leases/{leaseId}` 续租，`DELETE` 同路径注销。当前租约续租或注销成功时返回 204，未知或已经被替换的租约返回 404。相同实例 ID 再次注册会立即替换旧租约，旧租约不能修改新注册。

### 配置

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| `SCA4DJ_GATEWAY_PORT` | `8088` | Gateway HTTP 端口 |
| `SCA4DJ_GATEWAY_APPLICATION_NAME` | `sca4d-gateway` | Spring application name |
| `SCA4DJ_GATEWAY_SECRET` | 无 | 必填控制和可信转发 Secret |
| `SCA4DJ_GATEWAY_LEASE_TTL` | `PT30S` | 未续租注册的保留时长 |
| `SCA4DJ_GATEWAY_HEALTH_INTERVAL` | `PT5S` | readiness 扫描间隔 |
| `SCA4DJ_GATEWAY_HEALTH_TIMEOUT` | `PT3S` | 单次 readiness 请求超时 |
| `SCA4DJ_GATEWAY_HEALTH_CONCURRENCY` | `8` | 同一轮 readiness 检查的最大并发请求数，必须为正整数 |
| `SCA4DJ_GATEWAY_CONNECT_TIMEOUT_MILLIS` | `3000` | 转发连接超时，单位毫秒 |
| `SCA4DJ_GATEWAY_RESPONSE_TIMEOUT` | `PT30S` | 普通 HTTP 转发响应超时 |
| `SCA4DJ_GATEWAY_LOGGING_ENABLED` | `false` | 是否注册 Loki 日志写入路由 |
| `SCA4DJ_GATEWAY_LOKI_ORIGIN` | 无 | Gateway 可以访问的 Loki HTTP(S) Origin |
| `SCA4DJ_LOKI_PUSH_USERNAME` | `alloy` | Alloy 写入日志使用的独立用户名 |
| `SCA4DJ_LOKI_PUSH_PASSWORD` | 无 | Alloy 写入日志使用的独立密码 |
| `SCA4DJ_LOG_ROOT` | `logs` | Gateway ECS JSON 文件的宿主机日志根目录 |
| `SCA4DJ_INSTANCE_ID` | `HOSTNAME` 或 `local` | 日志目录和 ECS service node 使用的稳定实例标识 |
| `SCA4DJ_ENVIRONMENT` | 活动 profile 或 `default` | ECS service environment |

Secret 为空，或租约与 readiness 检查时长不是正数时，Gateway 启动失败。日志写入启用后，Loki Origin、写入用户名和写入密码也必须完整有效；写入凭据不能复用注册 Secret。Server 的续租间隔必须小于租约 TTL，Gateway 按 UTC 时间判断租约是否过期。

readiness 请求按并发上限执行，每个实例独立超时。某个实例失败不会取消其他实例的检查；一轮结束后等待扫描间隔，再开始下一轮。结果只更新请求发起时的租约，已替换的租约不会被旧响应覆盖。

Gateway 默认启用 `local` profile。本地配置从仓库根目录 `.env/application.env` 和 `.env/logging.env` 读取注册 Secret、监听端口与日志写入参数；部署环境通过进程环境变量覆盖这些值，不依赖仓库内的本机配置文件。

Spring Cloud Discovery 和 LoadBalancer 实例缓存保持关闭。Gateway 每次选择实例时直接读取 `GatewayInstanceRegistry` 的租约和 readiness 状态，不从服务发现或 `ServiceInstanceListSupplier` 缓存读取实例。

### Header 和失败结果

Gateway 在路由前删除调用方提交的全部 `X-Gateway-*`、`Forwarded` 和 `X-Forwarded-*`，再设置自己的 `X-Gateway-Secret` 和 `X-Gateway-Client-Ip`。`/api/**` 另外设置 `X-Gateway-External-Prefix: /api`。Server 的采用规则见 [TrustedGatewayRequestFilter](/javadoc/core/sca4dj-core-spring/com/sca4d/core/spring/gateway/TrustedGatewayRequestFilter.html)。

| 结果 | HTTP 状态 |
|---|---:|
| 没有符合租约、readiness 和回调编码条件的实例 | 503 |
| Server 或 Loki 目标 TCP 连接失败 | 502 |
| 注册 Secret 错误或缺失 | 401 |
| Loki 写入凭据错误或缺失 | 401 |
| 注册输入不合法 | 400 |
| Gateway 没有对应公开路由 | 404 |

注册表只保存在当前 Gateway 进程内。多个 Gateway 副本不会复制租约；部署限制和不自动重试的原因见[负载均衡 Gateway](/tutorials/gateway-and-callbacks#失败与集群限制)。

## IotTimescale

源码：`modules/iot/sca4dj-iot-timescale/src/main/java/com/sca4d/iot/config/IotTimescale.java`

IotTimescale 是历史 Flyway、MyBatis 工厂和 Mapper 的 Spring Qualifier，不选择事务连接。

`IotTimescale.DATASOURCE` 是 dynamic-datasource 的稳定配置键 `iot-history`。历史入口通过 `@DS` 选择它，并用 Spring 独立事务控制提交与回滚。

名称的 App 隔离与数据源选择分别执行。Key 决定服务器资源的物理名称，`@DS` 决定新事务取得哪个数据库连接。

## 审计内容与脱敏 {#auditproperties}

源码：`modules/audit/sca4dj-audit/src/main/java/com/sca4d/audit/config/AuditProperties.java`

AuditProperties 读取 `sca4dj.audit` 下的默认记录方式和 payload 保存配置。

| 配置 | 默认值 | 含义 |
|---|---:|---|
| `max-payload-bytes` | `65536` | 单条 payload 允许保存的最大 UTF-8 字节数 |
| `mode` | `DIRECT` | 默认记录方式；注解可覆盖。`DIRECT` 等待数据库写入，`QUEUED` 等待独立事务 Outbox 交接后返回；配置不接受 `DEFAULT` |
| `sensitive-fields` | `password, token, secret, salt, authorization` | 不区分大小写、递归匹配的敏感字段名片段 |

`@Audit(mode = Audit.Mode.QUEUED)` 可以在默认同步的应用中要求可靠排队，`DIRECT` 可以覆盖类型或应用的队列默认。注解未指定方式时继承类型显式值，再使用应用配置。Messaging 可用时安装队列记录器、Codec 和消费者，不因默认方式为 DIRECT 而省略。默认方式不可用时启动失败；方法声明的方式不可用时在业务执行前失败。

队列模式在交接前完成相同脱敏和大小限制。消费者使用消息 ID 去重，最多尝试 5 次、间隔 5 秒，耗尽后由 Messaging 进入 DLQ。Outbox 派发与消费分别遵守 Messaging 配置；没有内存队列降级。调用结果与事务边界见[审计等待方式](/home/permission#审计等待方式)。

`max-payload-bytes` 必须为正数。编码并脱敏后的 JSON 超过限制时，模块记录 WARN 日志并只保存以下固定对象，不保存原 payload 内容：

```json
{"truncated":true,"reason":"payload too large"}
```

敏感字段值在写入前替换为 `***`；匹配作用于对象的每一层，数组中的对象也会处理。应用可以替换字段片段列表，但不能依靠该列表保存密码、Token、Authorization Header 或密钥等本来就不应进入 AuditPayload 的数据。

`sensitive-fields` 可以设为空列表，但列表项不能为 `null` 或空白文本。配置包含无效列表项时，属性绑定失败，应用不会在静默忽略脱敏规则后继续启动。

## 集中日志运行规格

集中日志由 `docker-compose.logging.yml` 的中心端和 `docker-compose.logging-agent.yml` 的主机端组成。中心端运行 Loki `3.7.4` 和 Grafana `13.1.0`；每台 Java 主机运行 Alloy `1.18.0`。Sca4d Gateway 提供唯一的跨主机日志写入入口，应用不使用网络日志 appender。

### Java 日志文件

Java 应用的活动文件路径为：

```text
<SCA4DJ_LOG_ROOT>/<spring.application.name>/<SCA4DJ_INSTANCE_ID>/application.json
```

`SCA4DJ_LOG_PATH` 非空时直接覆盖完整目录。默认 `SCA4DJ_LOG_ROOT=logs`，实例 ID 依次读取当前进程的 `SCA4DJ_INSTANCE_ID`、`HOSTNAME`，最终使用 `local`。同一主机上的并行实例必须为每个进程单独设置不同实例 ID，不能把同一个 `SCA4DJ_INSTANCE_ID` 写入这些进程共用的环境文件，也不能同时写同一个活动文件。

Sca4d Server 在生产环境把 DEBUG 及以上事件写入文件，在本地与开发环境写入 TRACE 及以上事件；Sca4d Gateway 写入 INFO 及以上事件。活动文件达到 100 MB 或日期变化时归档为 `application.<date>.<index>.json.gz`，保留 30 天，总上限为 10 GB。

每一行是一个 ECS JSON 对象。Spring Boot 自动写入时间、level、logger、线程、消息、异常和 `service.*`。MDC 与 SLF4J key/value 字段写在 JSON 根对象：

| 字段 | 来源 | 是否成为 Loki label |
|---|---|---|
| `callId` | Server HTTP 调用标识 | 否 |
| `subjectId` | 当前登录主体 | 否 |
| `accountId` | 当前账号 | 否 |
| `principalId` | 当前业务主体 | 否 |
| `sessionId` | 不写入 ECS JSON | 否 |
| `service.name` | `spring.application.name` | Alloy 从目录映射为 `service` label |
| `service.environment` | `SCA4DJ_ENVIRONMENT` 或活动 profile | Alloy 使用自己的 `environment` label |
| `service.node.name` | `SCA4DJ_INSTANCE_ID` 或 `HOSTNAME` | Alloy 从目录映射为 `instance` label |

### Alloy 采集

应用日志根目录在 Alloy 容器中的只读挂载点固定为 `/var/log/applications`。Alloy 匹配 `/var/log/applications/*/*/application.json`，从两级目录分别提取 `service` 和 `instance`，并保存读取位置到 `docker-data/logging-agent/alloy`。`environment`、`host`、`service` 和 `instance` 组成 Loki stream label；不会从 JSON 提取调用或用户字段作为 label。

Alloy 不读取 Docker Socket，也不采集基础设施容器标准输出。它使用 host 网络访问 Gateway；自身 HTTP 端点默认只监听宿主机 `127.0.0.1:12345`，端口冲突时通过 `SCA4DJ_ALLOY_HTTP_LISTEN_ADDR` 修改。

### Gateway 写入路由

`SCA4DJ_GATEWAY_LOGGING_ENABLED=true` 时，Gateway 只注册 `POST /internal/logging/push`。请求必须提交 `SCA4DJ_LOKI_PUSH_USERNAME` 和 `SCA4DJ_LOKI_PUSH_PASSWORD` 对应的 Basic Auth；缺失、重复、格式错误或不匹配时返回 401。认证成功后，Gateway 删除 `Authorization` 和自身添加的 `X-Gateway-*` Header，把路径改为 `/loki/api/v1/push`，并转发到 `SCA4DJ_GATEWAY_LOKI_ORIGIN`。

日志写入凭据不复用 `SCA4DJ_GATEWAY_SECRET`。Alloy 因此不能调用 Server 注册、续租和注销接口。Gateway 不为日志功能开放 Loki 查询、删除、管理或就绪检查 API。日志写入未启用时该路径返回 404；启用但 Loki Origin、用户名或密码不合法时 Gateway 启动失败；Loki 连接失败时返回 502。

Loki 自身没有 HTTP 身份认证层，`auth_enabled` 只控制多租户行为，不能替代身份认证。`SCA4DJ_LOKI_BIND` 默认是 `127.0.0.1:3100`，不能改成公网监听地址。跨公网部署时，`SCA4DJ_LOKI_PUSH_URL` 必须使用 Gateway 的 HTTPS 地址；Basic Auth 不能在明文 HTTP 上使用。

### 配置

| 环境变量 | 默认值 | 限制 |
|---|---|---|
| `SCA4DJ_ENVIRONMENT` | `local` | 同一部署环境使用稳定名称 |
| `SCA4DJ_LOG_HOST` | 无 | 每台 Alloy 主机必填且保持唯一稳定 |
| `SCA4DJ_LOG_ROOT` | `./logs` | Java 写入并由 Alloy 只读挂载的宿主机根目录 |
| `SCA4DJ_INSTANCE_ID` | `HOSTNAME` 或 `local` | 同一主机中的并行实例必须不同 |
| `SCA4DJ_ALLOY_HTTP_LISTEN_ADDR` | `127.0.0.1:12345` | Alloy 管理端点必须只绑定受信任地址 |
| `SCA4DJ_LOKI_PUSH_URL` | `http://127.0.0.1:8088/internal/logging/push` | 各地部署时改为中心 Gateway 的 HTTPS 或可信专网地址 |
| `SCA4DJ_LOKI_PUSH_USERNAME` | `alloy` | Gateway 与所有 Alloy 必须一致 |
| `SCA4DJ_LOKI_PUSH_PASSWORD` | 无 | Gateway 与所有 Alloy 必须一致，不能等于注册 Secret |
| `SCA4DJ_GATEWAY_LOGGING_ENABLED` | `false` | 中心 Gateway 启用日志写入时设为 `true` |
| `SCA4DJ_GATEWAY_LOKI_ORIGIN` | 无 | 中心 Gateway 可以访问的 Loki HTTP(S) Origin |
| `SCA4DJ_LOKI_BIND` | `127.0.0.1:3100` | 只供中心主机上的 Gateway 访问 |
| `SCA4DJ_GRAFANA_ADMIN_USERNAME` | `admin` | Grafana 初始管理员 |
| `SCA4DJ_GRAFANA_ADMIN_PASSWORD` | 无 | 启动中心端前必填 |
| `SCA4DJ_GRAFANA_BIND` | `127.0.0.1:3000` | Grafana 宿主机监听地址 |
| `SCA4DJ_LOKI_RETENTION` | `720h` | Loki 最短允许 24 小时 |
| `SCA4DJ_LOKI_STORAGE_MODE` | `filesystem` | 只允许 `filesystem` 或 `s3` |

`filesystem` 把索引、块和 Compactor 状态写入 `docker-data/logging/loki`。`s3` 还需要 `SCA4DJ_LOKI_S3_ENDPOINT`、`SCA4DJ_LOKI_S3_REGION`、`SCA4DJ_LOKI_S3_BUCKET`、`SCA4DJ_LOKI_S3_ACCESS_KEY` 和 `SCA4DJ_LOKI_S3_SECRET_KEY`；MinIO 使用 path-style 请求。同一 Docker Compose project 中，Loki 通过默认网络直接访问根 Compose 的 `minio:9000`；其他部署必须填写 Loki 实际可达的 MinIO/S3 地址。`SCA4DJ_LOKI_S3_INSECURE=true` 只表示 S3 endpoint 使用 HTTP，不会关闭 HTTPS 证书校验。MinIO/S3 凭据只保护对象存储，不能用于 Gateway 日志写入认证。

Grafana 通过 `logging-backend` Docker 内部网络直接连接 Loki。Grafana 页面由自己的登录认证保护，Loki 不发布查询端口。预置的 Loki 数据源可以在 Grafana Explore 中按环境、主机、服务和实例筛选。Java ECS 文件可以继续解析 JSON 字段：

```logql
{service="example-server", host="shanghai-1"} | json | callId="example-call-id"
```

中心端不可用不会使 Java 进程停止写文件。Alloy 写入失败在 Alloy 自身日志中可见；应用日志不记录或重试 Loki 请求。
