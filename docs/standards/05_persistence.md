# 05 SQL 与持久化

持久化代码应把业务决定、数据读写语义和 SQL 执行分开。分层的价值在于让调用方少理解一组下层细节，而不是增加固定数量的类。查询要保持范围与数量有界；写入要把竞争条件放进数据库原子操作，并明确失败结果。

## 抽象梯度与封装梯度

抽象梯度描述每层选取什么信息来表达同一业务操作：Mapper 表达数据库列与条件，Repository 表达数据读取、写入及竞争结果，Service 表达业务动作与规则。向上调用时，数据库细节减少，业务含义增加。三层各自回答的问题不同；增加一个同名转发类不会提高抽象层次。

封装梯度描述每层怎样管理自己负责的细节：Mapper 收住 SQL 与字段映射，Repository 收住行模型、组合读写和数据库结果解释，Service 收住业务状态变化、事务和副作用顺序。上一层依赖下一层公开的操作与结果，不自行接管下一层的细节。封装程度由实际所有权和访问方式决定，不能用类或调用层数衡量。

| 位置 | 使用的语言 | 自己处理的内容 |
| --- | --- | --- |
| Service | 业务动作与业务规则 | 状态、权限、事务和副作用顺序 |
| Repository | 数据操作及其结果 | 聚合加载、行模型转换和持久化冲突 |
| Mapper 或数据库适配器 | SQL 读写形状 | 字段、条件、锁和行映射 |

以下三个片段取自同一个账号目录实现，省略类定义和 import。这个实现的 Mapper 检查账号 ID 与删除状态，没有版本列；后文的版本 SQL 是另一种适用条件的示意，不能混作同一段实现。

```java
@Transactional
public @NotNull PlatformAccountEntry changeStatus(
        @NotNull Long accountId,
        @NotNull IdentityStatus status,
        @NotNull Long currentAccountId) {
    PlatformAccountEntry current = loadById(accountId);
    IdentityStatus checkedStatus = Objects.requireNonNull(status, "Account 状态不能为空");
    if (checkedStatus == IdentityStatus.DISABLED) {
        if (current.accountId().equals(Objects.requireNonNull(currentAccountId, "当前 Account 标识不能为空"))) {
            throw new BusinessException(IamErrorCode.CURRENT_ACCOUNT);
        }
        if (current.protectedRoot()) {
            throw new BusinessException(IamErrorCode.PROTECTED_ACCOUNT);
        }
    }
    if (current.status() == checkedStatus) {
        return current;
    }
    repository.updateStatus(current.accountId(), checkedStatus);
    PlatformAccountEntry updated = loadById(current.accountId());
    if (checkedStatus == IdentityStatus.DISABLED && sessions != null) {
        sessions.terminateAccount(updated.accountId(), SessionTerminationReason.IDENTITY_UNAVAILABLE);
    }
    LOG.info(
            "Platform Account 状态已修改 | accountId={} previousStatus={} status={}",
            updated.accountId(),
            current.status().value(),
            updated.status().value());
    return updated;
}
```

Repository 的单行检查与 Mapper XML 分别如下：

```java
@AffectedRows(target = "Platform Account 状态")
void updateStatus(@NotNull Long accountId, @NotNull IdentityStatus status) {
    mapper.updatePlatformAccountStatus(accountId, status);
}
```

```xml
<update id="updatePlatformAccountStatus">
    UPDATE iam_account
    SET status = #{status}
    WHERE id = #{accountId} AND deleted = 0
</update>
```

业务操作判断当前账号和受保护账号不能停用；Repository 使用已有的影响行检查机制要求单行写入；Mapper 只表达数据库条件。`@AffectedRows` 是这一实现采用的机制，标准要求的是写入结果有负责解释的位置，不要求所有项目使用同一注解。

这个例子还包含另一个需要单独判断的结果：`terminateAccount` 修改会话系统，数据库事务回滚不会自动恢复已终止的 Session；方法内 `LOG.info` 发生在事务提交前，也不能证明最终提交成功。以上代码用于说明分层职责，不作为跨系统原子性或提交后日志的示范。

Controller 不直接编排 Repository 写入。Service 不直接依赖 Mapper，也不解释数据库影响行数。Repository 不把数据库行、分页实现或框架类型原样暴露给业务调用方。只有一层确实转换了职责，才保留这一层；没有数据访问的流程不创建空的 Repository 和 Mapper。

一个 Repository 方法可以只执行一条 SQL。它仍应提供调用方需要的稳定语义，例如检查写入结果、转换行模型、统一访问范围或隔离数据库异常。方法数量和名称相似只能提示审查，不能代替逐项判断。

一个账号目录实现提供了三种不同的数据操作。下表只说明职责怎样形成，不把具体类名或注解当作所有项目都要采用的规则：

| Repository 方法 | 数据库操作 | 它向调用方增加的含义 |
| --- | --- | --- |
| `findRoleAccounts(...)` | 分别查询命中总数和当前页记录 | 返回包含总数与页数据的 `Page` |
| `loadByIds(...)` | 按批次查询账号 | 拒绝重复或缺失的账号，并按 ID 返回完整结果 |
| `updateStatus(...)` | 执行一条状态更新 | 检查预期的一行确实写入；调用方不处理影响行数 |

`findRoleAccounts(...)` 的核心语句如下；`checked` 和 `checkedRoleId` 是此前已经校验的入参：

```java
long total = mapper.countPlatformAccounts(
        principalType, checked.search(), checked.status(), checkedRoleId, assigned);
List<PlatformAccountEntry> items = total == 0
        ? List.of()
        : List.copyOf(mapper.selectPlatformAccounts(
                principalType,
                checked.search(),
                checked.status(),
                checkedRoleId,
                assigned,
                checked.offset(),
                checked.page().size()));
return new Page<>(items, checked.page(), total);
```

第三种方法即使只调用一次 Mapper，也承担了写入结果的解释。相反，一个 Repository 方法若把相同参数传给 Mapper，再原样返回数据库行或影响行数，调用方仍需理解 SQL 结果，单独加这一层没有解决问题。示例中的状态更新没有要求所有对象都增加版本列；并发条件取决于该对象实际需要保护的写入。

## SQL 查询与写入

SQL 应明确字段、条件和结果。简单固定查询可以使用框架支持的直接声明；动态筛选、复杂联表、聚合、批量写入或原子条件更新应放在便于单独审查的位置。不要通过任意字段 Map、动态表名或通用 CRUD 包装隐藏实际读写的列与条件。

Mapper 或数据库适配器表达查询与写入形状。业务动作的名称、允许的状态流转和异常含义由 Service 或领域对象决定。相同 SQL 字符串不必机械合并；不同 SQL 只有在读写对象、条件和原子不变量相同时才合并。

例如「启用」和「停用」都只修改账号的 `status` 时，Service 可以分别提供两个业务动作，Mapper 则接收已确定的目标状态，使用同一个状态更新 SQL。若「停用」还需要写停用原因、结束会话，而「启用」需要另一组原子条件，这些差异属于各自业务流程；SQL 是否共用取决于实际写入的列和条件，不能只看动词是否不同。

项目选用 MyBatis 时，Mapper 负责业务 SQL，Repository 负责把 Mapper 结果转成持久化语义。迁移、测试初始化和基础设施接入是否使用 JDBC，由各自的所有者说明；不能把一种技术选型写成对所有数据库访问方式的普遍禁令。

## 查询

先确认调用方已掌握的事实，再决定是否需要联表。列表和分页查询应先限制数据范围、排序和数量，再按批次加载关联数据。跨表筛选、排序或同一快照读取确实需要数据库一次完成时，使用能够保持语义的 SQL。

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

预期影响一行的写入，应检查零行和多行分别意味着什么。零行可能表示资源不存在、条件已变化、允许的幂等结果或客户端版本过期，不能仅根据行数猜测业务含义。多行超出声明的写入应视为条件或数据错误。批量写入按实际集合大小和操作契约检查结果。

例如，`UPDATE` 同时检查账号 ID 与预期版本时，零行只说明这组条件没有命中。若调用方需要区分「账号不存在」与「版本过期」，Repository 还需依据已读取事实或补充查询得出结果；无法区分时不能把零行直接称作版本冲突。

集合写入优先使用有界的集合 SQL 或明确的批处理机制。选择批处理实现前，确认它何时返回真实影响行数、怎样处理部分失败，以及调用方是否依赖逐项结果。

例如，一次为多个账号增加相同关系，可以由一条带多组 `VALUES` 的 SQL 写入；在 Java 中循环调用单行 Mapper，数据库往返次数仍随账号数增长。使用 MyBatis `ExecutorType.BATCH` 时，单次调用在 flush 前不能提供最终影响行数；需要逐项判断写入结果的 Repository，不能把占位值当作真实成功数量。

以下两段分别表示「每个账号调用一次 Mapper」与「一次 Mapper 调用生成一条集合 SQL」，均为写入形状示意：

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

## 并发机制的选择

数据库约束保护单行与唯一性；条件更新和版本检查处理并发写入；幂等键处理可重放请求；分布式锁保护必须跨事务串行的资源和副作用；限流约束请求频率。选择机制时先写明竞争对象、失败结果和恢复方式，不把这些机制互相替代。

单条 SQL 可以原子领取候选并返回已更新记录时，优先把状态、版本和租约条件放进该 SQL。需要在同一事务中清理多张关联表或保持跨语句一致时，可以用 `FOR UPDATE SKIP LOCKED` 等行锁协调并发执行者；应说明锁顺序、批量上限及失败回滚。锁的选择取决于哪些操作必须共同完成，以及数据库条件能否决定竞争结果。

下面的 SQL 只示意待发送消息的 `PENDING` 分支。实际投递还要处理失败重试和租约到期，但候选读取与写入条件必须保持一致：

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

两个实例可以读到同一候选；只有满足当前版本和状态条件的实例取得 `RETURNING` 结果。这里的一条 SQL 已决定竞争结果，候选读取不需要额外加 `FOR UPDATE`。

清理任务历史使用多个 SQL，操作范围不同。以下代码取自一个现有的 Repository 方法，省略类定义和 import；它在同一事务中锁定有界的根记录，按关系顺序删除子记录，最后核对根记录删除数量：

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

对应的 Mapper XML 使用实际的应用隔离、状态和截止时间条件：

```xml
<select id="selectExpiredRunIdsForUpdate" resultType="long">
    SELECT id FROM job.job_run
    WHERE app_name = #{appName} AND status = 'SUCCEEDED'
      AND completed_at &lt; #{completedBefore}
    ORDER BY completed_at, id LIMIT #{limit}
    FOR UPDATE SKIP LOCKED
</select>
```

这段清理过程依赖多条删除语句共同提交或回滚。行锁让并发实例领取不同的根记录；它解决的问题与前一例的单条条件更新不同，不能从其中一个例子推出「所有领取都加锁」或「所有领取都不用锁」。

分布式锁应以稳定资源标识构造键，覆盖需要串行的事务提交和外部操作；持锁后重新读取状态。数据库条件和唯一约束已经足以产生明确竞争结果时，不叠加锁。定时任务、消息消费和重试必须考虑重复执行、租约到期、节点退出与接管。

HTTP `If-Match` 适用于调用方基于已读取的稳定资源快照进行编辑，且过期写入可能覆盖重要新内容的流程。状态命令、追加记录和允许后写覆盖的字段 Patch 通常依靠服务端当前状态、唯一约束或原子条件。是否使用 ETag 取决于资源和交互契约，不由数据库是否有版本列自动决定。

例如，用户读取一份完整配置后修改并提交整份内容，另一个人先改动配置时，旧快照可能覆盖新值。这个编辑流程需要让客户端提交版本条件，并在冲突后重新读取与确认。将一条通知标记为已读则由服务端判断当前状态并执行条件更新；表中即使有版本列，也不因此要求页面先读取 ETag。

## 缓存

缓存值应能从权威数据重新生成。Key 必须包含影响结果的租户、身份、语言或其他查询条件。写入与失效时机应避免回滚后错误清理，或允许旧值在事务提交前重新回填。

本地缓存只用于允许实例间短时间不一致的数据。缓存 Value 发生不兼容变化时，使用新名称、Key 版本或明确的旧值读取策略。权限、额度、唯一性和状态机判断不能只依赖单实例缓存。

例如，事务内修改账号状态后立即删缓存，另一个请求可能在事务提交前从数据库读到旧状态并重新填入缓存。提交后失效可以避免这一顺序；若要求事务提交与通知其他实例可靠衔接，还需要消息或持久任务记录失效动作。
