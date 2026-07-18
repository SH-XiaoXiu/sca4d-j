# 审计规则

## 声明

需要追溯的业务动作使用 `@Audit` 和已登记 operation。禁止在 Service 手工拼接 `sys_log`，禁止以普通应用日志替代审计。

## Actor 与 Target

actor 来自 CurrentRequest 的当前 Principal；target 使用已登记的资源类型和 ID。SYSTEM 动作标记来源，匿名动作不得伪造用户。

## Payload

复杂详情使用类型化 `AuditPayload` 和 Codec。密码、token、密钥、完整请求头和不必要个人信息禁止记录。展示失败不得破坏审计主记录。

## 执行模式

SYNC 写入失败会回滚业务事务；ASYNC 写入失败只记录错误日志。审计记录不可缺失的操作使用 SYNC，允许业务成功但日志稍后排查的操作使用 ASYNC。

## 失败审计

每个操作必须规定是否记录失败结果。失败记录只保存已经取得且可以公开的字段，不把内部异常堆栈写入管理端可见的 Payload。

## 目录与查询

审计 operation 由领域贡献并可在 Catalog 查询。操作日志服务统一分页、解析 Payload 和资源引用；业务模块不得建立平行日志表。
