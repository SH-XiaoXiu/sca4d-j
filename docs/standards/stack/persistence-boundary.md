# SQL 与持久化分层完整规范

## 目录

- [目标](#目标)
- [抽象梯度](#抽象梯度)
- [Service](#service)
- [Repository](#repository)
- [Mapper](#mapper)
- [方法数量信号](#方法数量信号)
- [状态变更](#状态变更)
- [查询](#查询)
- [写入与乐观锁](#写入与乐观锁)
- [聚合与事务](#聚合与事务)
- [N+1 与批处理](#n1-与批处理)
- [命名](#命名)
- [有效语义方法](#有效语义方法)
- [无效包装](#无效包装)
- [审查步骤](#审查步骤)
- [不合规情况](#不合规情况)

## 目标

Mapper、Repository 和 Service 必须形成清晰的抽象梯度。

三层之间的价值来自语义转换、职责收敛和细节隐藏，不来自类名、注解或调用层级本身。

增加 Repository 后，Service 应当不再理解 SQL 形状、Mapper 返回值、影响行数和持久化结果结构。增加 Mapper 后，Repository 应当不再手写数据库访问细节。

任何一层只把相同参数传给下一层并原样返回结果，都需要证明其独立价值。

## 抽象梯度

| 层级 | 使用的语言 | 负责内容 | 不负责内容 |
| --- | --- | --- | --- |
| Service | 业务动作和业务规则 | 状态机、权限、业务校验、事务边界、副作用、事件与通知 | SQL、影响行数、ResultMap、数据库字段组合 |
| Repository | 持久化语义和聚合存储 | 稳定读写能力、聚合加载、关系维护、乐观锁结果、Entity/Result 转换 | HTTP 请求、页面动作、业务权限、业务状态机 |
| Mapper | SQL 形状和数据库映射 | insert、select、count、update、delete、原子条件、行结果映射 | 业务动作、业务异常、跨流程编排、调用方语义 |

抽象层级从 Mapper 到 Repository 再到 Service 逐步升高。

每向上一层，调用方看到的数据库细节更少，看到的稳定语义更多。

## Service

Service 表达用户和业务能够理解的动作，例如：

- `enable(...)`
- `disable(...)`
- `approve(...)`
- `reject(...)`
- `cancel(...)`
- `complete(...)`

Service 负责：

- 判断当前业务状态是否允许执行动作；
- 校验操作者权限和业务输入；
- 选择目标状态和必要数据；
- 定义业务事务边界；
- 调用 Repository 完成持久化；
- 根据结果发布事件、发送通知或刷新缓存。

Service 不检查 Mapper 影响行数，不拼接 SQL 条件，不理解 ResultMap 和数据库字段命名。

Service 禁止直接依赖、注入或调用 Mapper。所有数据库访问必须经过 Repository，不因查询简单、历史代码、开发阶段或当前尚未创建 Repository 设置例外。

Service 中出现 Mapper 字段、Mapper import、Mapper 方法调用、数据库结果拼装或关系查询聚合时，直接判定分层失败。必须建立正确 Repository 边界并重新审查完整 Service、Repository、Mapper 和 SQL 对应关系。

## Repository

Repository 表达稳定的持久化能力，而不是页面按钮、Controller 路由或每个业务动作的同名副本。

Repository 负责：

- 隐藏 Mapper、XML、Wrapper、JOIN 和数据库结果结构；
- 使用 MyBatis-Flex `QueryWrapper` 组合简单单表查询、动态筛选、排序和分页，并在 Repository 内转换分页结果；
- 将 Entity 或查询 Result 转换为业务模型；
- 组合多个 Mapper 或多个 SQL 操作形成稳定聚合读写；
- 维护关系表的 replace、sync、cleanup 等存储语义；
- 接收并传递乐观锁版本和必要的当前状态条件；
- 检查受影响行数并转换为明确的持久化结果或并发冲突；
- 统一数据库时间、分页结果和存储层异常边界；
- 保证由数据库访问方式决定的稳定不变量。

Repository 不负责：

- 决定某个业务状态是否允许流转到另一个状态；
- 根据用户权限选择业务动作；
- 校验 HTTP Request；
- 为每个 Controller 或 Service 方法创建同名包装；
- 只为了绑定一个枚举常量创建方法；
- 通过字符串格式校验代替类型系统。

Repository 可以与 Mapper 使用相同动词，例如 `updateStatus`，前提是 Repository 增加了持久化层价值，例如乐观锁、影响行数处理、聚合状态或返回模型转换。

Repository 与 Mapper 同名、同参数、同返回值并直接转发时，Repository 没有形成新的抽象层级。

## Mapper

Mapper 表达最小、可组合、可验证的 SQL 能力。

默认 Mapper 继承 MyBatis-Flex `BaseMapper<Entity>`。框架已经能够类型安全表达的简单单表查询、动态筛选、排序和分页，不新增同形状的自定义 Mapper 方法或 XML；这些查询由 Repository 使用实体 getter 构造 `QueryWrapper`。只有复杂 JOIN、聚合、原子条件写入、锁语义、JSON/数据库特有表达式，或 Wrapper 无法清晰表达的 SQL，才增加自定义 Mapper 方法和 XML。

典型方法包括：

- `insert(...)`
- `selectById(...)`
- `selectPage(...)`，仅用于 Wrapper 不适合表达的复杂分页 SQL
- `countByStatus(...)`
- `updateStatus(...)`
- `updateVersionedFields(...)`
- `deleteBy...(...)`

Mapper 负责：

- SQL 条件和数据库字段；
- 原子写入和原子条件更新；
- 查询行、聚合行和影响行数；
- `FOR UPDATE`、`SKIP LOCKED`、版本条件和数据库锁语义；
- Entity 和 SQL Result 的映射。

Mapper 不负责：

- `enable`、`disable`、`approve`、`reject` 等业务动作；
- 抛出业务状态异常；
- 决定业务允许的状态流转；
- 发送消息、清理缓存和调用外部服务；
- 将多个 SQL 步骤包装成业务流程。

同一个 SQL 形状只保留一个 Mapper 方法。

## 方法数量信号

自定义 Mapper 方法数量大于或等于 Repository 公开持久化方法数量时，大概率存在以下问题之一：

- Repository 与 Mapper 一一对应；
- SQL 被按业务动作过度拆分；
- Service 语义下沉到了 Mapper；
- Repository 没有聚合查询、转换结果或压缩持久化能力；
- Repository 只是为了符合分层形式而存在。

该判断是高风险信号，不是数学定律。

以下情况可能使数量接近但设计仍然成立：

- Repository 只暴露少量稳定查询，而每个查询需要独立复杂 SQL；
- Mapper 需要分别提供 count 与 page rows，Repository 将其组合为一个分页能力；
- 一个 Repository 协调多个 Mapper，单个 Mapper 的方法数量不能单独代表 Repository 价值；
- BaseMapper 已经提供大量基础 SQL，未计入自定义方法数量。

审查时重点检查方法映射关系，而不是只比较数字。

如果大部分 Repository 方法都只调用一个同名 Mapper 方法，并原样返回结果，必须阻塞提交并重新设计持久化抽象。

## 状态变更

相同 SQL 更新形状不按业务动作拆分。

错误设计：

```java
interface AccountMapper {
    int updateEnabled(Long id);

    int updateDisabled(Long id);
}

class AccountRepository {
    void enable(Long id) {
        mapper.updateEnabled(id);
    }

    void disable(Long id) {
        mapper.updateDisabled(id);
    }
}
```

Mapper 的两个方法执行相同字段更新，只是绑定不同状态。Repository 的两个方法同样只是绑定常量，没有形成持久化语义。

推荐结构：

```java
interface AccountMapper {
    int updateStatus(
            Long id,
            AccountStatus expectedStatus,
            AccountStatus nextStatus,
            long expectedVersion,
            OffsetDateTime updatedAt);
}

class AccountRepository {
    void updateStatus(
            Long id,
            AccountStatus expectedStatus,
            AccountStatus nextStatus,
            long expectedVersion,
            OffsetDateTime updatedAt) {
        int affected = mapper.updateStatus(
                id,
                expectedStatus,
                nextStatus,
                expectedVersion,
                updatedAt);
        // 条件更新未命中时，统一转换为乐观锁或状态条件冲突，调用方无需理解影响行数。
        if (affected == 0) {
            throw new ConcurrencyConflictException(id, expectedVersion);
        }
    }
}

class AccountServiceImpl implements AccountService {
    void enable(Long id, long version) {
        // 业务层确认停用 Account 允许重新启用，并负责启用后的缓存和事件副作用。
        repository.updateStatus(id, AccountStatus.DISABLED, AccountStatus.ACTIVE, version, clock.now());
    }

    void disable(Long id, long version) {
        // 业务层确认当前 Account 允许停用，并在持久化成功后关闭相关业务入口。
        repository.updateStatus(id, AccountStatus.ACTIVE, AccountStatus.DISABLED, version, clock.now());
    }
}
```

允许状态流转的规则属于 Service 或领域模型。Repository 只执行调用方已经确定的类型化状态更新，并处理持久化结果。

状态使用枚举或稳定类型表达，不在 Repository 校验字符串格式。状态值无法通过类型系统限制时，优先修复类型设计。

## 查询

Mapper 返回数据库形状：

- Entity；
- 用于 JOIN、聚合和统计的 Result；
- 行数；
- 查询行集合。

Repository 返回调用方需要的持久化语义：

- `Optional<DomainModel>`；
- 必须存在的 DomainModel；
- `PageData<DomainModel>`；
- 聚合详情；
- 稳定统计结果。

Repository 可以将 Mapper 的 count 与 rows 组合为一个分页能力，可以将多个 Result 转换为一个业务模型，可以隐藏 JSON、枚举和字段映射细节。

Mapper 不直接返回 View。Repository 不返回 MyBatis Wrapper、游标实现、ResultMap 中间结构和数据库专用分页对象。

## 写入与乐观锁

Mapper 写方法返回影响行数或数据库生成结果，不抛出业务异常。

Repository 将数据库结果转换为持久化层可理解的结果：

- 影响一行表示写入成功；
- 影响零行根据已知条件转换为不存在、状态条件不匹配或乐观锁冲突；
- 唯一约束和数据完整性问题转换为统一的持久化冲突；
- 生成 ID、更新时间和版本回填到稳定模型。

Service 决定冲突在业务流程中的含义和后续副作用。

乐观锁的版本条件必须进入原子 SQL。Repository 负责要求调用方提供 expectedVersion，并统一处理更新失败结果。

Repository 不通过先查询再无条件更新模拟乐观锁。

## 聚合与事务

Service 通常定义业务事务边界，因为只有 Service 理解完整业务动作和副作用。

Repository 可以组合多个 Mapper 操作，但组合必须代表稳定的聚合存储能力，例如：

- `replaceMembers(...)`
- `syncPermissions(...)`
- `appendMessage(...)`
- `saveAggregate(...)`

这些方法可以包含多次 SQL，但不包含业务权限、通知和外部服务调用。

Repository 方法使用 `@Transactional` 时，事务只保证该稳定存储操作的原子性。跨 Repository、消息、缓存和业务副作用的事务仍由 Service 统一编排。

## N+1 与批处理

所有列表、分页、树、批量任务和循环处理必须检查 N+1，不限于 ORM 关联查询。以下模式都属于必须解释和优化的候选：

- 查询一批主记录后，循环按主键查询关联记录；
- 循环执行可以由集合条件、批量插入、批量更新或聚合 SQL 一次完成的数据库操作；
- 每项先查询再无条件更新，而数据库原子条件可以直接表达竞争和状态迁移；
- 为每条记录重复加载相同字典、配置、权限、租户或主体数据；
- 批量业务入口内部逐项调用远程服务、对象存储或消息系统，却没有说明对方是否支持批量协议以及失败隔离要求。

审查时必须给出可验证的调用数量，例如“固定 2 条 SQL”“1 次分页查询加每项 2 次条件写入”“每个对象 1 次外部删除”。只写“数据量不大”“已经分页”或“后台执行”不能证明不存在 N+1。

能够使用 `IN`、JOIN、聚合、批量 DML、原子 `UPDATE`、`RETURNING`、批量 API 或预加载映射稳定消除的逐项调用必须消除。确因单项失败隔离、外部协议限制、逐项乐观锁、流式资源所有权或状态机终态记录而保留逐项调用时，必须：

- 设置明确且可配置的批次上限；
- 保持每项幂等，并说明失败、重试和部分成功语义；
- 不用线程池、异步或提高并发掩盖调用数量；
- 提供覆盖调用次数和批次边界的测试或数据库执行证据。

异步只改变执行线程，不消除 N+1；并行只改变耗时分布，还可能放大数据库、连接池和外部服务压力。不得把异步或并行化作为 N+1 修复结论。

## 命名

Mapper 方法使用 SQL 动词和查询形状：

- `insert...`
- `select...`
- `count...`
- `update...`
- `delete...`
- `claim...`，仅在它准确表达原子 SQL 抢占语义时

Repository 方法使用稳定持久化语义：

- `find...`
- `require...`
- `page...`
- `save...`
- `updateStatus...`
- `replace...`
- `sync...`
- `append...`
- `claimFor...`

Service 方法使用业务动作：

- `enable...`
- `disable...`
- `approve...`
- `reject...`
- `cancel...`
- `complete...`

名称不是硬编码词表。判断标准是调用者看到的方法是否处于该层应有的抽象高度。

## 有效语义方法

Repository 可以提供比 Mapper 更具体的方法，前提是方法封装了真实且稳定的持久化语义。

例如 `claimForDispatch(...)` 可以成立，因为它可能统一：

- 当前时间和超时边界；
- 批量大小；
- `FOR UPDATE SKIP LOCKED`；
- 抢占状态；
- 集群竞争结果；
- Entity 到待投递模型的转换。

例如 `markRetryOrDead(...)` 可以成立，因为它可能统一：

- 尝试次数递增；
- 重试与死信状态选择；
- 退避时间计算；
- 错误摘要截断；
- 当前状态条件和更新时间；
- 更新失败的并发处理。

如果这些方法最终只调用 `updateStatus(id, SENT)` 或绑定一个枚举常量，则不成立，应合并为 `updateStatus(...)`。

方法名称是否更业务化不是关键，是否封装了稳定且不可由简单参数替代的持久化语义才是关键。

## 无效包装

以下模式通常没有 Repository 价值：

```java
void insert(Entity entity) {
    mapper.insert(entity);
}

Entity findById(Long id) {
    return mapper.selectById(id);
}

void enable(Long id) {
    mapper.updateStatus(id, ACTIVE);
}

void disable(Long id) {
    mapper.updateStatus(id, DISABLED);
}
```

以下特征全部存在时，Repository 包装没有增加持久化语义：

- 方法只改变名称；
- 所有参数原样传递；
- Mapper 结果原样返回；
- 方法只绑定一个常量；
- 没有转换模型、处理持久化结果或形成聚合操作；
- Service 仍然知道 Mapper 字段和影响行数语义。

全部成立时，删除包装、合并能力或重新设计 Repository 边界。

简单转发并非绝对禁止。Repository 为隔离框架类型而保留极少量转发时，必须保持整个 Repository 仍然具有明确聚合和持久化边界，不得以此为模板扩展出一一映射。

## 验收记录

持久化验收记录必须包含：

1. Service、Repository 和 Mapper 的相关公开方法；
2. 每个 Service 方法的业务动作和副作用；
3. 每个 Repository 方法增加的持久化语义；
4. 每个 Mapper 方法对应的 SQL 形状；
5. 一一转发、常量绑定、重复 SQL 和业务名称下沉的扫描结果；
6. 自定义 Mapper 与 Repository 方法数量及比例；
7. Repository 对 Entity、Result、影响行数、分页和框架类型的隔离结果；
8. Service 对状态机、权限和业务校验的所有权；
9. 乐观锁和原子条件对应的 SQL；
10. 状态条件、并发冲突、查询转换和聚合写入的测试证据。

## 不合规情况

以下任一情况成立时，持久化实现不符合本规范：

- Service 直接依赖、注入或调用 Mapper；
- Mapper 使用 enable、disable、approve、reject 等业务动作拆分相同 SQL；
- Repository 为每个业务动作创建只绑定常量的包装方法；
- Repository 与 Mapper 大面积同名、同参数、同返回值；
- Repository 没有隐藏影响行数、框架类型和数据库结果结构；
- Service 直接处理 Mapper 行数、Wrapper、ResultMap 或 SQL 字段；
- Mapper 抛出业务异常或决定状态机；
- Repository 校验本应由枚举和类型系统表达的状态格式；
- 乐观锁没有进入原子 SQL；
- 关系同步和聚合写入散落在 Service 的多次 Mapper 调用中；
- 方法数量和映射关系表明 Repository 只是形式层；
- 只根据方法名称判断层级，没有证明每层实际承担的语义。
