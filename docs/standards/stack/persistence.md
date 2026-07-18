# MyBatis-Flex 与持久化规范

日常数据库访问按以下规则选择。Service、Repository、Mapper 的职责、状态更新、批处理和不合规情况见[持久化分层完整规范](./persistence-boundary)。

## 持久化抽象梯度

按复杂度选择：BaseMapper 单表能力 → 自定义 Mapper 方法 → XML 动态 SQL。不得为了“统一”把所有查询塞进 XML，也不得用 Wrapper 拼接难以审查的复杂联表。

## 简单查询

单表主键、明确等值条件和普通分页可使用 MyBatis-Flex。查询必须包含逻辑删除、租户/访问范围和必要排序。

## 复杂查询

联表、聚合、批量装配、动态访问范围或需稳定 SQL 形状时使用具名 Mapper 和 XML。SQL 字段显式列出，禁止业务查询长期依赖 `SELECT *`。

## 状态变更

Repository 调用语义化 Mapper 方法一次完成条件更新。禁止先查后无条件写，禁止 Mapper 通过传入 Entity 猜本次允许修改哪些列。

## 乐观写入

更新条件必须同时包含 ID、访问范围、当前版本和必要状态，并原子递增版本。以影响行数判断成功；冲突转换为统一并发异常。

## 聚合与事务

Repository 可以组合多个 Mapper 查询结果，事务由 Service 的公共方法开启。唯一性使用数据库唯一键；引用完整性由 Repository 在同一事务中校验目标，并由带访问范围、状态和版本的条件写入失败关闭。数据库不建立物理外键，也不能只依赖 Service 的无锁先查询。

## N+1 与批处理

列表关联数据必须批量查询并按 ID 聚合。批量写入限定批大小，明确部分失败语义。评审时计算固定 SQL 次数。

## Mapper 数量信号

一个业务操作调用大量 Mapper 时，必须使用联表或批量查询减少可以合并的 SQL。新增 Helper 不能用来隐藏重复查询。

## 命名

Mapper 放在 `com.sca4d.mapper`，XML 放在 `resources/mapper`。Mapper 方法名说明查询或更新内容；Repository 位于业务包，方法名使用业务动作。
