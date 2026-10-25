# 开发后端业务接口

本页以「为工作区文档增加摘要字段」为例，说明新增业务字段、局部更新和分页列表时需要改动的位置。适合修改 Sca4d Server 后端接口的开发者。

场景：工作区（Workspace）中的文档（Document）需要一个可选的摘要（`summary`）。成员（Member）可以修改摘要，也可以清空；文档列表要支持按更新时间排序和分页。

需要解决三个问题：

- 数据库、请求、响应各改哪里。
- 请求不提交 `summary` 与提交 `null` 的区别怎样保留到 SQL。
- 列表的分页和排序怎样声明，才不会把请求字符串拼进 SQL。

前置条件：已按[认证与 Session](/tutorials/authentication-and-session)取得 Token，并按[身份与权限](/tutorials/identity-and-permission)为文档接口配置了 Principal 选择和权限。

本页的 `Document`、`DocumentService`、`DocumentRepository`、`DocumentMapper`、`DocumentController` 等类型由具体应用定义。

## 实现步骤

各层职责如下。路径相对于应用模块的 `src/main/`。

| 层 | 位置 | 本例的改动 |
|---|---|---|
| 迁移 | `resources/db/migration/` | 增加 `summary` 列 |
| 请求与响应 | `java/.../request/`、`java/.../view/` | 声明输入校验和返回字段 |
| Controller | `java/.../controllers/` | 路由、当前 Principal、权限、审计、响应转换 |
| Service | `java/.../service/` | 业务规则 |
| Repository | `java/.../repository/` | 把意图转换为 Patch，并解释影响行数 |
| Mapper | `java/.../mapper/`、`resources/mapper/app/` | 明确列出列的 SQL |

### 1. 增加数据库字段

已有数据库通过新的 Flyway 迁移增加字段，文件放在 `resources/db/migration/`，命名为 `V<版本>__<说明>.sql`。

```sql
ALTER TABLE public.document
    ADD COLUMN summary VARCHAR(500);

COMMENT ON COLUMN public.document.summary IS '文档摘要；NULL 表示没有摘要';
```

先确定字段类型、长度、空值含义、默认值和允许修改的条件。增加非空或唯一约束前，检查旧记录怎样取得有效值。迁移要求见[数据库规范](/standards/06_database)。

### 2. 声明请求和响应

`summary` 有三种意图：保留原值、写入新值、清空。请求对象用「是否提供」标志区分保留与清空，再转换为 `Patch`。

```java
public final class ChangeDocumentRequest {

    @Size(max = 500)
    private @Nullable String summary;

    private boolean summaryProvided;

    public @Nullable String getSummary() {
        return summary;
    }

    public void setSummary(@Nullable String summary) {
        this.summary = summary;
        this.summaryProvided = true; // Jackson 只在 JSON 含该字段时调用 setter
    }

    /** 未提交时保留，提交 null 时清空，提交文本时写入。 */
    public Patch<String> getSummaryPatch() {
        if (!summaryProvided) {
            return Patch.ignore();
        }
        return summary == null ? Patch.clear() : Patch.set(summary);
    }
}
```

响应对象声明返回字段。需要乐观并发的资源，在响应类型上用 `@ETag` 标记一个返回 `Version` 的方法，Core Spring 把它发布为强 ETag。

```java
public final class DocumentView {

    private final long id;

    private final @Nullable String summary;

    private final Version version;

    // 构造方法与 getter 省略

    @ETag
    public Version revision() {
        return version;
    }
}
```

`@ETag` 返回的版本必须覆盖当前实例的全部可观察字段。同一类型既用于固定资源又用于列表元素时，列表实例的 `revision()` 返回 `null`，不发布 ETag。

### 3. 编写 Controller

Controller 选择当前 Principal、声明权限和审计、要求 `If-Match`。

```java
@PatchMapping("/{documentId}")
@IfMatch
@RequirePermission(operation = WorkspaceOperations.DOCUMENTS_WRITE_VALUE)
@RequirePermission(
        resource = "#documentId",
        resourceType = Document.RESOURCE_TYPE_VALUE,
        action = Action.UPDATE_VALUE)
@Audit(
        operation = "WORKSPACE/DOCUMENT/UPDATE",
        target = "#documentId",
        resourceType = Document.RESOURCE_TYPE_VALUE)
public ApiResponse<DocumentView> change(
        @PathVariable long workspaceId,
        @PathVariable long documentId,
        @CurrentPrincipal WorkspaceMemberPrincipal member,
        @RequestBody @Valid ChangeDocumentRequest request) {
    Document updated = documents.change(member, documentId, request.getSummaryPatch());
    return ApiResponse.ok(DocumentView.from(updated));
}
```

`WorkspaceOperations.DOCUMENTS_WRITE_VALUE` 按[身份与权限](/tutorials/identity-and-permission)的方式声明。类级别的 `@CurrentPrincipal(selection = ...)` 同样见该页。

`@CurrentPrincipal` 参数由服务端填充，客户端不通过请求体指定修改的是谁。`@Audit` 只保存允许记录的标识和字段变化，不写入密码、验证码或完整个人资料。

### 4. 在 Repository 解释影响行数

`@IfMatch` 把请求中的强 ETag 绑定到当前请求。Repository 通过 `VersionConditions` 取得条件，并把预期版本交给 Mapper。

```java
// DocumentRepository
public void changeSummary(long workspaceId, long documentId, Patch<String> summary) {
    Document current = findInWorkspace(workspaceId, documentId)
            .orElseThrow(() -> new BusinessException(CoreErrorCode.NOT_FOUND));
    VersionCondition condition = versions.resolve(new Version(current.version()));
    AffectedRowCounts.requireSingle(
            mapper.updateSummary(documentId, new DocumentSummaryUpdate(summary), condition.expected()),
            condition.whenZero(),
            "文档摘要");
}
```

`DocumentSummaryUpdate` 是只保存 `Patch<String> summary` 的参数对象，由具体应用定义。只更新一个字段时，Mapper 方法也可以直接接收具名 `Patch` 参数。

Repository 在调用 Mapper 前拒绝所有字段均为 `IGNORE` 的更新，避免生成没有列的 `UPDATE`。

### 5. 在 Mapper 明确列出列

```java
@CheckReturnValue
int updateSummary(
        @Param("documentId") long documentId,
        @Param("update") DocumentSummaryUpdate update,
        @Param("expectedVersion") Version expectedVersion);
```

```xml
<update id="updateSummary">
    UPDATE document
    <set>
        <choose>
            <when test="update.summary.set">
                summary = #{update.summary.value},
            </when>
            <when test="update.summary.clear">
                summary = NULL,
            </when>
        </choose>
        version = version + 1
    </set>
    WHERE id = #{documentId}
    AND version = #{expectedVersion}
</update>
```

Patch 不生成 SQL，也不通过反射推断列名。Mapper XML 明确写出每个允许修改的列、状态条件、版本条件和 `WHERE` 条件。`IGNORE` 状态不生成列赋值。
Principal 选择、权限、审计和 `If-Match` 在进入 Service 前按固定顺序完成，Service 只处理业务规则。

### 6. 增加分页列表

所有分页接口接受 `page` 和 `size`：

| 参数 | 默认值 | 限制 |
|---|---:|---|
| `page` | 1 | 不小于 1 |
| `size` | `sca4dj.core.pagination.default-size`，默认 20 | 不大于全局上限 `sca4dj.core.pagination.max-size`（默认 200）或当前接口声明的上限 |

支持动态排序的接口另外接受可以重复的 `sort`：

```http
GET /workspaces/101/documents?page=2&size=20&status=PUBLISHED&sort=TITLE,ASC&sort=UPDATED_AT,DESC
```

服务端按参数出现顺序应用排序。字段名和方向区分大小写，方向只能是 `ASC` 或 `DESC`。`sca4dj.core.sort-max-fields` 控制单次请求的字段数量，默认 5，配置范围是 1 到 20。

没有提交 `sort` 时使用接口声明的默认排序。动态排序接口不接受旧的 `sortBy` 和 `sortDirection`，出现任一旧参数都会返回 400。

`Sort` 内置 `Sort.CREATED_AT`、`Sort.UPDATED_AT`、`Sort.NAME`（ASCII 稳定名称，不表示本地化展示名称）和 `Sort.SERIAL`（数据库标识、序号或同类稳定流水字段）。业务字段通过 `Sort.of(...)` 创建，只接受大写协议标识符，返回的 `Sort` 不包含数据库列或 SQL。

`ListDefinition` 是当前接口的字段白名单。默认排序必须使用已经登记的字段，同一个字段不能重复登记或重复排序。

```java
private static final Sort TITLE = Sort.of("TITLE");

private static final ListDefinition DOCUMENT_LIST = ListDefinition.builder()
        .fields(Sort.CREATED_AT, Sort.UPDATED_AT, TITLE)
        .defaultSort(Sort.UPDATED_AT.desc())
        .maxSize(100)
        .build();
```

Controller 显式绑定业务筛选参数，再用列表声明解析公共参数：

```java
@GetMapping
@RequirePermission(operation = WorkspaceOperations.DOCUMENTS_READ_VALUE)
public ApiResponse<Page<DocumentView>> find(
        @PathVariable long workspaceId,
        @RequestParam @Nullable String search,
        @RequestParam @Nullable DocumentStatus status, // 由具体应用定义
        @NotNull ListParameters parameters) {
    DocumentQuery query = new DocumentQuery(
            workspaceId,
            search,
            status,
            parameters.resolve(DOCUMENT_LIST));
    return ApiResponse.ok(documents.find(query).map(DocumentView::from));
}
```

`ListParameters` 只存在于 Controller 中。`resolve(...)` 完成后，`DocumentQuery`（由具体应用定义）保存业务筛选条件和不含外部字符串的 `ListRequest`。固定排序接口直接接收 `PageRequest`。需要更小的页大小上限时，使用 `@PageOptions(maxSize = 100)`。

业务 Query 负责自己的筛选规则，例如空白搜索词、标识正数、两个字段必须同时提供，或时间下界必须早于上界。公共列表类型不解释这些业务条件。

Repository 使用同一个 Query 执行 count 和当前页查询，并返回 `Page<T>`。没有命中记录时可以跳过 rows 查询。超过末页时返回空 `items` 和不变的 `total`。

```java
// DocumentRepository
public Page<Document> find(DocumentQuery query) {
    long total = mapper.count(query);
    List<Document> rows = total == 0 ? List.of() : mapper.find(query);
    return new Page<>(rows, query.list().getPage(), total);
}
```

Mapper 的筛选 SQL 由 count 和 rows 共用，排序列逐项显式映射：

```xml
ORDER BY
<foreach collection="query.list.sort" item="order" separator=",">
    <choose>
        <when test="order.field.name() == 'CREATED_AT'">d.created_at</when>
        <when test="order.field.name() == 'UPDATED_AT'">d.updated_at</when>
        <when test="order.field.name() == 'TITLE'">d.title</when>
    </choose>
    <include refid="com.sca4d.core.spring.persistence.mybatis.SortSql.direction"/>
</foreach>,
d.id DESC
OFFSET #{query.list.page.offset} LIMIT #{query.list.page.size}
```

请求字段不会作为 SQL 片段传入 Mapper。每个 Mapper 仍需明确列出允许排序的数据库列，并追加能够消除相同字段值歧义的稳定排序。

`Page<T>` 的 JSON 结构固定为：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": "0"
}
```

`total` 表示满足全部筛选和数据范围条件的记录总数，不是当前页数量。Java `long` 在 HTTP JSON 中使用十进制字符串，`page` 和 `size` 保持数字。

## 验证

前置条件：已取得 Token，Token 对应的 Account 是工作区 `101` 的成员，角色已授予读写文档的 Operation。

1. 读取文档，记录响应头 `ETag`（例如 `"3"`）。

```http
GET /api/workspaces/101/documents/9001
Authorization: Bearer <accessToken>
```

2. 写入摘要。

```http
PATCH /api/workspaces/101/documents/9001
Authorization: Bearer <accessToken>
If-Match: "3"
Content-Type: application/json

{"summary": "季度回顾"}
```

预期：200，`data.summary` 为「季度回顾」，`ETag` 递增为 `"4"`。

3. 提交 `{}`，预期摘要保持不变。提交 `{"summary": null}`，预期摘要被清空。每次请求都使用最近一次响应的 `ETag`。
4. 使用旧的 `If-Match: "3"` 再提交一次，预期 412。
5. 读取列表，预期 `data.items`、`page`、`size` 和字符串形式的 `total`。

```http
GET /api/workspaces/101/documents?page=1&size=20&sort=UPDATED_AT,DESC
Authorization: Bearer <accessToken>
```

测试至少覆盖：有效输入、超长摘要、保留、写入、清空、版本冲突、无权限成员，以及分页越界。涉及事务时，覆盖回滚后数据是否保持一致。

只修改本应用时，先编译 `:apps:<应用名>-server:compileJava`，再运行直接相关测试。检查范围见[测试规范](/standards/10_testing)，资源限制以仓库构建指令为准。

## 失败结果与限制

| 条件 | 结果 |
|---|---|
| 请求字段不满足校验（例如 `summary` 超过 500 个字符） | 400，`HttpErrorCode.BAD_REQUEST`（`1100`），响应数据为各字段的拒绝原因 |
| 页码、页大小、排序格式、字段、方向、重复字段或字段数量无效 | 400，`VALIDATION_FAILED`（`1000`） |
| 动态排序接口收到 `sortBy` 或 `sortDirection` | 400 |
| `@IfMatch` 接口缺少 `If-Match` 头 | 428，`PRECONDITION_REQUIRED`（`1105`） |
| `If-Match` 有多个值、是弱 ETag、通配符，或不是整数 | 412，`PRECONDITION_FAILED`（`1106`） |
| `If-Match` 版本与当前版本不一致，Mapper 影响 0 行 | 412，抛出 `VersionPreconditionFailedException` |
| 内部对象的乐观版本已经变化（`OPTIMISTIC_LOCK` 策略） | 抛出 `OptimisticLockException` |
| 影响行数不是预期值且不属于上述零行策略 | 抛出 `AffectedRowsMismatchException` |
| 当前 Principal 没有权限 | 403，`PERMISSION_DENIED`（`1002`） |
| 目标文档不存在 | 404，`NOT_FOUND`（`1003`） |

- `Patch.set(null)` 抛出 `NullPointerException`。在 `IGNORE` 或 `CLEAR` 状态读取 `value()` 抛出 `IllegalStateException`。
- `Patch.choose()` 中两个或更多条件同时命中时抛出 `IllegalStateException`，不按声明先后静默覆盖。`Patch.resolve(...)` 的回调返回 `null` 时立即失败。
- Patch 只表达字段怎样写入，不代替 Controller 的 HTTP 校验、实体构造入口的字段限制，也不代替 Service 的跨字段和业务规则。
- Patch 不依赖 HTTP Request、Jackson、Jakarta Validation、Spring 或 MyBatis。
- 增加其他资源的完整编辑接口时，按[HTTP 并发约定](/reference/http/common#etag、if-match-与修改冲突)判断是否需要 ETag。
- 与 Account 登录标识关联的字段（例如邮箱）不放在业务表中修改。修改 Account 时调用 IAM 公开接口，并使两处在同一事务中提交或回滚。

## 相关页面

- [接入身份与权限](/tutorials/identity-and-permission)
- [接入认证与 Session](/tutorials/authentication-and-session)
- [编写与运行测试](/tutorials/testing)
- [持久化规范](/standards/05_persistence)
- [HTTP 通用约定](/reference/http/common)
