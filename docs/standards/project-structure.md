# Sca4d-J 项目工程约定

## 生效范围

本页规定 Sca4d-J 仓库的顶层目录、模块边界和公开契约。类和方法的通用要求见[核心编码原则](/standards/principles)，分层与持久化细节见[分层与 API](/standards/layers-and-api)和 [SQL 与持久化分层](/standards/persistence)。

开始新功能前，先在[项目结构](/home/project-structure)和[应用组装](/tutorials/assemble-application)中确认现有项目和依赖。

## 顶层目录

| 位置 | 负责内容 | 依赖要求 |
|---|---|---|
| `core/sca4dj-core-api` | 长期使用的公开接口、身份类型、调用生命周期、并发和消息等基础契约 | 尽量不依赖 Spring，不依赖具体模块或 App |
| `core/sca4dj-core-spring` | 把 Core API 接入 Spring MVC、AOP、Redis、MyBatis 和自动配置 | 可以依赖 Core API，不实现 IAM、认证方法或具体应用流程 |
| `modules/` | IAM、Auth、Audit、Messaging、Notification、Job、Scheduling、Web Realtime、IoT 和 Storage 等可选功能 | 依赖 Core 的公开契约；模块间不使用对方的 Mapper、Repository、数据库行类型或内部包 |
| `apps/` | 选择模块、配置运行环境，并完成具体应用的 HTTP 接口和跨模块业务 | 可以依赖 Core 和模块；不能成为 Core 或模块的依赖 |
| `gateway/` | 可独立部署的 HTTP、SSE、WebSocket 和回调转发入口 | 不读取 App 数据库，不执行业务权限和状态机 |
| `frontends/` | 可替换的管理端示例、前端契约和页面代码 | 不成为后端业务规则的依据 |
| `tools/` | Gradle 插件、生成任务、质量检查和主构建接入 | 不包含具体应用的业务规则 |

依赖方向是 `apps -> modules -> core`。模块之间确实需要协作时，使用 Core 或所属模块已经公开的稳定接口。只服务于当前实现步骤的方法，不应升级为跨模块 API。

## 模块边界

一个 Gradle 模块应当表示可以单独选择、替换或运行的功能。只有文件较多、希望缩短 import、或预想以后可能替换，都不足以新建模块。

新建模块前必须说明：

- 现有模块为什么不应负责这些代码；
- 该功能是否需要单独裁切、替换或部署；
- 它需要公开哪些稳定契约，不公开哪些实现细节；
- 它会维护哪些数据、自动配置和运行资源。

公共代码不能因为被多个模块使用就自动放进 Core。只有当它表达的概念适用于多个模块、接口可以长期保持稳定，且不需要引用具体模块类型时，才属于 Core。

## 公开接口和实现

公开 API 使用调用方能够理解的名称、参数、返回值和失败结果。不向上层暴露 MyBatis 参数、SQL 影响行数、Spring 容器查找结果、HTTP Request/View 或模块内部类。

只有在下列情况之一成立时，才为 Service 或其他类型增加 Java `interface`：

- 已经存在两个或以上具有相同契约的实现；
- 调用方不应依赖具体模块或技术实现；
- 该接口是明确的扩展点，具体应用会提供实现。

只有一个实现，且调用方与它属于同一模块时，直接使用命名清楚的普通类。不为形式完整保留一对同名 Service 接口和 `Impl` 类。

## Controller、Service、Repository 和 Mapper

后端业务代码使用 `Controller -> Service -> Repository -> Mapper` 的调用和依赖方向。这是职责约束，不是要求每个功能机械地创建四个同名类。

- Controller 处理路由、请求绑定、权限声明和响应转换，不开启业务事务，不组合 Repository 写入。
- Service 完成一个完整业务动作，负责规则、事务、状态变化和副作用，不理解 SQL、数据库列和影响行数。
- Repository 提供稳定的数据读写语义，隐藏 Mapper、XML、数据库行类型、分页结构和写入结果。
- Mapper 表达最小、可组合、可直接审查的 SQL 读写形状，不决定业务状态机和业务异常。

完成一个层级后，上层应当少理解一组下层细节。同名、同参数、同返回值的直接转发没有形成新的职责，应当删除、合并或转换结果。

## 命名与类型

- 类名表达业务对象或稳定技术职责。
- HTTP 输入可以使用 `Request`，HTTP 输出可以使用 `View`，数据库行投影可以使用 `Row` 或 `State`；后缀必须与类型所在边界一致。
- `Result`、`Query`、`Snapshot`、`Payload` 只在它们分别表示一次操作结果、稳定查询、时点快照或消息内容时使用。
- 不默认添加 `DTO`、`VO`、`BO`、`PO`、`Data`、`Info`、`Context` 或 `Manager`。
- 项目不使用 Java `record`，生产代码、测试、文档和示例都使用名称清楚的普通类。
- 系统定义且取值有限的状态、类型、模式和操作使用枚举或专用值类型，不在 Java 中退化成字符串。
- Java `long` 和 `Long` 进入浏览器后使用十进制字符串，不转换为 JavaScript `number`。

更完整的包结构、`Util` 和 `Helper` 规则见[代码结构与分包](/standards/naming-and-code-structure)。

## 现有公共入口

已有公共接口能够表达目标行为时，直接使用它，不建立平行入口。常见例子包括：

- 使用 `AuthenticationAttempts`、`AuthenticationMethod` 和 `SessionLifecycle` 完成认证与 Session；
- 使用 `Call`、`PermissionPolicy` 和 `ResourceScope` 传递一次调用的身份和权限结果；
- 使用 `MessageBus` 发布业务消息，不在业务代码中操作 RabbitMQ 或 Outbox 表；
- 使用 `WebRealtimePublisher` 发布浏览器短暂通知，不操作 SSE emitter、WebSocket session 或本地连接表；
- 使用 `FileService` 管理文件状态，使用 `FileStorage` 实现字节存储，业务表不保存 MinIO bucket、object key 或本地路径；
- 使用 Spring 的类型化配置、事务、消息转换和生命周期，使用 MyBatis 的 Mapper 与 XML，使用 Jackson 公共配置，不包装等价 API。

新入口只有在现有公开契约无法表达一个长期存在的需求时才成立。增加前说明缺少的具体行为、调用方和失败结果，不以“解耦”或“方便扩展”作为唯一理由。

## 数据模型

- 表和关系由负责该业务数据的模块维护，具体应用的资料与流程不追加到公共模块表中。
- 可以从稳定数据直接计算的数量、标记和页面临时状态默认不持久化。确实需要持久化时，同时设计更新、失效、修复和验证方法。
- 逻辑删除只用于需要恢复、追溯、保持引用或隔离唯一键复用的数据。可重建关系、临时数据和只追加记录不默认逻辑删除。
- 需要防止旧快照覆盖新内容的 HTTP 编辑接口使用强 `If-Match`，版本失效返回 412，缺少前置条件返回 428；内部乐观锁竞争返回 409。采用条件见[并发规则](/standards/persistence#并发规则)。
- 分布式锁只保护跨请求、跨节点且必须串行的副作用，不代替原子 SQL、乐观锁和幂等设计。

## 代码、测试与文档

每次修改保持一套当前契约。类型、路由、配置键、数据库编码或错误结果改变时，同步处理直接调用方、测试、迁移和负责完整说明它的文档。已经确认不再使用的旧路由、字段、转发方法和兼容分支直接删除。

代码注释按[注释与成品文档](/standards/comments-and-logging)编写，日志按[应用日志](/standards/comments-and-logging)编写，项目文档按[文档规则](/standards/documentation)分类和维护。

## 验证

验证从受影响的公开行为和失败风险出发，不使用固定全仓命令套餐。

- Java 签名或逻辑变化时，先编译直接受影响的 Gradle 项目，再运行与目标行为直接相关的测试。
- 公开契约跨越多个模块时，增加直接消费项目的编译和测试。
- Spring 自动配置、条件装配或启动流程变化时，运行最小上下文或启动检查；普通单元测试不能代替装配证据。
- SQL 迁移变化时，在隔离数据库中验证新库和已有库的受影响路径。
- 前端契约、状态或页面变化时，运行对应 workspace 的类型检查和相关核心业务测试。页面效果由开发者人工验收；AI 只在任务明确要求时启动浏览器或 E2E。
- 只修改 Markdown 正文或注释时，不运行 Java 编译、前端构建和产品测试。

每次修改都完整重读改动文件，检查最终 diff 和 `git diff --check`。格式化、构建和专项命令见[格式化与构建](/standards/build)。

## 不合规情况

以下任一情况成立时，实现不符合本页要求：

- Core 依赖具体模块或 App；
- 模块直接使用另一模块的 Repository、Mapper、数据库行类型或内部包；
- 为单个 App 的特殊规则增加 Core 参数、公共配置或公共依赖；
- Controller、Service、Repository 和 Mapper 职责错位，或上下层只做同名转发；
- 为单一固定实现增加没有扩展契约的接口和 `Impl` 类；
- 绕过已有公开入口，再实现一套身份、权限、消息、文件、实时通信或配置处理；
- 源码、配置、数据库、测试、文档和调用方使用不同契约；
- 只证明能够编译，没有验证受影响的行为和失败结果。
