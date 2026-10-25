# 字典与设置

本页以「工作区文档状态」和「工作区成员数量上限」为例，说明如何新增一个字典和一项系统设置，并在运行期读取、修改和恢复默认值。适合需要调整系统设置、新增字典或 Setting 的开发者。

场景：工作区（Workspace）的文档（Document）有状态，可选值由下拉框提供。每个工作区的成员（Member）数量不能超过一个上限，管理员可以在管理端调整该上限。

需要解决两个问题：

- 供业务选择的选项（文档状态）怎样定义，并允许在不改代码的前提下扩展。
- 一项可在线调整的类型化配置（成员上限）怎样定义、读取和修改。

Dictionary 管理供业务选择的选项。Setting 直接读取某个系统、账号或业务身份的一项类型化配置。Setting 在构造时声明读取器、Codec、所属、继承和写入目标。数据库、ENV、文件和 JAR 资源是不同的读取或写入实现。

本页的 `WorkspaceDocumentStatus`、`WorkspaceSettings`、`WorkspaceMemberService` 等类型由具体应用定义。

## 实现步骤

### 1. 定义字典

字典由实现 `Dictionary` 的 Java 枚举定义。`DictionaryScanner` 扫描 `com.sca4d` 包和自动配置包下的枚举，应用不需要另行注册。

```java
public enum WorkspaceDocumentStatus implements Dictionary {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布");

    private final String value;

    private final String description;

    WorkspaceDocumentStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public String description() {
        return description;
    }

    /** 同一枚举的所有常量必须返回相同的键。 */
    @Override
    public String dictionaryKey() {
        return "workspace.document-status";
    }

    /** 返回 true 时，才允许在数据库中增加枚举之外的选项。 */
    @Override
    public boolean allowsDatabaseOptions() {
        return true;
    }
}
```

`labelKey()` 默认返回 `dictionary.<dictionaryKey>.<value 的小写连字符形式>`，本例为 `dictionary.workspace.document-status.draft`。代码选项使用国际化消息键，需要在消息资源中提供对应文本。

代码字典默认封闭，`allowsDatabaseOptions()` 默认返回 `false`。动态字典不需要 Java 枚举，但必须先存在 `sys_dictionary` 行，才能写入选项。

读取时通过 `Dictionaries`：

```java
Set<String> keys = dictionaries.keys();
List<DictionaryOption> options = dictionaries.options("workspace.document-status");
```

`DictionaryOption` 提供 `value()`、`label()`、`labelText()`、`enabled()`、`sortOrder()` 和 `codeDefined()`。结果先按 `sortOrder` 排序，再按值排序。禁用项仍保留在读取结果中，调用方不能把它用于新的业务输入。

### 2. 定义系统设置

先根据谁读取和修改值，选择定义类型：

- 系统管理页维护的值，使用 `SystemSetting`。
- 后端按 owner 执行业务规则的值，使用 `LogicSetting`。
- 前端按当前 owner 保存完整界面状态的值，使用 `PreferenceSetting`。

成员上限由管理员在系统管理页维护，选用 `SystemSetting`。所有 Setting 都声明稳定的 TreeCode、`SettingCodec`、默认值、Pipeline 和敏感标记。`SystemSetting` 另外声明分组、标签和说明消息键、初始化 ENV 后缀与展示顺序。

```java
@Configuration(proxyBeanMethods = false)
public class WorkspaceSettings {

    /** 工作区允许的成员数量上限，默认 50，范围 1 到 1000。 */
    public final SystemSetting<Integer> MEMBER_LIMIT;

    public WorkspaceSettings(SettingReader<String> reader, SettingWriter<String> writer) {
        this.MEMBER_LIMIT = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("WORKSPACE:MEMBER_LIMIT"),
                WorkspaceSettingSections.WORKSPACE, // 由具体应用定义，构造方式见 ProductSettings
                "setting.workspace.member-limit",
                "setting.workspace.member-limit.description",
                "WORKSPACE_MEMBER_LIMIT",
                50,
                SettingCodec.positiveInteger(),
                Pipeline.of(
                        PipelineStage.of(
                                "workspace.member-limit.maximum",
                                Integer.class,
                                Integer.class,
                                value -> {
                                    if (value > 1000) {
                                        throw new IllegalArgumentException("成员上限不能超过 1000");
                                    }
                                    return value;
                                })),
                false,
                100);
    }

    @Bean
    SystemSetting<Integer> workspaceMemberLimitSetting() {
        return MEMBER_LIMIT;
    }
}
```

`SettingCodec` 内置 `text(int)`、`positiveInteger()`、`positiveLong()`、`positiveDuration()` 和 `jsonObject(int)`。Codec 负责内容结构版本和外部表示，Pipeline 对完整 Java 值执行与 owner 无关的规范化和校验。默认值在定义创建时经过 Pipeline 和 Codec 往返校验，非法定义会阻止应用启动。
Codec、默认值和校验都在定义时声明，调用方只使用容器中注册的同一个定义对象。

TreeCode 使用大写节点、冒号层级和下划线节点。存储键只在 Flyway 迁移和数据库访问处使用字符串。

把定义作为 Spring Bean 注册。`SettingCatalog` 会拒绝重复 TreeCode、重复 SystemSetting ENV、冲突分组以及不符合格式的元数据。调用设置时，必须使用容器注册的同一个定义对象，不能临时构造同编码的替代实例。

业务代码直接调用定义的 `get`、`inspect`、`replace` 和 `reset`，不依赖统一设置执行器：

```java
@Service
public class WorkspaceMemberService {

    private final WorkspaceSettings settings;

    public WorkspaceMemberService(WorkspaceSettings settings) {
        this.settings = settings;
    }

    public void addMember(long workspaceId, long principalId) {
        int limit = settings.MEMBER_LIMIT.get();
        // 超过 limit 时拒绝，具体错误码由具体应用定义
    }
}
```

`PersonalSetting` 的子类 `LogicSetting` 与 `PreferenceSetting` 的构造不含分组、消息键和 ENV：

```java
new LogicSetting<>(reader, writer, code, defaultValue, codec, pipeline, secret);
new PreferenceSetting<>(reader, writer, code, defaultValue, codec, pipeline);
```

读取时使用 `get(SettingOwner owner)`，由调用方提供 Account、Principal 或 Resource owner。`PreferenceSetting` 不能是敏感值，因为前端必须读取完整 JSON。

### 3. 修改和恢复系统设置

路由由具体应用的 Controller 暴露。本仓库的示例应用以 `SystemSettingController` 提供下面的接口，并要求 Operation `SYSTEM:SETTINGS:READ` 与 `SYSTEM:SETTINGS:MANAGE`。路径以应用 HTTP 参考为准，通过 Gateway 调用时加 `/api` 前缀并携带 Token。

1. 读取 `GET /system/settings`，找到 `WORKSPACE:MEMBER_LIMIT` 及其当前 `version`。
2. 使用读取到的版本提交新值：

```http
PATCH /system/settings/WORKSPACE:MEMBER_LIMIT
If-Match: "<读取到的版本>"
Content-Type: application/json

{
  "value": 100
}
```

3. 写入后重新读取。响应应返回新值和递增的版本。

恢复 Java 默认值时，用最新版本调用 `DELETE /system/settings/WORKSPACE:MEMBER_LIMIT` 并携带同样的 `If-Match` 头。结果回到 Java 默认值。

系统设置页面先请求 `GET /system/settings`。响应只包含已注册的 SystemSetting，并按分组排序。每个设置的 `value` 和 `defaultValue` 是 JSON 值。`version` 是后续写入使用的行版本。敏感值不返回内容。

### 4. 读取和修改个人偏好

个人偏好使用 `GET|PATCH|DELETE /me/preferences/{key}`。服务端从当前 Session 选择 Platform Principal，客户端不提交 owner。

`GET` 返回完整 JSON 和强 ETag。`PATCH` 的正文同样是 `{ "value": ... }`。`PATCH` 与 `DELETE` 都必须使用最近读取的 `If-Match`。发生 412 时，先重新读取，再由具体前端决定保留本地完整值，还是采用服务端值。

`PreferenceController` 只读取 `SettingCatalog` 中已注册的 `PreferenceSetting`，客户端不能切换 owner，也不能访问 `LogicSetting`。

`LogicSetting` 没有通用 HTTP 管理接口。具体应用的 Controller 调用领域 Service，由 Service 检查 owner 和业务限制后，再使用对应 Setting。例如账号认证策略保存于 `sys_setting`，仍要检查当前 Credential 和认证因素，不能让通用键值接口直接覆盖。

### 5. 用 ENV 初始化系统设置

只有 `SystemSetting` 参与 ENV 初始化。先在定义中声明 ENV 后缀（本例为 `WORKSPACE_MEMBER_LIMIT`，完整变量名由部署的前缀规则决定，见运行配置参考），再只对承担初始化职责的 JVM 设置 `sca4dj.bootstrap.enabled=true`。普通运行节点不解释 Setting ENV，也不会因本机 ENV 不同而得到另一份 System owner 数据。

初始化节点支持三种动作：

| 输入 | 结果 |
| --- | --- |
| `<ENV>_INIT=true` 与非空 `<ENV>` | 数据库没有该 SystemSetting 行时写入一次，之后允许管理端修改 |
| `<ENV>_SYNC=true` 与非空 `<ENV>` | 每次启动覆盖同一个 System owner 行，并标记为管理端只读 |
| `<ENV>_SYNC=false` | 保留最后值，解除只读标记 |

例如，固定成员上限为 100，并禁止管理端修改：

```properties
WORKSPACE_MEMBER_LIMIT=100
WORKSPACE_MEMBER_LIMIT_SYNC=true
```

上例省略了前缀。全部定义在同一数据库事务和初始化锁中处理。任一 ENV、Codec、SYSTEM Subject ID 或 `SecretKeyring` 失败，都不会留下部分更新。

多个 JVM 共享由 `sca4dj.iam.system-subject-id` 确定的 SYSTEM owner。部署不同代码版本时，先保证所有仍在运行的版本都能读取目标 Codec 版本，再让唯一的初始化节点同步新值。不能依赖 JVM 标识创建各自的副本。

需要不同实例使用不同连接地址、凭据或启动条件时，继续使用部署配置，不要改成 SystemSetting。

运行配置键和完整失败结果见 [Sca4d Server Setting 初始化节点](/reference/runtime/sca4d-server#setting-初始化节点)。

### 6. 读取启动期配置常量

进程运行期间不再改变的公开协议名称使用 `ConfiguredConstants`。声明字段时提供配置键和代码默认值。Spring 加载配置后、创建 Bean 前读取外部覆盖值，并通过 Setting 的文本 Codec 校验。

```java
public static final String SECRET_HEADER = ConfiguredConstants.text(
        "sca4dj.gateway.protocol.secret-header", "X-Gateway-Secret");

request.getHeader(GatewayConstants.SECRET_HEADER);
```

调用代码直接读取 `static final` 字段，不需要注入配置对象。修改部署配置后必须重启。

该字段在 Java 中不是编译期常量，因此路由注解使用同一个配置键的 Spring 占位符。`ConfiguredConstants` 不读写 `sys_setting`，也不参加系统设置管理接口。成员上限等可在线调整的值继续使用 `SystemSetting`。

配置可以使用规范属性键，也可以在进程环境或本地 Properties 格式的 `.env` 文件中使用对应的大写下划线名称，例如 `SCA4DJ_GATEWAY_PROTOCOL_SECRET_HEADER`。两种名称同时出现且值不一致时启动失败。两者表示同一项配置，不是新旧配置别名。

### 7. 修改已有设置的内容结构

修改现有值结构时，新增版本 Codec，并为每个相邻版本登记 `Migratable`。当前版本 Codec 写入新结构，旧版本 Codec 只负责读取或迁移。不要在普通请求中隐式写回。没有登记迁移步骤时，迁移命令在缺少中间版本处失败。

数据库行中的 `schema_version` 选择已登记的历史 Codec，未注册的版本直接失败。`VersionedCodecMigrator` 只执行明确的内容迁移，Setting 正常读取不会隐式改写数据库。

## 验证

前置条件：服务已启动，使用具有 `SYSTEM:SETTINGS:READ` 与 `SYSTEM:SETTINGS:MANAGE` 的账号登录。

1. `GET /api/system/settings`，预期响应包含 `WORKSPACE:MEMBER_LIMIT`，`value` 与 `defaultValue` 均为 `50`，并给出 `version`。
2. 按上一节提交 `{"value": 100}`，预期 `value` 为 `100`，`version` 递增。
3. 用旧版本再次提交，预期 412。
4. 提交 `{"value": 2000}`，预期被 Pipeline 拒绝，值保持为 `100`。
5. `DELETE` 恢复默认值，预期 `value` 回到 `50`。
6. 添加成员接口在成员数量达到 `limit` 时被拒绝。
7. 如果枚举允许数据库选项，调用 `GET /system/dictionaries`，预期响应包含键 `workspace.document-status` 及两个代码选项。

## 失败结果与限制

| 条件 | 结果 |
| --- | --- |
| 修改或恢复时缺少 `If-Match` | 428，`PRECONDITION_REQUIRED`（`1105`） |
| 版本与当前行不一致 | 412，`PRECONDITION_FAILED`（`1106`） |
| 设置受 ENV 持续同步控制 | 409，`CONFLICT`（`1004`）。底层抛出 `SettingReadOnlyException`，先把 `<ENV>_SYNC` 设为 `false` 并重启初始化节点 |
| 未知 key，或不在应用允许管理的分组内 | 404，`NOT_FOUND`（`1003`） |
| 值无法被 Codec 解码，或被 Pipeline 拒绝 | 不写入 |
| 缺少 `SYSTEM:SETTINGS:MANAGE` | 403，`PERMISSION_DENIED`（`1002`） |

- 只有 `<ENV>` 而没有 `_INIT` 或 `_SYNC` 时，初始化抛出 `IllegalStateException`。`_INIT` 与 `_SYNC` 同时存在、`_INIT` 设为 `false`、标记值不是 `true` 或 `false` 时，同样抛出该异常。
- 常量类在配置来源就绪前被初始化，会使进程启动失败。
- `Dictionary` 只能由枚举实现。同一枚举的常量返回不同的 `dictionaryKey()`、重复键，或枚举定义不合法时，`DictionaryRegistry` 抛出异常，应用启动失败。
- 调用 `Setting.defaultValue()` 时没有默认值，抛出 `IllegalStateException`。
- 数据库和设置文件中的 `encryption_context_version` 必须对应目录中已登记的版本，未登记的版本直接报错，不尝试旧键名或旧加密上下文。
- 字典写入前检查代码键、选项扩展规则和乐观锁版本。写入失败时不产生部分更新。
- `has_value=false` 的行是恢复默认值后的 tombstone（删除标记）。它保留递增的行版本，避免旧客户端用过期 ETag 覆盖新状态。owner 已删除时，负责该 owner 生命周期的 Service 使用 `erase` 物理清理对应设置。
- 短信模板、邮件模板等可以独立创建、删除、审计、授权或排序的业务对象，应拥有自己的 Repository 和表，不能改写为 Setting 键值。数据库地址、连接凭据、模块启用条件和启动装配参数仍是运行配置。

## 相关页面

- [字典与设置功能说明](/capabilities/data-configuration/dictionaries-and-settings)
- [接入认证与 Session](/tutorials/authentication-and-session)
- [Sca4d Server HTTP API](/reference/http/sca4d-server)
- [Setting API](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/setting/package-summary.html)
- [VersionedCodec](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/codec/VersionedCodec.html)
