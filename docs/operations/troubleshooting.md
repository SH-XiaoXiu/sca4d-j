# 故障排查

排查先固定源码版本、Profile、配置来源、实例和 traceId，再根据真实失败层逐级检查。

## 启动失败

确认 Java 21、唯一环境 Profile、配置绑定错误、端口占用和 BootCheck 摘要。REQUIRED 依赖失败不要通过关闭健康检查掩盖；修复地址、凭据、网络或依赖状态。

## 数据库迁移失败

查看 Flyway 报错版本和 `flyway_schema_history`。空开发库优先用干净 schema 验证；共享/生产库禁止改历史脚本、随意删除记录或盲目 repair。先判断源码分支与数据库历史是否匹配。

## 登录失败

核对登录标识、Credential、Subject、Account、Principal 状态、Redis 连接和错误码。Bootstrap 只在不存在 PLATFORM Principal 时创建，留空凭据不会创建，也不会重新启用旧管理员。

## 权限异常

依次检查当前 Principal、SessionPermission、权限目录/角色绑定、`@RequirePermission`、Policy、租户 ACL 和 Repository SQL。RBAC 通过不代表 ACL 允许目标租户动作；缺少 ACL 规则必须失败关闭。

## 当前用户未刷新

检查实际 HTTP 响应的 `X-Client-State-Stale/Refresh/Version`，确认统一 ApiClient 读取 Header 并重新请求 `/me`。只检查响应 body 不能证明刷新协议。

## 乐观锁冲突

409 是预期并发结果。核对 Request version、Repository 条件更新和影响行数；前端应刷新并重新确认，禁止自动用旧输入覆盖。

## 消息未投递

按模式检查事务是否提交、Outbox 状态/attempt/next time、Rabbit publisher confirm、exchange/routing key、DLQ 和消费者幂等。AFTER_COMMIT 不能提供进程故障可靠性。

## Quartz 未启动

检查 `schedulerHealthIndicator`、`Quartz 启动失败` 日志、PostgreSQL 连接、`QRTZ_` 表以及启动锁等待时间。多个实例必须使用相同的 `SCA4DJ_SCHEDULER_NAME`。系统 Job 同一修订号出现不同指纹时，先确认各实例运行的代码和配置是否一致，不要直接修改 Quartz 表绕过检查。

## 实时事件未到达

确认客户端连接/订阅、STOMP 授权、target、Rabbit 广播、接入节点本地 registry 和 Origin。断线后重新拉取权威状态，不能靠补发猜测。

## 文件不可用

检查 File 状态、scene/resourceId/fileId、Policy、Grant 有效期、MinIO endpoint/public endpoint、bucket 和 BootCheck。业务表不得保存已过期 URL。

## 通知未显示

先查通知数据库与接收人，再查去重键、实时发布和前端刷新。实时事件丢失不应导致列表永久缺失。

## 网关无可用实例

检查 upstream 文件最后有效快照、enabled/draining、health path、连续健康阈值和主应用 readiness。配置文件写坏时应继续使用旧快照并记录错误。

## 管理端契约漂移

执行 `pnpm api:check`，比较当前后端 OpenAPI 与生成类型，核对 API base URL/context path。禁止在页面手写字段临时兼容。
