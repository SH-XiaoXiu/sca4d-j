# Spring 装配类型

包：`com.sca4d.config`

## Web、序列化与持久化

| 类型 | 主要 Bean 或回调 | 配置结果 |
| --- | --- | --- |
| `JacksonConfig` | `sca4dJacksonCustomizer()` | 注册统一时间、枚举和 JSON 行为 |
| `BaseViewJacksonConfig` | `baseViewJacksonCustomizer()` | 为 `BaseView` 安装 SerializerModifier |
| `MybatisFlexConfig` | `sca4dMyBatisFlexCustomizer(...)` | 注册集群 ID KeyGenerator、逻辑删除列和 version 列 |
| `WebMvcConfig` | MVC Interceptor、CurrentRequest Filter、ClientState Filter | 注册请求信息和响应状态处理 |
| `OpenApiConfig` | `sca4dOpenApi()` | 配置 OpenAPI 标题、版本和认证声明 |
| `I18nConfig` | `localeResolver()`、`localizedMessages(...)` | 支持 `zh-CN` 和 `en-US` |
| `IdConfig` | `idGenerator(...)` | 创建 `ClusterIdGenerator` |

## 认证、权限与并发

| 类型 | 主要 Bean | 配置结果 |
| --- | --- | --- |
| `AuthConfig` | Authentication、PrincipalSelection、PermissionChecker、ResourceAccess、MVC Resolver 和 Interceptor | 注册 HTTP 认证与 Principal 选择 |
| `PermissionConfig` | `permissionCatalog(...)` | 聚合全部 `PermissionModule` |
| `ConcurrentConfig` | IdempotencyStore、锁、限流、SpEL 和相应 Aspect | 使用 Redis 与 Redisson 的多实例实现 |

## 消息、审计与实时通信

| 类型 | 主要 Bean | 配置结果 |
| --- | --- | --- |
| `MessagingConfig` | RabbitMessageSender、MessagePublisher、Topology、Outbox Service 和 Dispatcher | `sca4dj.messaging.enabled=true` 时装配 |
| `AuditConfig` | Payload Registry、Codec、Source、Recorder、Executor 和 Aspect | 由 `Sca4djAuditProperties` 控制 |
| `RealtimeConfig` | 握手、STOMP、Publisher、SSE Registry、Connection Service 和 Listener | 由实时通信配置控制 |
| `RealtimeWebSocketConfig` | STOMP endpoint、broker relay、inbound interceptor 和参数解析器 | WebSocket 启用时装配 |

## 文件、字典与系统功能

| 类型 | 主要 Bean | 配置结果 |
| --- | --- | --- |
| `FileConfig` | GrantSigner、Local/MinIO Storage、MinIO BootCheck、FileService 和签名服务 | 文件功能启用时装配 |
| `FileFallbackConfig` | `FileServiceUnavailable` | 文件功能关闭时装配 |
| `DictionaryConfig` | CodeDictionaryScanner 和 Catalog | 扫描代码字典枚举 |
| `ErrorCodeConfig` | ErrorCodeScanner 和 Catalog | 扫描 `BizErrorCode` 枚举 |
| `SystemConfig` | NavigationRouteCatalog、AuditPayloadViewRegistry | 聚合管理端路由和审计展示工厂 |

## 调度

| 类型 | 主要 Bean | 配置结果 |
| --- | --- | --- |
| `ScheduleConfig` | `scheduleTaskRegistry(...)` | 聚合全部 `ScheduleTask` |
| `SchedulerConfig` | `schedulerFactoryBeanCustomizer(...)` | 配置 Quartz JDBC JobStore 和集群参数，并绑定系统 Job 所需的能力开关 |

类型化配置位于 `com.sca4d.config.properties`。环境变量、默认值和限制见[配置参考](/reference/configuration.md)。

## Properties 类型

| 包 | 类型 | 配置内容 |
| --- | --- | --- |
| `com.sca4d.config.properties` | `Sca4djBootstrapProperties` | 初始平台管理员 |
| `com.sca4d.config.properties` | `Sca4djCacheProperties` | Redis 与本地缓存 |
| `com.sca4d.config.properties` | `Sca4djConcurrentProperties` | 幂等、锁和限流 |
| `com.sca4d.config.properties` | `Sca4djErrorCodeProperties` | 错误码扫描包 |
| `com.sca4d.config.properties` | `Sca4djFileProperties`、`Sca4djMinioProperties` | 文件生命周期、Grant 和对象存储 |
| `com.sca4d.config.properties` | `Sca4djIdProperties` | worker ID 与 data center ID |
| `com.sca4d.config.properties` | `Sca4djMessagingProperties` | RabbitMQ、Outbox 和重试 |
| `com.sca4d.config.properties` | `Sca4djRealtimeProperties` | SSE、WebSocket、STOMP 和帧限流 |
| `com.sca4d.config.properties` | `Sca4djRequestProperties`、`Sca4djRequestLogProperties` | CurrentRequest 和访问日志 |
| `com.sca4d.config.properties` | `Sca4djAuditProperties` | 审计模式、异步执行和脱敏 |
| `com.sca4d.config.properties` | `Sca4djSchedulerProperties`、`Sca4djIamCleanupProperties` | Quartz 集群和 Subject 清理 |
| `com.sca4d.gateway.config` | `Sca4djGatewayProperties` | upstream 文件、重载和健康检查间隔 |

项目不提供 `CommonConfig`。新增稳定配置域时，在归属明确的 `XxxConfig` 中启用对应 `Sca4djXxxProperties`；只为集中放置无关 Bean 而创建通用配置类，会破坏条件装配和配置归属。
