# 配置项

后端读取 `application.yml`、Profile 配置、`.env` properties 文件和环境变量。时间长度使用 ISO-8601 Duration，例如 `PT5S`、`PT10M` 和 `PT24H`。

## 应用与数据库

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` | 运行 Profile |
| `SCA4DJ_APPLICATION_NAME` | `sca4d-j` | Spring 应用名 |
| `SCA4DJ_SERVER_PORT` | `8080` | HTTP 端口 |
| `SERVER_SERVLET_CONTEXT_PATH` | 空 | Servlet context path；非空值以 `/` 开头 |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `NATIVE` | 代理头解析方式 |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | 本地和私有网段 | Tomcat 信任的代理地址正则 |
| `SCA4DJ_DB_NAME` | `sca4dj` | Compose 创建的数据库名 |
| `SCA4DJ_DB_URL` | 本机 `sca4dj` 数据库 | PostgreSQL JDBC URL |
| `SCA4DJ_DB_USERNAME` | `sca4dj` | 数据库用户名 |
| `SCA4DJ_DB_PASSWORD` | `sca4dj` | 数据库密码 |
| `SCA4DJ_POSTGRES_BIND` | `127.0.0.1:5432` | Compose 暴露地址和端口 |
| `SCA4DJ_DB_TIMEZONE` | `Asia/Shanghai` | Compose 中 PostgreSQL 时区 |
| `SCA4DJ_FLYWAY_ENABLED` | `true` | Flyway 开关 |
| `SCA4DJ_OPENAPI_ENABLED` | `true` | OpenAPI 与 Swagger UI |

## Quartz 集群调度

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_SCHEDULER_NAME` | `sca4d-scheduler` | 同一数据库中所有 Server 实例共用的 Scheduler 名称，长度 1–120 |
| `SCA4DJ_SCHEDULER_THREAD_COUNT` | `10` | 当前实例的 Quartz 工作线程数，范围 1–200 |
| `SCA4DJ_SCHEDULER_CLUSTER_CHECKIN_INTERVAL` | `PT15S` | 集群节点签到间隔，范围 `PT1S`–`PT5M` |
| `SCA4DJ_SCHEDULER_MISFIRE_THRESHOLD` | `PT1M` | misfire 判定阈值，范围 `PT1S`–`PT24H` |
| `SCA4DJ_SCHEDULER_SHUTDOWN_WAIT` | `PT30S` | 停机时等待当前 Job 的时间，范围 `PT1S`–`PT30M` |
| `SCA4DJ_SCHEDULER_BOOTSTRAP_LOCK_TIMEOUT` | `PT30S` | 等待 PostgreSQL 启动锁的总时长，范围 `PT1S`–`PT10M` |
| `SCA4DJ_SCHEDULER_BOOTSTRAP_LOCK_RETRY_INTERVAL` | `PT0.25S` | 启动锁重试间隔，范围 `PT0.025S`–`PT10S` |

## Redis 与 RabbitMQ

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_REDIS_HOST` | `localhost` | Redis 主机 |
| `SCA4DJ_REDIS_PORT` | `6379` | Redis 端口 |
| `SCA4DJ_REDIS_PASSWORD` | 空 | Redis 密码 |
| `SCA4DJ_REDIS_BIND` | `127.0.0.1:6379` | Compose 暴露地址和端口 |
| `SCA4DJ_RABBITMQ_HOST` | `localhost` | RabbitMQ 主机 |
| `SCA4DJ_RABBITMQ_PORT` | `5672` | AMQP 端口 |
| `SCA4DJ_RABBITMQ_USERNAME` | `sca4dj` | RabbitMQ 用户名 |
| `SCA4DJ_RABBITMQ_PASSWORD` | `sca4dj` | RabbitMQ 密码 |
| `SCA4DJ_RABBITMQ_VHOST` | `/` | virtual host |
| `SCA4DJ_RABBITMQ_AMQP_BIND` | `127.0.0.1:5672` | Compose AMQP 地址和端口 |
| `SCA4DJ_RABBITMQ_STOMP_BIND` | `127.0.0.1:61613` | Compose STOMP 地址和端口 |
| `SCA4DJ_RABBITMQ_MANAGEMENT_BIND` | `127.0.0.1:15672` | Compose 管理页面地址和端口 |

## 会话与初始化

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_SA_TOKEN_NAME` | `Authorization` | token 名称 |
| `SCA4DJ_SA_TOKEN_TIMEOUT` | `2592000` | 总有效期，秒 |
| `SCA4DJ_SA_TOKEN_ACTIVE_TIMEOUT` | `-1` | 活跃超时，秒 |
| `SCA4DJ_SA_TOKEN_LOG` | `false` | Sa-Token 日志 |
| `SCA4DJ_AUTHENTICATION_TTL` | `PT10M` | Flowable 认证实例从创建到过期的时间 |
| `SCA4DJ_AUTHENTICATION_BROWSER_COOKIE` | `sca4d_auth_browser` | 认证与浏览器绑定使用的 HttpOnly Cookie 名称；Cookie Path 为 `/`，供敏感操作复核浏览器 |
| `SCA4DJ_AUTHENTICATION_EMAIL_CODE_ENABLED` | `false` | 是否注册邮件验证码认证方式；关闭时 `/authentication-methods` 不返回 `EMAIL_CODE`，已验证邮箱也不能单独满足 Account 第二因素启用条件 |
| `SCA4DJ_AUTHENTICATION_EMAIL_CODE_KEY_PREFIX` | `sca4dj:authentication:email-code` | Challenge Redis key 前缀；共享 Redis 的不同部署必须不同 |
| `SCA4DJ_AUTHENTICATION_EMAIL_CODE_TTL` | `PT5M` | 单个验证码有效期 |
| `SCA4DJ_AUTHENTICATION_EMAIL_CODE_RESEND_INTERVAL` | `PT1M` | 同一次认证再次投递前的最短间隔 |
| `SCA4DJ_AUTHENTICATION_EMAIL_CODE_MAX_ATTEMPTS` | `5` | 单个验证码允许的错误提交次数，范围 1 至 20 |
| `SCA4DJ_AUTHENTICATION_EMAIL_CODE_SENDER_ID` | 空 | 投递认证邮件使用的启用 Sender 主键；启用邮件验证码时必填 |
| `SCA4DJ_AUTHENTICATION_EMAIL_CODE_TEMPLATE_FAMILY` | `authentication.email-code` | 投递认证邮件使用的启用模板 family |
| `SCA4DJ_AUTHENTICATION_TOTP_ENROLLMENT_KEY_PREFIX` | `sca4dj:authentication:totp-enrollment` | TOTP enrollment Redis key 前缀；共享 Redis 的不同部署必须不同 |
| `SCA4DJ_AUTHENTICATION_TOTP_ENROLLMENT_TTL` | `PT10M` | TOTP enrollment 从创建到激活的有效期 |
| `SCA4DJ_AUTHENTICATION_TOTP_ISSUER` | `SCA4D` | 身份验证器二维码显示的发行者名称，长度 1 至 64 个字符 |
| `SCA4DJ_AUTHENTICATION_PASSKEY_ENABLED` | `false` | 是否注册 Passkey AuthMethod、WebAuthn RP 和对应 REST 资源；关闭时 `/authentication-methods` 不返回 `PASSKEY` |
| `SCA4DJ_AUTHENTICATION_PASSKEY_REGISTRATION_KEY_PREFIX` | `sca4dj:authentication:passkey-registration` | registration ceremony Redis key 前缀；共享 Redis 的不同部署必须不同 |
| `SCA4DJ_AUTHENTICATION_PASSKEY_ASSERTION_KEY_PREFIX` | `sca4dj:authentication:passkey-assertion` | assertion ceremony Redis key 前缀；不得与 registration 前缀相同 |
| `SCA4DJ_AUTHENTICATION_PASSKEY_CEREMONY_TTL` | `PT5M` | registration 和 assertion 从创建到消费的有效期 |
| `SCA4DJ_AUTHENTICATION_PASSKEY_RP_ID` | 空 | WebAuthn RP ID；启用时必填，不包含协议、端口或路径 |
| `SCA4DJ_AUTHENTICATION_PASSKEY_RP_NAME` | `SCA4D` | 浏览器和认证器显示的 RP 名称，长度 1 至 64 个字符 |
| `SCA4DJ_AUTHENTICATION_PASSKEY_ORIGINS` | 空 | 允许提交 WebAuthn 响应的精确 Origin；多个值使用逗号分隔，生产环境只接受 HTTPS |
| `SCA4DJ_SECRET_KEYRING_ACTIVE_KEY_ID` | `local` | 新增 SMTP、Provider 与 TOTP 密文使用的 key-id |
| `SCA4DJ_SECRET_KEYRING_LOCAL_KEY` | 本地开发密钥 | `local` key-id 对应的 Base64 AES-256 密钥；生产环境必须替换 |
| `SCA4DJ_IDENTITY_PROVIDER_CONNECTION_TIMEOUT` | `PT5S` | OIDC、OAuth2 和 LDAP 建立连接的最长等待时间 |
| `SCA4DJ_IDENTITY_PROVIDER_READ_TIMEOUT` | `PT10S` | OIDC、OAuth2 和 LDAP 读取响应的最长等待时间 |
| `SCA4DJ_IDENTITY_PROVIDER_OIDC_AUTHORIZATION_KEY_PREFIX` | `sca4dj:identity:oidc-authorization` | OIDC 授权记录 Redis key 前缀；不同部署必须不同 |
| `SCA4DJ_IDENTITY_PROVIDER_OIDC_AUTHORIZATION_TTL` | `PT5M` | OIDC state、nonce 和 PKCE verifier 有效期 |
| `SCA4DJ_IDENTITY_PROVIDER_OIDC_REDIRECT_URI` | 空 | 在上游 OIDC Provider 注册的精确后端 callback URI |
| `SCA4DJ_IDENTITY_PROVIDER_OIDC_COMPLETION_URI` | 空 | callback 完成后返回管理端的精确 URI |
| `SCA4DJ_IDENTITY_PROVIDER_OAUTH2_AUTHORIZATION_KEY_PREFIX` | `sca4dj:identity:oauth2-authorization` | OAuth2 授权记录 Redis key 前缀；不得与 OIDC 前缀相同 |
| `SCA4DJ_IDENTITY_PROVIDER_OAUTH2_AUTHORIZATION_TTL` | `PT5M` | OAuth2 state 和 PKCE verifier 有效期 |
| `SCA4DJ_IDENTITY_PROVIDER_OAUTH2_REDIRECT_URI` | 空 | 在上游 OAuth2 Provider 注册的精确后端 callback URI |
| `SCA4DJ_IDENTITY_PROVIDER_OAUTH2_COMPLETION_URI` | 空 | callback 完成后返回管理端的精确 URI |
| `SCA4DJ_MAIL_CONNECTION_TIMEOUT` | `PT5S` | 建立 SMTP 连接的最长等待时间 |
| `SCA4DJ_MAIL_READ_TIMEOUT` | `PT10S` | SMTP 响应读取的最长等待时间 |
| `SCA4DJ_MAIL_WRITE_TIMEOUT` | `PT10S` | SMTP 内容写入的最长等待时间 |
| `SCA4DJ_MAILPIT_SMTP_BIND` | `127.0.0.1:1025` | Compose Mailpit SMTP 地址和端口 |
| `SCA4DJ_MAILPIT_UI_BIND` | `127.0.0.1:8025` | Compose Mailpit Web UI 地址和端口 |
| `SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_IDENTIFIER` | 空 | 首个管理员标识 |
| `SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_PASSWORD` | 空 | 首个管理员密码 |
| `SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_DISPLAY_NAME` | `平台管理员` | 首个管理员展示名 |
| `SCA4DJ_IAM_CLEANUP_INTERVAL` | `PT1M` | Subject 清理任务间隔 |

密钥轮换时先通过外部 YAML 或 `SPRING_APPLICATION_JSON` 在 `sca4dj.secret-keyring.keys` 增加新 key-id，再切换 `active-key-id`。仍有数据库密文引用的旧密钥必须保留；移除后对应 SMTP、Provider 或 TOTP 秘密将无法解密。

## 文件与 MinIO

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_FILE_ENABLED` | `true` | 文件服务开关 |
| `SCA4DJ_FILE_STORAGE` | `MINIO` | `LOCAL` 或 `MINIO` |
| `SCA4DJ_FILE_MAX_SIZE` | `20MB` | 单文件上限 |
| `SCA4DJ_FILE_UPLOAD_TTL` | `PT10M` | PENDING 上传有效期 |
| `SCA4DJ_FILE_READY_TTL` | `PT24H` | READY 文件有效期 |
| `SCA4DJ_FILE_ACCESS_TTL` | `PT5M` | 读取地址有效期 |
| `SCA4DJ_FILE_GRANT_TTL` | `PT24H` | 上传授权有效期 |
| `SCA4DJ_FILE_CLEANUP_INTERVAL` | `PT10M` | 清理间隔 |
| `SCA4DJ_FILE_CLEANUP_LEASE` | `PT30M` | 删除处理节点失联后的重领等待时间 |
| `SCA4DJ_FILE_CLEANUP_BATCH_SIZE` | `100` | 单批清理数量 |
| `SCA4DJ_FILE_GRANT_SECRET` | 本地开发密钥 | 文件授权签名密钥 |
| `SCA4DJ_FILE_LOCAL_ROOT` | `./file-data` | Local 存储目录 |
| `SCA4DJ_MINIO_ENDPOINT` | `http://localhost:9000` | 服务端 MinIO 地址 |
| `SCA4DJ_MINIO_PUBLIC_ENDPOINT` | 同 endpoint | 客户端可访问地址 |
| `SCA4DJ_MINIO_ACCESS_KEY` | `minioadmin` | Access Key |
| `SCA4DJ_MINIO_SECRET_KEY` | `minioadmin` | Secret Key |
| `SCA4DJ_MINIO_BUCKET` | `sca4dj` | Bucket |
| `SCA4DJ_MINIO_API_BIND` | `127.0.0.1:9000` | Compose API 地址和端口 |
| `SCA4DJ_MINIO_CONSOLE_BIND` | `127.0.0.1:9001` | Compose Console 地址和端口 |
| `SCA4DJ_MINIO_STARTUP_CHECK_ENABLED` | `true` | 是否执行 MinIO 启动检查 |
| `SCA4DJ_MINIO_STARTUP_CHECK_CRITICALITY` | `REQUIRED` | `REQUIRED`、`DEGRADED` 或 `OPTIONAL` |
| `SCA4DJ_MINIO_STARTUP_CHECK_TIMEOUT` | `PT3S` | 单次启动检查超时 |

生产环境必须替换文件授权密钥和 MinIO 默认凭据。`public-endpoint` 是浏览器或客户端实际访问的地址，不能填写只在容器网络内可解析的主机名。

## ID 与缓存

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_ID_WORKER_ID` | `1` | ID worker 标识 |
| `SCA4DJ_ID_DATA_CENTER_ID` | `1` | ID data center 标识 |
| `SCA4DJ_CACHE_KEY_PREFIX` | `sca4dj` | 缓存 key 前缀 |
| `SCA4DJ_CACHE_REDIS_TTL` | `PT30M` | Redis 缓存 TTL |
| `SCA4DJ_CACHE_LOCAL_TTL` | `PT5M` | 本地缓存 TTL |
| `SCA4DJ_CACHE_LOCAL_MAXIMUM_SIZE` | `1000` | 本地缓存最大条目数 |

## 分布式锁、限流与幂等

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_LOCK_KEY_PREFIX` | `lock` | 锁 key 前缀 |
| `SCA4DJ_LOCK_ENABLED` | `true` | 分布式锁开关 |
| `SCA4DJ_LOCK_WAIT_TIME` | `PT5S` | 获取锁等待时间 |
| `SCA4DJ_LOCK_LEASE_TIME` | `PT0S` | `0` 使用 watchdog |
| `SCA4DJ_RATE_LIMIT_KEY_PREFIX` | `rate-limit` | 限流 key 前缀 |
| `SCA4DJ_RATE_LIMIT_ENABLED` | `true` | 限流开关 |
| `SCA4DJ_RATE_LIMIT_CAPACITY` | `60` | 默认桶容量 |
| `SCA4DJ_RATE_LIMIT_REFILL_TOKENS` | `60` | 每窗口补充数量 |
| `SCA4DJ_RATE_LIMIT_TIME_WINDOW` | `PT1M` | 补充窗口 |
| `SCA4DJ_RATE_LIMIT_KEEP_AFTER_REFILL` | `PT5M` | 桶状态保留时间 |
| `SCA4DJ_IDEMPOTENCY_ENABLED` | `true` | 幂等开关 |
| `SCA4DJ_IDEMPOTENCY_KEY_PREFIX` | `idempotency` | 幂等 key 前缀 |
| `SCA4DJ_IDEMPOTENCY_PROCESSING_TTL` | `PT5M` | 处理中记录 TTL |
| `SCA4DJ_IDEMPOTENCY_SUCCESS_TTL` | `PT24H` | 成功响应 TTL |
| `SCA4DJ_IDEMPOTENCY_RETRY_AFTER` | `PT3S` | 建议重试间隔 |

## 消息

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_MESSAGING_ENABLED` | `true` | 消息服务开关 |
| `SCA4DJ_MESSAGING_SOURCE` | 应用名 | 消息来源 |
| `SCA4DJ_MESSAGING_RABBIT_CONFIRM_TIMEOUT` | `PT5S` | confirm 超时 |
| `SCA4DJ_MESSAGING_RABBIT_DELAYED_EXCHANGE_ENABLED` | `false` | delayed exchange |
| `SCA4DJ_MESSAGING_RABBIT_DECLARE_DEFAULT_TOPOLOGY` | `true` | 声明默认拓扑 |
| `SCA4DJ_MESSAGING_RABBIT_DEFAULT_EXCHANGE` | `sca4dj.event` | 默认 exchange |
| `SCA4DJ_MESSAGING_RABBIT_DEFAULT_QUEUE` | `sca4dj.event.default` | 默认 queue |
| `SCA4DJ_MESSAGING_RABBIT_DEFAULT_ROUTING_KEY` | `#` | 默认 binding key |
| `SCA4DJ_MESSAGING_RABBIT_DLX` | `sca4dj.event.dlx` | dead-letter exchange |
| `SCA4DJ_MESSAGING_RABBIT_DLQ` | `sca4dj.event.dlq` | dead-letter queue |
| `SCA4DJ_MESSAGING_RABBIT_DLK` | `dead` | dead-letter routing key |
| `SCA4DJ_MESSAGING_OUTBOX_ENABLED` | `true` | Outbox 投递开关 |
| `SCA4DJ_MESSAGING_OUTBOX_POLL_INTERVAL` | `PT3S` | 扫描间隔 |
| `SCA4DJ_MESSAGING_OUTBOX_BATCH_SIZE` | `50` | 单批领取数量 |
| `SCA4DJ_MESSAGING_OUTBOX_MAX_ATTEMPTS` | `10` | 最大投递次数 |
| `SCA4DJ_MESSAGING_OUTBOX_INITIAL_BACKOFF` | `PT5S` | 初始退避 |
| `SCA4DJ_MESSAGING_OUTBOX_MAX_BACKOFF` | `PT5M` | 最大退避 |
| `SCA4DJ_MESSAGING_OUTBOX_SENDING_TIMEOUT` | `PT5M` | SENDING 租约超时 |

## 审计与请求日志

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_AUDIT_ENABLED` | `true` | 审计开关 |
| `SCA4DJ_AUDIT_RECORD_SUCCESS` | `true` | 记录成功操作 |
| `SCA4DJ_AUDIT_RECORD_FAILURE` | `true` | 记录失败操作 |
| `SCA4DJ_AUDIT_MODE` | `ASYNC` | 全局执行模式：`ASYNC` 或 `SYNC` |
| `SCA4DJ_AUDIT_ASYNC_WORKER_COUNT` | `2` | 异步审计工作线程数 |
| `SCA4DJ_AUDIT_ASYNC_QUEUE_CAPACITY` | `1024` | 异步审计等待队列大小 |
| `SCA4DJ_AUDIT_ASYNC_SHUTDOWN_AWAIT` | `PT10S` | 关闭应用时等待审计任务的时间 |
| `SCA4DJ_AUDIT_MAX_PAYLOAD_LENGTH` | `64000` | Payload 字符上限 |
| `SCA4DJ_AUDIT_SENSITIVE_FIELDS` | 常见敏感字段 | JSON 脱敏字段 |
| `SCA4DJ_REQUEST_LOG_ENABLED` | `true` | 请求日志开关 |
| `SCA4DJ_REQUEST_LOG_SLOW_WARN_THRESHOLD` | `PT1S` | 慢请求 WARN 阈值 |
| `SCA4DJ_REQUEST_LOG_SLOW_ERROR_THRESHOLD` | `PT3S` | 慢请求 ERROR 阈值 |

## 请求上下文与错误码扫描

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_REQUEST_CONTEXT_ENABLED` | `true` | 安装 `CurrentRequestFilter` 与 MVC 身份参数解析器 |
| `SCA4DJ_REQUEST_CONTEXT_FILTER_ORDER` | `-100` | Filter 顺序，数值越小越早 |
| `SCA4DJ_ERROR_CODE_BASE_PACKAGES` | 应用根包 | 错误码枚举扫描包集合 |

关闭请求上下文后，身份注入、Principal、权限、审计来源和资源访问都失去统一请求态，通常只用于明确替换整套 Web 接入层的场景。错误码扫描包可以用逗号分隔；自定义业务包不在应用根包下时需要加入这里。

## 实时通信

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_REALTIME_ENABLED` | `true` | 实时通信总开关 |
| `SCA4DJ_REALTIME_WS_ENABLED` | `true` | WebSocket/STOMP |
| `SCA4DJ_REALTIME_WS_ENDPOINT` | `/realtime/ws` | 握手 endpoint |
| `SCA4DJ_REALTIME_WS_SOCKJS_ENABLED` | `true` | SockJS endpoint |
| `SCA4DJ_REALTIME_WS_ALLOWED_ORIGIN_PATTERNS` | `*` | 允许的 Origin pattern 列表 |
| `SCA4DJ_REALTIME_WS_APPLICATION_DESTINATION_PREFIXES` | `/app` | 应用消息前缀列表 |
| `SCA4DJ_REALTIME_WS_BROKER_DESTINATION_PREFIXES` | `/topic,/queue` | Broker 目标前缀列表 |
| `SCA4DJ_REALTIME_WS_USER_DESTINATION_PREFIX` | `/user` | 用户目标前缀 |
| `SCA4DJ_REALTIME_WS_USER_DESTINATION_BROADCAST` | `/topic/unresolved-user-destination` | 跨节点未解析用户广播目标 |
| `SCA4DJ_REALTIME_WS_USER_REGISTRY_BROADCAST` | `/topic/simp-user-registry` | 跨节点用户注册表广播目标 |
| `SCA4DJ_REALTIME_WS_BROKER_RELAY_ENABLED` | `true` | STOMP broker relay |
| `SCA4DJ_REALTIME_WS_BROKER_RELAY_HOST` | `localhost` | relay 主机 |
| `SCA4DJ_REALTIME_WS_BROKER_RELAY_PORT` | `61613` | STOMP 端口 |
| `SCA4DJ_REALTIME_WS_BROKER_RELAY_CLIENT_LOGIN` | RabbitMQ 用户名 | 客户端 relay 登录名 |
| `SCA4DJ_REALTIME_WS_BROKER_RELAY_CLIENT_PASSCODE` | RabbitMQ 密码 | 客户端 relay 密码 |
| `SCA4DJ_REALTIME_WS_BROKER_RELAY_SYSTEM_LOGIN` | RabbitMQ 用户名 | 系统 relay 登录名 |
| `SCA4DJ_REALTIME_WS_BROKER_RELAY_SYSTEM_PASSCODE` | RabbitMQ 密码 | 系统 relay 密码 |
| `SCA4DJ_REALTIME_WS_FRAME_RATE_LIMIT_ENABLED` | `true` | 帧级限流 |
| `SCA4DJ_REALTIME_WS_FRAME_RATE_LIMIT_WINDOW` | `PT10S` | 帧限流窗口 |
| `SCA4DJ_REALTIME_WS_CONNECT_CAPACITY` | `20` | CONNECT 容量 |
| `SCA4DJ_REALTIME_WS_SUBSCRIBE_CAPACITY` | `120` | SUBSCRIBE 容量 |
| `SCA4DJ_REALTIME_WS_SEND_CAPACITY` | `60` | SEND 容量 |
| `SCA4DJ_REALTIME_SSE_ENABLED` | `true` | SSE 开关 |
| `SCA4DJ_REALTIME_SSE_EXCHANGE` | `sca4dj.realtime.sse` | 跨节点 exchange |
| `SCA4DJ_REALTIME_SSE_TIMEOUT` | `PT30M` | 连接超时 |
| `SCA4DJ_REALTIME_SSE_HEARTBEAT_INTERVAL` | `PT25S` | 心跳间隔 |

broker relay 的 client/system login 与 passcode 默认复用 RabbitMQ 账号，也可以使用对应 `SCA4DJ_REALTIME_WS_BROKER_RELAY_*` 环境变量分别配置。

修改 endpoint 或 destination prefix 会改变客户端协议，服务端和管理端必须一起调整。生产环境不应保留 `allowed-origin-patterns=*`，应限制为实际站点来源。

## 网关

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4DJ_GATEWAY_APPLICATION_NAME` | `sca4d-gateway` | 应用名 |
| `SCA4DJ_GATEWAY_SERVER_PORT` | `8088` | HTTP 端口 |
| `SCA4DJ_GATEWAY_UPSTREAMS_FILE` | `config/gateway-upstreams.yml` | upstream 文件 |
| `SCA4DJ_GATEWAY_RELOAD_INTERVAL_MILLIS` | `5000` | 文件轮询间隔 |
| `SCA4DJ_GATEWAY_HEALTH_INTERVAL_MILLIS` | `5000` | 健康检查间隔 |

## 管理端和测试工具

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SCA4D_API_TARGET` | `http://localhost:8080` | Vite 开发代理的后端地址 |
| `SCA4D_OPENAPI_URL` | `http://localhost:8080/v3/api-docs` | OpenAPI 生成和检查地址 |
| `VITE_API_BASE_URL` | 空 | 生产管理端请求 API 的根地址 |
| `PLAYWRIGHT_EXECUTABLE_PATH` | 自动查找 | Playwright 使用的浏览器可执行文件 |
| `PLAYWRIGHT_SCREENSHOT` | `only-on-failure` | Playwright 截图模式 |
| `SCA4D_E2E_BASE_URL` | 空 | 已启动的管理端地址；留空时构建并启动 4173 预览服务 |
| `SCA4D_REAL_PASSKEY` | `false` | 是否运行连接真实后端的 Passkey 验收 |
| `SCA4D_REAL_API_URL` | `http://localhost:8091` | 真实 Passkey 验收使用的后端根地址 |
| `SCA4D_MAILPIT_URL` | `http://127.0.0.1:8025` | 真实邮件验证码验收使用的 Mailpit 根地址 |
