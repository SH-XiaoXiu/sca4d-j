# Sca4d Gateway 运行规格

源码：`gateway/sca4d-gateway`、`gateway/sca4dj-gateway-loki`

`gateway:sca4d-gateway` 是独立 Spring Boot WebFlux 进程。它使用 Spring Cloud Gateway 和 Spring Cloud LoadBalancer 接收外部请求，保存 Server 租约，主动检查 readiness，并把请求转发给一个可用实例。它不托管前端文件，不读取应用数据库，也不执行回调业务。一个 Gateway 进程只管理一个应用集群。`backend-service-id` 是该集群在 Spring Cloud LoadBalancer 中的内部标识，不是多应用 namespace。

## 路由

| 外部路径 | 转发行为 |
|---|---|
| `/api/events/ws` | 移除 `/api`，使用 WebSocket 负载均衡路由 |
| `/api/events` | 移除 `/api`，使用无响应超时的 SSE 路由 |
| `/api/**` | 移除 `/api`，转发普通 HTTP API |
| `/callbacks/{code}` | 保留路径，只选择声明该回调编码的实例 |
| `/actuator/health` | Gateway 自身 Actuator，不转发 |

这些路由定义位于 `spring.cloud.gateway.server.webflux.routes`。外部配置可以替换完整路由列表，内置 API 前缀由 `sca4dj.gateway.protocol.api-prefix` 配置。转发到动态注册 Server 的路由必须声明 `RegisteredServer` Filter。普通 HTTP(S) 代理路由不得使用该 Filter。

回调根路径由 `sca4dj.gateway.protocol.callback-path` 确定。外部另设入口时，先使用 `RewritePath` 恢复配置的内部回调路径，再执行 `RegisteredServer`。否则负载均衡器无法按回调编码（callback code）筛选实例。

内置 SSE 与 WebSocket 路由的应用内路径分别由 `sca4dj.gateway.routes.sse-path`、`websocket-path` 配置，默认 `/events` 和 `/events/ws`。修改后要与 Server 的有效 SSE 路由及 `sca4dj.web-realtime.protocol.websocket-endpoint` 一致。

普通 API、SSE、WebSocket 和回调都只选择租约未过期且最近一次 readiness 检查返回 2xx 的实例。回调还要求实例注册资料包含对应回调编码。候选实例按实例 ID 稳定排序并轮询。一次请求只选择一次，不自动换实例重试。

`RegisteredServer` 的可选参数是逐路由的公开路径前缀。配置该参数后，Filter 在转发前精确移除前缀，使用配置的 `external-prefix-header` 告知 Server，并在响应返回时为站内相对 `Location` 恢复该前缀。绝对 URI 和已经包含公开前缀的 `Location` 保持不变。

## 注册控制 API

所有控制请求默认在 `X-Gateway-Secret` 中提交与配置相同的 Secret。有效名称由 `sca4dj.gateway.protocol.secret-header` 确定，错误或缺失返回 401。

`POST /internal/gateway/registrations` 接受：

```json
{
  "instanceId": "server-1",
  "baseUrl": "http://server-1:8080",
  "readinessPath": "/actuator/health/readiness",
  "protocolFingerprint": "<Server 根据启动配置生成的 SHA-256>",
  "callbackCodes": ["iot.emqx.authenticate"]
}
```

成功返回 201 和新租约 ID。Gateway 在建立租约前比较 `protocolFingerprint`。与 Gateway 本机计算的协议指纹不一致或缺失时返回 409。

实例 ID 必须匹配 `[A-Za-z0-9._-]{1,128}`。Server 基础地址必须是 HTTP(S) Origin，包含 host，path 只能为空或 `/`，不能包含 user info、query 或 fragment。readiness 路径必须以 `/` 开头，且不包含 query、fragment 或控制字符。回调编码遵守 [GatewayCallbackCode](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/gateway/GatewayCallbackCode.html)。其他输入不合法时返回 400。

`PUT /internal/gateway/registrations/{instanceId}/leases/{leaseId}` 续租，`DELETE` 同路径注销。当前租约续租或注销成功时返回 204。未知或已经被替换的租约返回 404。相同实例 ID 再次注册会立即替换旧租约，旧租约不能修改新注册。

## 配置

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| `SCA4DJ_GATEWAY_PORT` | `8088` | Gateway HTTP 端口 |
| `SCA4DJ_GATEWAY_APPLICATION_NAME` | `sca4d-gateway` | Spring application name |
| `SCA4DJ_GATEWAY_SECRET` | 无 | 必填控制和可信转发 Secret |
| `SCA4DJ_GATEWAY_BACKEND_SERVICE_ID` | `sca4d-server` | 动态注册 Server 集群的内部 service ID |
| `SCA4DJ_GATEWAY_TRUSTED_PROXIES` | 空 | Spring Cloud Gateway 接受 Forwarded Header 的代理地址正则表达式 |
| `SCA4DJ_GATEWAY_LEASE_TTL` | `PT30S` | 未续租注册的保留时长 |
| `SCA4DJ_GATEWAY_HEALTH_INTERVAL` | `PT5S` | readiness 扫描间隔 |
| `SCA4DJ_GATEWAY_HEALTH_TIMEOUT` | `PT3S` | 单次 readiness 请求超时 |
| `SCA4DJ_GATEWAY_HEALTH_CONCURRENCY` | `8` | 同一轮 readiness 检查的最大并发请求数，必须为正整数 |
| `SCA4DJ_GATEWAY_CONNECT_TIMEOUT_MILLIS` | `3000` | 转发连接超时，单位毫秒 |
| `SCA4DJ_GATEWAY_RESPONSE_TIMEOUT` | `PT30S` | 普通 HTTP 转发响应超时 |
| `SCA4DJ_LOG_MAX_FILE_SIZE` | `100MB` | 单个 ECS JSON 日志文件的大小上限 |
| `SCA4DJ_LOG_MAX_HISTORY` | `30` | 日志归档保留天数 |
| `SCA4DJ_LOG_TOTAL_SIZE_CAP` | `10GB` | 日志归档总容量上限 |

Secret 为空、backend service ID 格式错误，或租约与 readiness 检查时长不是正数时，Gateway 启动失败。Server 的续租间隔必须小于租约 TTL。Gateway 使用显式的 UTC Clock Bean 判断租约时间。

readiness 请求按并发上限执行，每个实例独立超时。某个实例失败不会取消其他实例的检查。一轮检查结束后等待扫描间隔，再开始下一轮。结果只更新请求发起时的租约，已替换的租约不会被旧响应覆盖。

Gateway 和 Server 的 `sca4dj.gateway.protocol.*` 必须一致。准确键、默认值和校验条件见 [Sca4d Gateway 运行配置](/reference/runtime/sca4d-server)。Header 或路径名称修改后，需要同时重启两个进程并更新外部调用方。

Spring Cloud Discovery 和 LoadBalancer 实例缓存保持关闭。Gateway 每次选择实例时直接读取 GatewayInstanceRegistry 的租约和 readiness 状态，不从服务发现或 ServiceInstanceListSupplier 缓存读取实例。

默认不信任 Forwarded Header。Gateway 位于 Nginx 或其他反向代理之后时，配置 `SCA4DJ_GATEWAY_TRUSTED_PROXIES`，使 Reactor Netty 只采用受信代理写入的客户端地址。不要使用匹配任意地址的正则表达式。

## Header 和失败结果

Gateway 对全部路由删除调用方提交的配置控制前缀 Header。只有声明 `RegisteredServer` 的路由才会删除转发 Header，并写入配置指定的 Secret、客户端地址和可选外部前缀 Header。`RegisteredServer` 会验证路由目标是否等于配置的 backend service ID，避免把 Secret 发送到任意代理目标。Server 的采用规则见 [TrustedGatewayRequestFilter](/javadoc/core/sca4dj-core-spring/com/sca4d/core/spring/gateway/TrustedGatewayRequestFilter.html)。

| 结果 | HTTP 状态 |
|---|---:|
| 没有符合租约、readiness 和回调编码条件的实例 | 503 |
| 已选择实例但 TCP 连接失败 | 502 |
| 注册 Secret 错误或缺失 | 401 |
| `protocolFingerprint` 与 Gateway 不一致或缺失 | 409 |
| 注册输入不合法 | 400 |

注册表只保存在当前 Gateway 进程内。多个 Gateway 副本不会复制租约。部署限制和不自动重试的原因见 [负载均衡 Gateway](/capabilities/realtime-gateway/gateway#失败与集群限制)。

## 示例

本地启动 Gateway 并注册一个 Server 实例。以下示例对应工作区文档协作应用，Server 实例标识为 `workspace-server-1`。

```bash
export SCA4DJ_GATEWAY_SECRET='<由部署环境注入>'
export SCA4DJ_GATEWAY_BACKEND_SERVICE_ID=sca4d-server
./gradlew :gateway:sca4d-gateway:bootRun
```

Server 启动后自动注册。也可以用以下请求检查注册控制 API 的输入校验。`protocolFingerprint` 必须与 Server 根据启动配置生成的值一致，示例中的占位符需替换：

```bash
curl -i -X POST http://localhost:8088/internal/gateway/registrations \
  -H "Content-Type: application/json" \
  -H "X-Gateway-Secret: ${SCA4DJ_GATEWAY_SECRET}" \
  -d '{
        "instanceId": "workspace-server-1",
        "baseUrl": "http://workspace-server-1:8080",
        "readinessPath": "/actuator/health/readiness",
        "protocolFingerprint": "<Server 根据启动配置生成的 SHA-256>",
        "callbackCodes": ["partner.document.received"]
      }'
```

成功时响应状态为 `201`，响应中包含新租约 ID。租约 ID 用于后续 `PUT` 续租或 `DELETE` 注销。

## Loki addon

`gateway:sca4dj-gateway-loki` 是独立 Java library。`gateway:sca4d-gateway` 默认通过 `runtimeOnly` 携带 Loki addon 依赖，但 `sca4dj.gateway.logging.enabled=false` 时 addon 不注册属性 Bean 或路由。其他 Gateway 组装工程可以不声明该 addon 依赖。

addon 启用后注册一条可配置的 POST 路由。能力范围、凭据和失败结果见 [Loki 日志写入代理](/capabilities/operations-observability/loki-log-ingestion)。它验证 Alloy 的独立 Basic Auth，删除 `Authorization` 和 Gateway 控制前缀 Header，再把请求转发到 Loki。配置项如下：

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| `SCA4DJ_GATEWAY_LOGGING_ENABLED` | `false` | 是否启用 addon 路由 |
| `SCA4DJ_GATEWAY_LOKI_ORIGIN` | 无 | Gateway 可以访问的 Loki HTTP(S) Origin |
| `SCA4DJ_GATEWAY_LOGGING_PUSH_PATH` | `/internal/logging/push` | Alloy 调用的 Gateway 路径 |
| `SCA4DJ_GATEWAY_LOKI_PATH` | `/loki/api/v1/push` | Loki 接收 push 请求的路径 |
| `SCA4DJ_LOKI_PUSH_USERNAME` | `alloy` | Alloy 使用的 Basic Auth 用户名 |
| `SCA4DJ_LOKI_PUSH_PASSWORD` | 无 | Alloy 使用的独立密码 |

启用 addon 时，Origin、路径和凭据必须完整有效。Loki 写入密码不能复用 Gateway Secret。addon 不开放 Loki 查询、删除、管理或就绪检查 API。
## 扩展方式

Gateway 的路由和回调编码由 Spring 配置与 Bean 决定。外部部署需要替换路由时，覆盖 `spring.cloud.gateway.server.webflux.routes`。回调编码由 Server 中的 `GatewayCallback` Bean 声明，并通过注册资料上报给 Gateway。Loki addon 通过 `sca4dj-gateway-loki` 依赖和 `sca4dj.gateway.logging.enabled` 启用。
