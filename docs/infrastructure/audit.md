# 审计与操作日志

审计模块保存业务操作、执行主体、目标、结果和经过筛选的详情，供管理端查询。它用于追溯业务操作，不替代访问日志和异常日志。

## 审计场景

登录、凭据变更、用户和租户状态、角色权限、数据范围、发布和删除等需要追溯的动作应审计。纯查询仅在合规要求明确时记录，避免把每个请求都变成操作日志。

## 审计注解

业务入口使用 `@Audit` 声明已登记 operation 和执行模式。`AuditAspect` 从 CurrentRequest 建立 actor、request 和 traceId，从调用结果或异常建立 outcome。

## Payload Codec

简单动作只记录基础 envelope；复杂业务定义类型化 `AuditPayload` 与 `AuditPayloadCodec`。Registry 按类型恢复，View Factory 生成安全展示。敏感字段在写入前过滤，长度受配置限制。

## 同步与异步模式

SYNC 审计与业务事务同成败，适合审计记录不可缺失的动作。ASYNC 使用有界队列和工作线程，审计写入失败不回滚业务；队列已满、写入失败和关闭超时会记录错误日志。

## 操作日志查询

`OperationLogService`/Repository 提供分页和详情，组合 operation 定义、actor/target 资源引用与 Payload View。解析单条旧 Payload 失败时保留主记录，不让整个列表失败。

目标资源名称保存在审计信封的 `target.label`。更新和删除在业务执行前采集名称，创建在业务结果产生目标 ID 后采集名称。名称解析遵守当前请求的资源读取权限；无权限时不保存名称。

操作日志读取资源引用时，`label` 只表示允许展示的业务名称，`navigationMessage` 只表示已删除、无权限或不支持详情等不可导航原因。资源删除后优先显示审计信封中的历史名称和删除状态。旧日志没有名称快照时只显示删除状态，不根据 ID 或其他字段猜测原名称。操作者删除后使用信封中已有的 Subject 名称快照。

## 日志与审计区别

| 日志 | 记录对象 | 主要用途 |
| --- | --- | --- |
| 访问日志 | 每个 HTTP 请求的方法、路径、状态和耗时 | 性能和请求诊断 |
| 应用日志 | 内部状态和异常 | 工程排障 |
| 操作日志 | 声明的业务动作 | 用户操作追溯 |

添加 `@Audit`、Payload 和查询测试的步骤见[并发与审计开发](/development/concurrency-and-audit.md)。必须遵守的记录规则见[审计规范](/standards/facilities/audit.md)。
