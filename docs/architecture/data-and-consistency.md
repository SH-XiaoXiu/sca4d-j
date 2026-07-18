# 数据、事务与一致性

## 数据库存储内容

IAM、通知、IM、操作日志和业务表使用 `principal_id` 引用执行主体。具体角色信息保存在角色、成员或业务表中。数据库枚举值通过 `BaseEnumTypeHandler` 与 Java 枚举转换。

## 事务位置

Service 的公共写方法使用 `@Transactional`。校验、数据库更新和 Outbox 写入在同一事务中完成。RabbitMQ 发送、对象存储删除等操作在事务提交后执行或由后台任务执行。

## 乐观锁

可编辑主数据和业务主单使用 `version`。Request 携带期望版本，Repository 更新条件包含当前版本并原子递增；影响行数为零时抛出并发冲突。HTTP 返回 409，并通过 ClientState 反馈刷新需要。

## 逻辑删除

使用逻辑删除的表包含 `deleted` 字段。MyBatis-Flex 默认值为 `0`，删除值为 `1`。这些表的查询和部分唯一索引需要包含 `deleted = 0`。

## 硬删除与仅追加

关系表和临时任务可以按业务代码直接删除。操作日志和已发送 Outbox 记录由运维归档或清理任务处理，普通业务接口不修改历史记录。

## 派生投影

未读数、使用次数和列表统计由明细表查询或聚合得到。异步保存统计值时，后台任务需要支持重算和失败重试。

## 集群 ID

`ClusterIdGenerator` 和 MyBatis-Flex key generator 在多个实例中生成不重复的 `long` ID。每个实例配置不同的 worker ID 和 data center ID。TypeScript 使用 `string` 保存这些 ID。

## 提交后副作用

`AFTER_COMMIT` 在数据库事务提交后调用 RabbitMQ。`OUTBOX` 把消息和业务记录写入同一事务，再由 `MessageOutboxDispatcher` 发送。事务回滚时，两种模式都不会发送对应消息。
