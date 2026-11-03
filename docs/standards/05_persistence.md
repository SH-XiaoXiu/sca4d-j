# 05 SQL 与持久化

持久化代码把业务决定、数据读写方式和 SQL 分开，每一层只让调用方少理解一组下层细节。查询的范围和数量要有界；写入要把竞争条件放进数据库的原子操作，并明确失败结果。

## 持久化各层

Mapper、Repository 和 Service 都是封装：每一层依赖下一层的行为，把它换成更上层的含义（见 [01 工程设计原则](/standards/01_principles)，各层职责见 [02 功能归属与调用关系](/standards/02_architecture)）。本页只说明持久化相关的两层怎么写。

以修改 Account 状态为例。Mapper 的 XML 只写一条 SQL：

```xml
<update id="updateAccountStatus">
    UPDATE iam_account SET status = #{status}
    WHERE id = #{id} AND deleted = 0
</update>
```

Mapper 接口返回修改行数 `int`。Repository 把行数解释为「目标存在并已修改」：

```java
@CheckReturnValue
boolean updateAccountStatus(@NotNull Long id, @NotNull IdentityStatus status) {
    return AffectedRowCounts.changed(mapper.updateAccountStatus(id, status), "修改账号状态");
}
```

`IdentityService.changeAccountStatus(...)` 根据返回的 `boolean`，在目标不存在时抛出 `BusinessException`。这个方法的语义是后写覆盖（Javadoc 中明确写明），所以 SQL 只检查 ID 和删除标记，没有版本条件。需要避免并发覆盖的写入见下文「写入与部分更新」。

Controller 不依赖 Repository 或 Mapper，Service 不依赖 Mapper，也不解释影响行数。一层如果只是把相同的参数传给下一层、再原样返回数据库行或影响行数，调用方仍要理解 SQL 的结果，这一层应当删除。一个 Repository 方法即使只执行一条 SQL，也要为调用方增加含义，例如检查写入结果、转换行类型、统一访问范围或隔离数据库异常。没有数据库访问的流程，不创建空的 Repository 和 Mapper。

## SQL 查询与写入

SQL 应明确字段、条件和结果。简单固定查询可以使用框架支持的直接声明；动态筛选、复杂联表、聚合、批量写入或原子条件更新应放在便于单独审查的位置。不要通过任意字段 Map、动态表名或通用 CRUD 包装隐藏实际读写的列与条件。

Mapper 或数据库适配器表达查询与写入结构。业务动作的名称、允许的状态流转和异常含义由 Service 或领域对象决定。相同 SQL 字符串不必机械合并；不同 SQL 只有在读写对象、条件和原子不变量相同时才合并。

例如「启用」和「停用」都只修改账号的 `status` 时，Service 可以分别提供两个业务动作，Mapper 则接收已确定的目标状态，使用同一个状态更新 SQL。若「停用」还需要写停用原因、结束会话，而「启用」需要另一组原子条件，这些差异属于各自业务流程；SQL 是否共用取决于实际写入的列和条件，不能只看动词是否不同。

项目选用 MyBatis 时，Mapper 负责业务 SQL，Repository 负责把 Mapper 结果转成持久化含义。迁移、测试初始化和基础设施接入是否使用 JDBC，由各自的所有者说明；不能把一种技术选型写成对所有数据库访问方式的普遍禁令。

## 查询

先确认调用方已掌握的事实，再决定是否需要联表。列表和分页查询应先限制数据范围、排序和数量，再按批次加载关联数据。跨表筛选、排序或同一快照读取确实需要数据库一次完成时，使用能够保持结果一致的 SQL。

假设一次分页读取 50 个账号，随后逐个查询角色，就会额外执行 50 次 SQL。先取得有界的账号 ID 集合，再按同一访问范围批量查询这些账号的角色，可以保持分页主体不变，并让关联查询次数不随账号数量增长。若角色本身参与筛选或排序，则需要先在数据库查询中处理该条件，不能在分页后补筛。

拆分查询不得改变分页、去重、稳定排序、访问范围或一致性要求。列表、树、批量任务和循环处理应计算数据库与外部调用次数；逐条加载关联记录、重复读取同一配置或逐项执行可批量完成的写入，需要说明保留理由和批次上限。

`IN`、JOIN、聚合、批量 DML 或预加载映射能稳定消除逐项访问时，应采用对应方式。逐项乐观锁、单项失败隔离和外部协议限制可能需要保留逐项操作；此时应说明幂等、部分成功、重试和最大批量。异步或并行不会消除调用数量。

## 写入与部分更新

需要避免并发覆盖时，目标 ID、实际需要保护的当前状态、版本与访问范围进入同一条写入 SQL。先读取再无条件更新不能提供乐观锁保证。Service 决定状态变化，Repository 先把数据库结果解释为写入成功或条件未满足；只有已有事实足以区分时，才进一步报告不存在、状态冲突或版本冲突。

下面的 SQL 假设账号按租户隔离，且这次写入必须基于指定状态和版本；它不表示每个状态字段都需要版本列：

```sql
UPDATE account
SET status = :next_status, version = version + 1
WHERE id = :account_id
  AND tenant_id = :tenant_id
  AND status = :expected_status
  AND version = :expected_version;
```

Service 选择 `expected_status` 和 `next_status`，Repository 解释实际影响行数，Mapper 绑定列与条件。把版本检查留在 Java 的先行查询中，再执行不带版本条件的 `UPDATE`，两个请求仍可能根据同一旧版本先后覆盖结果。

一次更新如果需要区分「保持原值」「写入值」「清空字段」，应使用能够明确表达三种意图的类型或具名参数。完整替换且所有字段都有确定写入值时，不需要为了形式统一创建三态 Patch。可空参数不得同时表示忽略和清空；全字段均不写入的请求应在执行 SQL 前拒绝或明确返回无变化。

例如，修改资料时请求中没有 `remark` 字段表示保留原值，`{"remark": null}` 表示清空，`{"remark": "巡检员"}` 表示写入文本。若三种输入最终都被转成一个可空 Java 参数，Repository 就无法判断 SQL 应跳过该列还是写入 `NULL`。

在这个例子里，HTTP 输入只表达用户提交了什么；Service 决定资料是否允许清空；Repository 将已经确定的结果转成保留、写入或清空的列操作；Mapper 在 SQL 中明确列出允许修改的列。完整替换接口一次写入所有字段时，每列都有确定结果，可以直接使用具名参数，不需要三态包装。

下面的 Java 值和 MyBatis 片段示意三种列操作；SQL 假设 `remark` 允许为空：

```java
Patch<String> unchanged = Patch.ignore();
Patch<String> written = Patch.set("巡检员");
Patch<String> cleared = Patch.clear();
```

```xml
<set>
    <choose>
        <when test="remark.set">remark = #{remark.value},</when>
        <when test="remark.clear">remark = NULL,</when>
    </choose>
</set>
```

`IGNORE` 不生成 `remark` 赋值，`SET` 绑定文本，`CLEAR` 写入数据库 `NULL`。实际更新还需在同一条 SQL 中写入目标 ID、必要状态和版本条件；所有列都为 `IGNORE` 时，Repository 应在调用 Mapper 前明确拒绝或返回无变化，而不是执行缺少有效 `SET` 的 SQL。

预期影响一行的写入，应检查零行和多行分别意味着什么。零行可能表示资源不存在、条件已变化、允许的幂等结果或客户端版本过期，不能仅根据行数猜测业务含义。多行超出声明的写入应视为条件或数据错误。批量写入按实际集合大小和操作约定检查结果。

例如，`UPDATE` 同时检查账号 ID 与预期版本时，零行只说明这组条件没有命中。若调用方需要区分「账号不存在」与「版本过期」，Repository 还需依据已读取事实或补充查询得出结果；无法区分时不能把零行直接称作版本冲突。

集合写入优先使用有界的集合 SQL 或明确的批处理机制。选择批处理实现前，确认它何时返回真实影响行数、怎样处理部分失败，以及调用方是否依赖逐项结果。

例如，一次为多个账号增加相同关系，可以由一条带多组 `VALUES` 的 SQL 写入；在 Java 中循环调用单行 Mapper，数据库往返次数仍随账号数增长。使用 MyBatis `ExecutorType.BATCH` 时，单次调用在 flush 前不能提供最终影响行数；需要逐项判断写入结果的 Repository，不能把占位值当作真实成功数量。

以下两段分别表示「每个账号调用一次 Mapper」与「一次 Mapper 调用生成一条集合 SQL」，均为写入结构示意：

```java
for (Long accountId : accountIds) {
    mapper.insertRoleMember(roleId, accountId);
}
```

```xml
<insert id="insertRoleMembers">
    INSERT INTO role_member (role_id, account_id)
    VALUES
    <foreach collection="accountIds" item="accountId" separator=",">
        (#{roleId}, #{accountId})
    </foreach>
</insert>
```

空集合应在调用 Mapper 前返回明确的无变化结果，不能生成缺少 `VALUES` 的 SQL。非空集合仍需按数据库参数上限控制每批数量。没有冲突忽略策略时，任一行违反唯一约束会使整条 `INSERT` 失败；显式采用冲突忽略时，Repository 再按实际写入行数判断结果。`<foreach>` 只生成 SQL，不等于把 MyBatis 执行器改成 `BATCH`。

## 事务与外部副作用

完整业务动作的数据库事务通常由 Service 定义。Repository 可以为稳定的聚合存储操作声明事务，但不在其中决定业务权限或执行通知。外部调用不能因为同处一个方法就被当作数据库事务的一部分；提交前、提交后和外部确认后的状态应分别命名。

数据库提交后必须可靠完成的消息或外部操作，需要持久化任务、Outbox 或等价的恢复机制。进程内提交后回调可能丢失，不作为可靠投递、审计或恢复依据。

例如，创建订单与登记待发布消息可以在同一数据库事务中完成；事务回滚时两者都不存在。登记成功只表示消息等待发布，消息代理确认后才表示发布完成。若直接在事务内调用代理，数据库回滚与代理已接收之间可能留下无法由原事务撤销的部分成功。

## 并发

并发问题的本质是：多个请求或多个实例同时操作同一份数据，结果取决于谁先谁后。设计时先回答三件事：竞争的对象是什么，输掉的一方怎么办（失败、重试、等待或无变化），以及怎样恢复。机制按它们解决的问题选择，而不是先选一个机制再套用：

| 要避免的问题 | 机制 | 竞争结果由谁决定 |
| --- | --- | --- |
| 同一份事实被写入两次 | 唯一约束 | 数据库的唯一索引 |
| 同一个请求被重放 | 幂等键 | 幂等键上的唯一约束或状态 |
| 覆盖了别人刚做的修改 | 条件更新、版本检查 | `UPDATE` 的 `WHERE` 条件 |
| 多个执行者领取同一个任务 | 带状态和租约条件的原子更新 | 同一条 SQL |
| 跨事务串行执行，或保护外部副作用 | 分布式锁 | 锁服务 |
| 同一事务内要对同一批行执行多条语句 | 行锁 | 数据库的行锁 |
| 请求太频繁 | 限流 | 计数器 |

顺序也是选择的优先级：数据库条件能决定竞争结果时，不使用锁；只有条件无法表达，才依次考虑分布式锁和行锁。这些机制解决的问题不同，不能互相替代。

### 唯一约束与幂等

唯一约束保证并发的两次写入最终只有一次成功，Repository 把约束失败转换成明确的冲突结果，见 [06 数据库](/standards/06_database)。幂等键用来识别被重放的请求：同一个键第二次到达时，返回第一次的结果，或者在第一次仍在处理时返回等待信息。定时任务、消息消费和重试都可能重复执行，代码要写明重复执行的结果、租约到期后由谁接管，以及节点退出后未完成的工作怎样处理。

### 条件更新与版本

目标 ID、需要保护的当前状态和版本，放进同一条 `UPDATE`，见上文「写入与部分更新」。

HTTP `If-Match` 适用于调用方基于已读取的稳定资源快照进行编辑，且过期写入可能覆盖重要新内容的流程。状态命令、追加记录和允许后写覆盖的字段 Patch 通常依靠服务端当前状态、唯一约束或原子条件。是否使用 ETag 取决于资源和交互约定，不由数据库是否有版本列自动决定。

例如，用户读取一份完整配置后修改并提交整份内容，另一个人先改动配置时，旧快照可能覆盖新值。这个编辑流程需要让客户端提交版本条件，并在冲突后重新读取与确认。将一条通知标记为已读则由服务端判断当前状态并执行条件更新；表中即使有版本列，也不因此要求页面先读取 ETag。

### 原子领取

多个实例领取同一批待处理记录时，如果状态、版本和租约条件可以写进一条 SQL，由这条 SQL 决定谁领到。下面的 SQL 只示意待发送消息的 `PENDING` 分支，实际投递还要处理失败重试和租约到期，候选读取与写入条件必须保持一致：

```sql
WITH candidate AS MATERIALIZED (
    SELECT message_id, version
    FROM messaging_outbox
    WHERE status = 'PENDING' AND available_at <= :now
    ORDER BY available_at, message_id
    LIMIT :limit
), leased AS (
    UPDATE messaging_outbox AS message
    SET status = 'SENDING', lease_id = :lease_id,
        lease_until = :lease_until, version = message.version + 1
    FROM candidate
    WHERE message.message_id = candidate.message_id
      AND message.version = candidate.version
      AND message.status = 'PENDING'
      AND message.available_at <= :now
    RETURNING message.message_id
)
SELECT message_id FROM leased;
```

两个实例可以读到同一个候选，只有满足当前版本和状态条件的实例取得 `RETURNING` 结果。这一条 SQL 已经决定了竞争结果，候选读取不需要加 `FOR UPDATE`。

### 分布式锁

条件更新和唯一约束无法覆盖整个业务动作时，才使用分布式锁，例如需要串行执行的事务提交，加上提交后的外部操作。锁的键由稳定的资源标识构造；持锁后重新读取状态，再做判断。数据库条件和唯一约束已经足以产生明确的竞争结果时，不再叠加锁。

### 行锁

行锁是最后的选择，只用于必须在同一个事务中对同一批行执行多条语句，而单条 SQL 无法表达的场景。使用时要写明锁的顺序、批量上限和失败回滚。

清理 Job 运行历史就是这样的场景：要在同一事务中锁定一批根记录，再按关系顺序删除它们的子记录，最后核对根记录的删除数量。以下代码取自现有的 Repository 方法，省略类定义和 import：

```java
@Transactional
public int deleteSucceededRunsBefore(@NotNull Instant completedBefore, int limit) {
    if (limit < 1) {
        throw new IllegalArgumentException("Job 成功历史删除上限必须大于零");
    }
    List<Long> selected = mapper.selectExpiredRunIdsForUpdate(
            appName,
            Objects.requireNonNull(completedBefore, "Job 成功历史截止时间不能为空"),
            limit);
    int[] deleted = {0};
    sqlBatches.forEach(selected, 1, 1, batch -> {
        mapper.deleteAttempts(batch);
        mapper.deleteDependencies(batch);
        mapper.deleteNodes(batch);
        int count = mapper.deleteRuns(appName, batch);
        AffectedRowCounts.requireExactly(count, batch.size(), "清理 Job 运行历史");
        deleted[0] += count;
    });
    return deleted[0];
}
```

对应的 Mapper XML 使用应用隔离、状态和截止时间条件：

```xml
<select id="selectExpiredRunIdsForUpdate" resultType="long">
    SELECT id FROM job.job_run
    WHERE app_name = #{appName} AND status = 'SUCCEEDED'
      AND completed_at &lt; #{completedBefore}
    ORDER BY completed_at, id LIMIT #{limit}
    FOR UPDATE SKIP LOCKED
</select>
```

多条删除语句要么一起提交，要么一起回滚，`FOR UPDATE SKIP LOCKED` 让并发的实例领取不同的根记录。它和上一节的原子领取解决的问题不同，不能由其中一个例子推出「所有领取都加锁」或「所有领取都不用锁」。

## 缓存

缓存是性能优化手段，也是同一个事实的另一份副本（见 [01 工程设计原则](/standards/01_principles) 的「数据」一节）。先让逻辑正确，确有性能问题再加入。缓存值必须能从权威数据重新生成，写入、读取、失效三件事都要由代码明确负责。

### 为什么大多数场景不直接使用 `@Cacheable`

`@Cacheable` 的做法是：用方法参数生成 Key，命中就直接返回，未命中就执行方法并保存返回值。它适合参数完全决定结果、数据很少变化、允许短时间读到旧值的方法，例如读取一份外部提供的只读配置。大部分业务不满足这几个条件：

- **失效的方向相反。** 注解按 Key 失效，业务变化却是从另一端来的。角色的权限变了，要失效的是「拥有这个角色的所有 Principal 的缓存」，注解只能按 Key 逐个删除，或者 `allEntries` 全部清空。项目里的 `RbacPermissionCache.evict(...)` 接收的是受影响的 RBAC 范围和 Principal 集合，精确删除，这是业务关系决定的，注解无法表达。
- **回填会和失效交错。** 请求 A 未命中，读到数据库中的旧值；这时另一个请求修改数据并失效缓存；A 随后把旧值写回缓存，旧值就一直留在里面。`@Cacheable` 没有「读取之后发生过失效，则不回填」的机制。`RbacPermissionCache.find(...)` 在返回命中项的同时返回修订号，`put(...)` 只回填读取后没有发生失效的坐标，就是为了防止这种情况。
- **一次读取多个对象。** 权限查询一次要读取多个 Principal。注解把整个参数集合当作一个 Key，只能整批命中或整批未命中，不能逐个命中、只回源缺失的部分。
- **「没有」也是结果。** 一个 Principal 没有任何授权，是数据库确认的事实，应当缓存，否则每次都要回源。`@Cacheable` 默认遇到 `null` 的处理取决于缓存配置，调用方无法区分「没有缓存」和「缓存了空结果」。`RbacPermissionCache.put(...)` 明确说明保存空授权结果。
- **事务提交的时机看不出来。** 在事务内修改数据后立刻失效缓存，另一个请求可能在提交前读到旧数据并重新填入。注解是否在提交后才执行，取决于代理的顺序和缓存管理器的配置，业务代码里看不出来，也无法用测试稳定地验证。
- **行为被隐藏。** 注解依靠代理生效，同一个类内部的调用不经过代理，缓存静默失效；调用者看不到方法里有缓存读写。这与「事务、锁和外部操作的位置要能从主流程里看出来」的原则冲突，见 [04 Java 与 Spring](/standards/04_java-and-spring)。

因此，项目没有使用 `@Cacheable`，权限缓存是一个明确的接口 `RbacPermissionCache`，由 `RbacService` 在回源前后读写，实现有 `RedisRbacPermissionCache`，也可以关闭为 `NoOpRbacPermissionCache`。使用 `@Cacheable` 前，先逐条确认：参数是否完全决定结果、变化是否可以只靠过期时间处理、读到旧值是否可以接受。有任何一条不成立，就写显式的缓存接口。

### 缓存的写法

- Key 必须包含所有影响结果的条件：租户、RBAC 范围、身份、语言等。漏掉一个，就会把一个人的结果返回给另一个人；
- 失效和回填的顺序要写明，避免回滚之后误删，或旧值在事务提交前被重新填入；
- 本地缓存只用于允许实例之间短时间不一致的数据；
- 缓存的 Value 发生不兼容变化时，使用新的名称、Key 版本，或明确的旧值读取策略；
- 权限、额度、唯一性和状态机的判断不能只依赖单实例缓存。

例如，事务内修改账号状态后立即删缓存，另一个请求可能在提交前从数据库读到旧状态并重新填入缓存。提交后失效可以避免这一顺序；要求事务提交与通知其他实例可靠衔接时，还需要消息或持久任务记录失效动作。
