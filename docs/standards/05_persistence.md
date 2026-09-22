# 05 SQL 与持久化分层完整规范

## 目标

Mapper、Repository 和 Service 必须形成清晰的抽象梯度。

三层之间的价值来自语义转换、职责收敛和细节隐藏，不来自类名、注解或调用层级本身。

增加 Repository 后，Service 应当不再理解 SQL 形状、Mapper 返回值、影响行数和持久化结果结构。增加 Mapper 后，Repository 应当不再手写数据库访问细节。

任何一层只把相同参数传给下一层并原样返回结果，都需要证明其独立价值。

## 抽象梯度

| 层级 | 使用的语言 | 负责内容 | 不负责内容 |
| --- | --- | --- | --- |
| Service | 业务动作和业务规则 | 状态机、权限、业务校验、事务边界、副作用、事件与通知 | SQL、影响行数、ResultMap、数据库字段组合 |
| Repository | 持久化语义和聚合存储 | 聚合读写、聚合加载、关系维护、乐观锁结果、Entity/Result 转换 | HTTP 请求、页面动作、业务权限、业务状态机 |
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

Repository 表达聚合读写、关系维护和持久化结果，不使用页面按钮、Controller 路由或每个业务动作的名称复制 Mapper 方法。

Repository 负责：

- 隐藏 Mapper、XML、JOIN、动态 SQL 和数据库结果结构；
- 组合分页、动态筛选和排序所需的 Mapper 读取，并将数据库分页结果转换为上层契约；
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

Repository 不直接使用 JdbcTemplate、JdbcClient、Connection、PreparedStatement 或手写 SQL 访问业务数据。关系数据库读写由 Mapper 执行；不能以尚未采用 MyBatis、历史实现或 SQL 简单为由跳过这一层。数据库迁移和隔离测试初始化不属于生产 Repository 的业务数据访问。

## Mapper

Mapper 表达最小、可组合、可验证的 SQL 查询和写入。

Sca4d-J 使用手写 MyBatis Mapper。字段少、条件固定且一眼可以读完的 SQL 可以使用 Mapper 注解；动态条件、复杂 JOIN、聚合、批量操作、原子条件写入、锁或数据库专用表达式使用 Mapper XML。Mapper 只声明当前 Repository 需要的 SQL 形状，不创建通用 CRUD 基类或 Query Wrapper 来隐藏字段与条件。

典型方法包括：

- `insert(...)`
- `selectById(...)`
- `selectPage(...)`，用于同时表达筛选、访问范围和稳定排序的分页 SQL
- `countByStatus(...)`
- `updateStatus(...)`
- `updateVersionedFields(...)`
- `deleteBy...(...)`

Mapper 负责：

- SQL 条件和数据库字段；
- 原子写入和原子条件更新；
- 查询行、聚合行和影响行数；
- 版本、状态、租约条件和 `UPDATE ... RETURNING` 的原子竞争语义；
- Entity 和 SQL Result 的映射。

Mapper 不负责：

- `enable`、`disable`、`approve`、`reject` 等业务动作；
- 抛出业务状态异常；
- 决定业务允许的状态流转；
- 发送消息、清理缓存和调用外部服务；
- 将多个 SQL 步骤包装成业务流程。

同一个 SQL 形状只保留一个 Mapper 方法。

合并的依据是数据语义，不只是 SQL 字符串完全相同。状态、次数、错误摘要和完成时间共同描述一次投递的进展，即使不同动作修改其中不同字段，也应优先使用一个明确列出这些字段的 Patch 更新入口。业务层选择目标结果，Mapper 不按「开始、成功、失败」分支决定字段值。

设计从少量、字段明确的较粗读写入口开始，有独立数据含义或不同原子不变量时再拆。例如凭据版本替换与验证码时间步抢占的并发条件不同，可以保留独立入口。既不为每个业务动作拆 Mapper，也不强制一张表只有一个万能 CRUD；禁止任意字段 Map、动态表名和反射更新。

## 字段部分更新

同一更新入口需要分别表达保留列原值、写入非空值和写入数据库 `NULL` 时，Repository 必须使用 [Patch](/tutorials/backend-feature) 表达每个字段的写入意图：

- IGNORE 不生成该列赋值；
- SET 绑定非空值，数值零、`false`、空字符串和空集合均保持为 SET；
- CLEAR 明确生成数据库 `NULL`；
- `Patch.set(null)` 不得用于代替 CLEAR；
- 所有字段均为 IGNORE 时，Repository 必须在调用 Mapper 前拒绝该更新。

Repository 根据 Service 已经确认的业务结果构造 Patch。`Patch.resolve(...)` 和 `Patch.choose()` 只用于把这些结果写成明确、可检查的字段决策，不执行 HTTP、实体字段或业务校验。Controller 和 HTTP Request 不依赖 Patch，Service 不理解 Mapper 如何根据 Patch 生成列赋值。

Mapper 可以直接接收具名的 `Patch<T>` 参数，也可以使用所属模块的类型化更新对象。根据字段数量、共同使用方式和复用情况选择清楚的写法，不要求每个更新都建立一个 Update 类，不只为了减少参数创建包装。

注解 SQL 或 XML 明确列出每个允许修改的列。SET、CLEAR 和 IGNORE 的分支必须能从 SQL 直接读出；版本、状态、数据范围和其他原子条件仍然写在同一条 SQL 中。不以可空 Java 参数隐含约定清空或忽略，不因更新覆盖一组完整字段而绕过 Patch。不得使用注解扫描、反射、字段 Map、通用 DAO、Query Wrapper 或自动 SQL 生成器根据 Patch 推断表名和列名。

## 方法数量信号

自定义 Mapper 方法数量大于或等于 Repository 公开持久化方法数量时，大概率存在以下问题之一：

- Repository 与 Mapper 一一对应；
- SQL 被按业务动作过度拆分；
- Service 语义下沉到了 Mapper；
- Repository 没有聚合查询、转换结果或隐藏持久化结构；
- Repository 只是为了符合分层形式而存在。

该判断是高风险信号，不是数学定律。

以下情况可能使数量接近但设计仍然成立：

- Repository 只暴露少量稳定查询，而每个查询需要独立复杂 SQL；
- Mapper 需要分别提供 count 与 page rows，Repository 将其组合为一个分页结果；
- 一个 Repository 协调多个 Mapper，单个 Mapper 的方法数量不能单独代表 Repository 价值；
- 一个 Mapper 可能同时服务多个 Repository 操作，单独比较数量不能代替逐项查看调用关系。

审查时重点检查方法映射关系，而不是只比较数字。

上述情况都需要逐项说明实际职责，不构成按类别豁免。不能靠改名、增加无用 Repository 方法、Optional 包装或集合复制降低比例；方法少于阈值也不能证明合规。

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
    @AffectedRows(target = "Account", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    void updateStatus(
            Long id,
            AccountStatus expectedStatus,
            AccountStatus nextStatus,
            long expectedVersion,
            OffsetDateTime updatedAt) {
        mapper.updateStatus(
                id,
                expectedStatus,
                nextStatus,
                expectedVersion,
                updatedAt);
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

查询设计按以下顺序选择：

1. 先确认上层已经掌握的事实。例如已经加载 Group 类型和是否查询子级，就把这些信息作为明确参数传入，不再 JOIN Group 只为重复取值。
2. 没有严格单次 IO 性能或一致性要求时，先有界查询主体，再批量读取关联事实并组装。Repository 聚合自身对象与存储关系，Service 组合不同领域结果并执行业务判断。不得逐条查询形成 N+1。
3. 前两种方式不能保持所需行为时才保留复杂 SQL，例如明确要求同一快照的强一致读取、精确统计，以及必须在分页前完成的跨表筛选和排序。审查记录需写明具体理由，不以「性能可能更好」代替证据。

多次 IO 往返是允许的，目标是让查询和对象关系易于维护。拆分不得改变分页、去重、排序、访问范围、版本或绑定代数语义，也不能把一条长 SQL 改成另一层同样难懂的数据库记录拼装。

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

Repository 可以将 Mapper 的 count 与 rows 组合为一个分页结果，可以将多个 Result 转换为一个业务模型，可以隐藏 JSON、枚举和字段映射细节。

Mapper 不直接返回 View。Repository 不返回 MyBatis 配置类型、游标实现、ResultMap 中间结构和数据库专用分页对象。

## 写入与乐观锁

Mapper 写方法不抛出业务异常。固定单行写入由 Repository 使用 `@AffectedRows` 声明，Repository 不读取 Mapper 返回的影响行数；动态行数、集合大小和生成结果仍由 Repository 显式读取。

Repository 将数据库结果转换为持久化层可理解的结果：

- 影响一行表示写入成功；
- 影响零行根据已知条件转换为不存在、状态条件不匹配或乐观锁冲突；
- 唯一约束和数据完整性问题转换为统一的持久化冲突；
- 生成 ID、更新时间和版本回填到稳定模型。

Service 决定冲突在业务流程中的含义和后续副作用。

乐观锁的版本条件必须进入原子 SQL。Repository 负责要求调用方提供 expectedVersion，并统一处理更新失败结果。

Repository 不通过先查询再无条件更新模拟乐观锁。

`@AffectedRows` 的适用条件、零行策略、`AffectedRowCounts` 和集合写入规则见本页的[影响行检查](/standards/05_persistence#影响行检查)。

## 聚合与事务

Service 通常定义业务事务边界，因为只有 Service 理解完整业务动作和副作用。

Repository 可以组合多个 Mapper 操作，但这些操作必须共同完成一个可以准确命名的聚合读写，例如：

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

审查时必须给出可验证的调用数量，例如「固定 2 条 SQL」「1 次分页查询加每项 2 次条件写入」「每个对象 1 次外部删除」。只写「数据量不大」「已经分页」或「后台执行」不能证明不存在 N+1。

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
- `load...`，用于找不到即失败的读取
- `page...`
- `save...`
- `updateStatus...`
- `replace...`
- `sync...`
- `append...`
- `claim...`，仅在它准确表达原子 SQL 抢占语义时

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

例如 `claim(...)` 可以成立，因为它可能统一：

- 当前时间和超时边界；
- 批量大小；
- 状态、版本和租约到期条件；
- `UPDATE ... RETURNING` 的原子领取结果；
- 抢占状态；
- 集群竞争结果；
- Entity 到待投递模型的转换。

重试与死信的状态选择、尝试次数及退避时间由 Service 或领域对象确定。Repository 保存这一结果并解释版本或租约条件冲突，Mapper 使用同一个投递进展更新入口执行字段 Patch，不在存储层重新作出业务判断。

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

全部成立时，删除包装、合并重复读写或重新设计 Repository 公开接口。

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
- Repository 直接使用 JDBC 执行业务 SQL；
- 为查询临时增加 Row、Data、Result 等包装类型，未证明其独立且稳定的概念；
- Mapper 使用 enable、disable、approve、reject 等业务动作拆分相同 SQL；
- Repository 为每个业务动作创建只绑定常量的包装方法；
- Repository 与 Mapper 大面积同名、同参数、同返回值；
- Repository 没有隐藏影响行数、框架类型和数据库结果结构；
- Service 直接处理 Mapper 行数、MyBatis 配置类型、ResultMap 或 SQL 字段；
- Mapper 抛出业务异常或决定状态机；
- Repository 校验本应由枚举和类型系统表达的状态格式；
- 乐观锁没有进入原子 SQL；
- 关系同步和聚合写入散落在 Service 的多次 Mapper 调用中；
- 方法数量和映射关系表明 Repository 只是形式层；
- 只根据方法名称判断层级，没有证明每层实际承担的语义。

## MyBatis 与持久化规范

Sca4d-J 选择原生 MyBatis 的原因和取舍见[数据访问与持久化设计](/tutorials/backend-feature)。本页只规定采用这项技术选型后，Mapper、SQL 和 Repository 必须遵守的编码规则；Service、Repository 和 Mapper 的完整职责、示例与评审方法见本页前面的对应章节。

### 基本选择

Sca4d-J 使用原生 MyBatis Mapper。根据 SQL 形状选择写法：

- 字段少、条件固定、一眼可以读完的单表查询或写入，可以使用 `@Select`、`@Insert`、`@Update` 或 `@Delete`；
- 存在动态条件、复杂联表、聚合、批量操作、数据库专用表达式或需要长期单独审查时，使用 Mapper XML；
- 一个 Mapper 内保持一致的字段名、参数名和结果类型，不为了「全部使用同一种形式」把简单 SQL 拆成 XML，也不把难以阅读的动态 SQL 挤进注解字符串。

不引入第二套 ORM、通用 Query Wrapper 或动态字段 Map 表达同一批表。现有 MyBatis 能够清楚表达的 SQL，就使用项目已有方式。

### 查询字段和结果

长期业务查询显式列出字段，不使用 `SELECT *`。这样能够在表新增列、字段类型变化或查询涉及敏感值时，清楚判断 Mapper 的输出。

单表数据可以映射为表示该持久化数据的普通类。联表、聚合和统计使用专用行类型，字段只包含当前 SQL 实际返回的内容。Repository 再把这些行类型转成 Service 所需要的对象或结果。

已有 MyBatis 驼峰映射和 TypeHandler 能够正确处理字段时，不在每个 Mapper 重复声明 `resultMap`。确实存在一对多结构、字段重名、构造参数或数据库专用转换时，使用明确 `resultMap`。

### 查询条件

查询必须包含实际所需的以下条件：

- 逻辑删除状态；
- 资源范围或访问范围；
- 业务状态和有效时间；
- 稳定且可重复的排序；
- 分页、批量或抢占上限。

需要权限范围的读写由 Repository 把已经批准的 `ResourceScope` 或具体查询条件放入 SQL。不先查询全部数据再在 Service 或 Controller 内存过滤，也不在读取时检查范围后执行不带范围的写入。

### 状态变更与乐观写入

需要乐观锁的状态变更使用一条原子 SQL 同时检查：

- 目标 ID；
- 必要的访问范围；
- 允许变更的当前状态；
- Repository 解析得到的预期版本；
- 本次允许更新的具体列。

更新成功时原子递增版本。根资源 Repository 读取当前 `Version` 后调用 `VersionConditions.resolve(...)`；HTTP `If-Match` 存在时使用请求版本条件，内部调用使用刚读取的版本。Service 不接收 HTTP 版本，也不直接理解影响行数。

不使用「先查询、再无条件更新」模拟乐观锁，也不向 Mapper 传入一个字段不明的完整对象，让 SQL 猜测本次应修改哪些列。

### 影响行检查

只执行一条 MyBatis 写入、并且最多允许影响一行的 Repository 方法使用 `@AffectedRows` 声明结果。注解只放在 Repository 方法上；Service 不声明持久化结果，Mapper 也不决定零行代表什么。

```java
class AccountRepository {
    public Account updateStatus(long id, AccountStatus status) {
        Account current = loadById(id);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.updateStatus(id, condition.expected(), status),
                condition.whenZero(),
                "Account");
        return loadById(id);
    }
}

interface AccountMapper {
    int updateStatus(long id, long expectedVersion, AccountStatus status);
}
```

带注解的方法必须成功执行且只能执行一条 `INSERT`、`UPDATE` 或 `DELETE`。方法可以在写入前后查询，但没有执行写入或执行多条写入都表示声明与实现不一致。Mapper 可以继续返回影响行数，带注解的 Repository 不接收和解释该返回值；MyBatis 插件直接读取 `Executor.update()` 的实际结果。

`whenZero` 明确零行的持久化含义：

- `ERROR`：本应写入一行，但实际没有写入；
- `ALLOW`：幂等写入或条件写入没有发生变化；
- `OPTIMISTIC_LOCK`：内部对象的版本、状态或租约已经变化；
- `VERSION_PRECONDITION`：HTTP 客户端提交的 `If-Match` 版本已经失效。

影响多于一行在所有策略下都表示 SQL 条件或数据不符合声明。内部乐观锁冲突和 HTTP 版本前置条件失败使用不同异常；Repository 使用 `VersionConditions` 返回的策略，不检查 Controller、请求头或全局状态。

以下方法不使用 `@AffectedRows`，而是直接调用 `AffectedRowCounts`：

- 返回「本次是否发生修改」的条件写入使用 `changed(...)`；
- 预期行数来自集合大小的写入使用 `requireExactly(...)`；
- 只要求达到最小影响行数的写入使用 `requireAtLeast(...)`；
- 允许影响任意行、但需要返回是否至少修改一行的写入使用 `any(...)`；
- 一个 Repository 方法执行多条 DML 时，逐项检查需要限制的写入；
- 根资源写入需要根据 `VersionCondition.whenZero()` 区分内部冲突和 HTTP 前置条件失败；
- 使用 `JdbcTemplate` 等非 MyBatis 入口时显式检查返回值。

`@AffectedRows` 由 Spring AOP 建立当前 Repository 调用范围，再由 MyBatis 插件读取 `Executor.update()` 返回的实际行数。因此，带注解的 Repository 必须是 Spring Bean，类和方法不能声明为 `final`，调用必须经过 Spring 代理，不能依赖同类方法的内部调用触发注解。注解方法不能嵌套调用另一个注解方法。

### 聚合读写与事务

Repository 可以组合多个 Mapper 或多条 SQL，但组合后必须形成稳定的数据操作，例如替换成员关系、同步权限、追加消息或保存聚合。它不在这些方法中决定业务权限、发送通知或调用外部服务。

完整业务动作的事务由 Service 公开方法定义。事务中只包含需要共同提交或回滚的数据库操作；耗时外部调用、对象存储和不需要共同回滚的消息不无理由地占用数据库事务。

### N+1 与批处理

列表、分页、树、批量任务和关系装配都要计算 SQL 与外部调用数量。以下情况都属于 N+1 候选：

- 查询一批主记录后，循环按 ID 查询关联数据；
- 循环执行可以由集合条件、批量 DML 或原子更新完成的 SQL；
- 每条记录重复加载同一字典、配置、权限或主体数据；
- 批量入口内部逐项调用远程服务、文件存储或消息系统，但没有说明批量协议和失败隔离。

能够使用 `IN`、JOIN、聚合、批量 DML、`RETURNING` 或预加载映射稳定消除的逐项调用应当消除。确因逐项乐观锁、外部协议限制、失败隔离或流式资源而保留时，设置批次上限，说明幂等、部分成功和重试结果。异步和并行只改变执行方式，不会消除 N+1。

集合写入使用一条集合 SQL，例如一个带 `<foreach>` 的多行 `VALUES`、`UPDATE ... FROM` 或 CTE。`<foreach>` 在这里生成一条 SQL，不等于循环调用 Mapper。禁止把 MyBatis 默认执行器或单次会话切换为 `ExecutorType.BATCH`；BATCH 在 flush 前只返回占位值，Repository 无法在方法返回前得到每条写入的真实影响行数。也不使用 Java 循环逐条调用 Mapper 来代替集合 SQL。

领取任务、消息或清理记录时，使用一条 CTE 选择有界候选，并通过状态、版本、到期时间或租约所有者条件执行 `UPDATE ... RETURNING`。候选查询不加 `FOR UPDATE`；多个实例可以读到同一候选，但只有满足原子更新条件的实例取得结果。Repository 直接接收已经写入新状态的行，不先查询候选后逐条登记租约。

需要跨事务串行修改同一业务对象时，Service 必须先取得 Redis 分布式锁，再开始数据库事务，并在事务中重新读取当前状态。普通 CRUD、批量领取、状态机推进和清理不使用显式行锁或 PostgreSQL advisory lock。只有数据库必须直接仲裁且不能由版本条件、唯一约束或分布式锁表达的金融级强一致事务，才可以保留 `FOR UPDATE`；新增前必须在负责页面说明不可替代的 ACID 要求和锁顺序。

### Mapper 与 Repository 命名

Mapper 放在所属对象或持久化包中，XML 放在该 Gradle 项目的 `src/main/resources/mapper/`。Mapper 方法使用 `insert`、`select`、`count`、`update`、`delete` 或准确表达原子抢占的 `claim` 等 SQL 动词。

Repository 方法表达读取、要求存在、分页、保存、替换关系、同步或抢占等稳定数据操作。Mapper 和 Repository 不因 Service 分别执行启用、停用、批准或拒绝，就为同一 SQL 形状创建多个方法。

### 完成条件

- Service 不直接依赖 Mapper；
- Repository 隐藏 Mapper、SQL 字段、行类型、分页实现和影响行数；
- Mapper 不使用业务动作决定状态机，不发布消息或调用其他模块；
- 数据范围、状态和版本条件进入原子 SQL；
- 固定单行写入由 Repository 的 `@AffectedRows` 声明解释，动态行数使用 `AffectedRowCounts`；
- 集合写入使用一条集合 SQL，没有 MyBatis BATCH 或循环调用 Mapper；
- 并发领取使用条件更新和 `RETURNING`，没有不必要的 `FOR UPDATE` 或 advisory lock；
- 查询显式列出字段，没有可稳定消除的 N+1；
- 事务、外部副作用、失败恢复和批次上限都有明确负责位置。

## 缓存规则

### 适用范围

本规则适用于 Spring Cache、Redis 缓存和 Caffeine 本地缓存。

### 数据来源

缓存必须能够从数据库或外部服务重新生成。权限、会话、幂等记录、锁、限流记录和业务写入结果不得只保存在本地缓存。

### 缓存 Key

Key 必须包含读取结果所依赖的 tenantId、principalId、locale 或其他查询条件。禁止让不同租户或不同权限范围共用同一个业务 Key。

### 失效时机

写操作必须在数据库事务提交后失效缓存。事务可能回滚时，禁止在提交前清理后立即允许其他请求回填旧值。

### 本地缓存

本地缓存只用于允许节点间短时间不一致的数据。清理一个实例不会清理其他实例；涉及权限、额度、状态机或唯一性判断时禁止使用。

### Value 变更

缓存 Value 的字段或类型不兼容变化时，必须更换缓存名称或 Key 前缀，或者提供可以读取旧值的转换。不能假设 Redis 中没有旧数据。

### 验证

测试至少覆盖首次回源、命中、事务提交后失效、Redis 不可用、本地缓存过期和不同租户 Key 隔离。

## 并发规则

### 乐观锁

`Version` 只表示数据库中真实存在的非负版本，不使用负数、`disabled` 或其他特殊值绕过检查。

HTTP 写入默认不要求 ETag 和 `If-Match`。大多数业务对象不会频繁被多人同时编辑；字段级 Patch、数据库约束、原子状态条件和服务端读取当前值，已经能处理多数真实竞争。不能因为表中存在 `version`，或者理论上可能发生并发写入，就给每个接口增加 HTTP 前置条件。

只有调用方先读取稳定资源，并根据这份快照生成后续修改，而且旧快照写入会以现实可发生的方式静默覆盖重要新内容时，才使用 ETag 和 `If-Match`。选择时同时判断：同一对象实际由多少人或自动流程修改、竞争发生频率、被覆盖内容的业务代价、一个 `Version` 是否覆盖用户看到的完整内容，以及客户端收到 412 后能否重新读取并让用户比较或重新编辑。低频理论风险且覆盖结果可以接受时，不增加这套交互成本。

以下场景通常不使用 ETag 和 `If-Match`：列表、搜索、分页和统计；创建、追加和事件记录；只写请求中明确出现字段且允许后写覆盖前写的 Patch；取消、启停、重试、设为默认等由服务端根据当前状态执行的命令；以及消息、任务和模块内部调用。这些入口分别使用字段级写入、唯一约束、状态条件、幂等或普通数据库乐观锁保证自己的正确性。删除只有在业务要求「删除用户刚确认的那个内容版本」时才使用 `If-Match`，不能仅因删除不可逆就机械添加。

当前 HTTP 契约没有实现 `If-None-Match` 和 304 缓存验证，因此不为了通用缓存单独发布 ETag。读取接口返回的 ETag 应对应一个确实需要 `If-Match` 的后续编辑流程；修改成功后可返回新 ETag，让客户端继续编辑同一资源而不必立即重读。既有接口不要求脱离相关业务任务批量删除 `@IfMatch`；修改该功能时再按上述实际场景复核。

确实需要避免客户端覆盖新内容的 HTTP 方法在 Controller 声明 `@IfMatch`。`IfMatchInterceptor` 解析单个强 ETag，`CallLifecycleAspect` 再把版本条件登记为该方法 CallExecution 的成功要求；Controller 和 Service 不接收版本参数。根资源 Repository 读取当前对象后调用 `VersionConditions.resolve(currentVersion)`，把得到的版本和零行策略用于同一条条件 SQL，同时标记版本条件已经消费。缺少 `If-Match` 返回 428，格式错误或版本已经变化返回 412；业务方法正常返回但 Repository 没有消费条件时，事务提交前或方法离开生命周期前抛出失败。

普通 HTTP、消息、任务和模块内部调用没有请求版本条件，`resolve(...)` 使用 Repository 刚读取的当前版本执行普通乐观锁；冲突表示内部竞争并返回 409。Repository 的公开方法不因为底层 SQL 需要版本就把版本参数逐层传给 Service。

覆盖旧值不会破坏业务不变量时，可以提供名称明确的无条件 Repository 方法，例如 `updateLastSeenAt(...)`。这类方法不能使用特殊 `Version`、默认参数或全局开关隐藏绕过行为。禁止先查版本再无条件写，也禁止给所有表机械增加版本列。

### 幂等

只对语义允许重放的入口使用 `@Idempotent`。Redis IdempotencyStore 在调用业务方法前原子取得处理权，并区分 `ACQUIRED`、`REPLAY`、`IN_PROGRESS` 和 `CONFLICT`。key 与方法参数请求指纹共同校验；同 key 不同请求必须冲突。幂等不能替代业务唯一约束。

### 分布式锁

只有并发执行会破坏跨事务状态、重复执行外部副作用或让多步写入无法恢复时才使用分布式锁。数据库唯一约束、条件更新或 `version` 已经能把竞争转换成明确冲突时，不再叠加 Redis 锁。

锁 key 必须唯一对应竞争资源。角色、Principal、设备下行、设备计划等独立对象按自己的稳定标识分别加锁，不使用模块级或应用级全局大锁。取得锁后再开始数据库事务，并在事务中重新查询当前值；锁内任务必须覆盖事务提交以及该资源必须串行的外部操作，不能在事务提交前提前释放。

Core Spring 的 RedisDistributedLock 使用 Redisson watchdog，在任务没有结束时自动续期，不使用按预估耗时设置的固定租约。同一线程嵌套调用相同 LockKey 时直接复用已经持有的锁，不再次访问 Redis；最外层任务结束后才释放。不同锁键仍然独立竞争，调用方必须保持固定的多锁顺序。

JVM `synchronized`、`ReentrantLock` 和进程内缓存只能协调当前实例，不能用于保护多实例共享数据库或外部系统。它们仅适合明确属于单 JVM 的可变状态，并且不能与 Redis 锁表达同一个业务互斥范围。

### 限流

`@RateLimited` 对一个具体方法使用 Redis 共享令牌桶。`permits` 是桶容量，`windowSeconds` 是从空桶连续补满所需的时间；不声明 `dimensions` 时默认使用 `GLOBAL`。Bucket4j 按经过时间连续补充令牌，不存在「窗口结束后一次性清零」的边界。

| `RateLimitDimension` | 每个额度对应的对象 | 使用条件 |
|---|---|---|
| `GLOBAL` | 具体目标类型和完整方法签名的全部调用 | 所有调用均可使用 |
| `IP` | HTTP 请求的客户端 IP | 调用生命周期中必须存在 HTTP 请求快照 |
| `UA` | HTTP 请求的 User-Agent；缺失 User-Agent 的请求共用一个额度 | 调用生命周期中必须存在 HTTP 请求快照 |
| `SUBJECT` | 当前认证 Subject | 调用生命周期中必须存在 Subject |
| `ACCOUNT` | 当前登录 Account | 调用生命周期中必须存在 Account |

Subject 与 Account 是两个独立维度。限流声明不按 Principal 计算额度；需要按具体业务对象限流时实现 `CallValueResolver<String>`，通过 `keyResolver` 返回非空白业务键。自定义键默认作为 `GLOBAL` 之外的额外维度；声明 `dimensions = {}` 后可以只使用自定义维度。

声明多个内置维度或同时使用自定义键时，每个维度有独立 Redis 令牌桶，所有桶使用同一容量和补充时间。CallLifecycle 会判断全部维度，并采用最长等待时间生成拒绝结果。各桶分别原子消费；前一个维度已经消费的令牌不会因为后一个维度拒绝而退回。重复维度、空维度且没有自定义解析器，以及缺少维度需要的调用数据都会使当前调用失败。

`@RateLimited` 把具体方法签名、IP、User-Agent、Subject、Account 和自定义值转换为 SHA-256 摘要，Redis key 不保存这些原始值。Core Spring 使用 Bucket4j Redis CAS 安装共享 RedisRateLimiter。`sca4dj.redis.rate-limit.keep-after-refill` 默认一小时，表示额度完全恢复后继续保留桶状态的时间。

Redis 是 Core Spring 的必需基础设施。RateLimiter、IdempotencyStore 或 DistributedLock 任一 Bean 缺失时，CallLifecycle 无法创建，应用启动失败；不会安装内存实现、空实现或延迟到第一次调用才失败。

HTTP 调用超过额度时返回 `429 Too Many Requests`、Core 错误码 `1005` 和正整数秒数的 `Retry-After`。SSE 请求保留 429 与 `Retry-After`，但不返回 JSON 错误体。

### 组合顺序

CallLifecycle 的顺序固定为声明检查、权限与限流评估、幂等处理权、分布式锁、业务方法及其事务、成功要求检验、幂等结果保存和审计记录。锁位于业务方法事务之外；权限范围和 `If-Match` 等要求在事务提交前检查，并在无事务方法正常返回后再次检查。异常不得留下永久的处理中记录或错误的成功缓存。

### 选择原则

数据库约束保证数据有效；乐观锁处理更新冲突；幂等处理重放；分布式锁处理必须串行的副作用；限流处理频率。禁止互相替代。
