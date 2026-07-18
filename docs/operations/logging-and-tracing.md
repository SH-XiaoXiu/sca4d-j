# 日志与 Trace ID

## Trace ID

HTTP 使用 `X-Trace-Id`。有效上游值被保留，否则服务端生成；响应 Header 和 `ApiResponse.traceId` 返回同一值。MDC、访问日志、审计和消息 envelope 传播它。

traceId 只用于关联，不得使用手机号、token 或其他敏感业务值。

## 访问日志

`CurrentRequestFilter` 在请求结束后记录请求信息。WARN/ERROR 阈值由 `SCA4DJ_REQUEST_LOG_SLOW_WARN_THRESHOLD` 与 `SCA4DJ_REQUEST_LOG_SLOW_ERROR_THRESHOLD` 控制；5xx 至少按 ERROR 记录。日志失败不得覆盖业务响应。

## 应用日志

应用日志记录启动、外部服务、重试和内部异常。结构化字段优先包含 traceId、资源 ID、messageId 和实例；禁止输出密码、Authorization、签名 URL、密钥和敏感 payload 全文。

应用日志的业务阶段、级别、字段顺序、模块和类名来源遵守[应用日志规范](/standards/facilities/logging)。线上查询和故障处理使用本章后续步骤。

## 操作日志

操作日志来自 `@Audit`，记录 actor、operation、target、结果和安全 Payload。它回答“谁做了什么”，不是每次 HTTP 请求，也不替代异常堆栈。

## 排障链路

1. 从客户端响应取得 traceId；
2. 查访问日志确认实例、路径、状态和耗时；
3. 查同 traceId 应用日志定位内部失败；
4. 对业务写操作查审计记录；
5. 对异步链查 messageId、Outbox 和消费者日志。

## 保留和访问

生产日志集中采集，按合规和容量设置保留期；访问权限最小化。日志脱敏规则和审计敏感字段清单随业务字段变化同步维护。
