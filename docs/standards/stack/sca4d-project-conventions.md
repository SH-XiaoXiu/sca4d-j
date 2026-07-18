# Sca4d-J 项目工程约定

## 目录

- [生效范围](#生效范围)
- [架构语言](#架构语言)
- [模块边界](#模块边界)
- [命名与类型](#命名与类型)
- [Service](#service)
- [Controller 与 View](#controller-与-view)
- [Mapper 与 Repository](#mapper-与-repository)
- [核心流程与私有方法](#核心流程与私有方法)
- [公共实现入口](#公共实现入口)
- [常量与路由](#常量与路由)
- [数据模型](#数据模型)
- [注释与文档](#注释与文档)
- [格式化](#格式化)
- [验证](#验证)
- [不合规情况](#不合规情况)

## 生效范围

本规范适用于 Sca4d-J 主仓库及由其直接创建、仍沿用相同工程结构的项目。

实现和评审时读取仓库最新的：

- `docs/standards/`
- 与变更直接相关的系统设计、开发指南、内置功能和运维文档

以下内容规定项目结构和技术选择。具体专题规范与本页冲突时，必须形成唯一规则并同步修正文档；存在两套冲突要求的实现不符合项目规范。

## 架构语言

以可预测性作为优雅标准。

新代码延续项目已经形成的目录、命名、API、持久化和调用模式。重复可以是经过确认的样板，不为了展示技巧创造新的表达方式。

项目遵守以下原则：

- 稳定核心，丰富边缘；
- 抽象能力，不抽象步骤；
- 核心流程保持展开；
- API 表达业务语言；
- 显式参数优于无意义包装；
- 数据库保存稳定事实；
- 类型系统表达业务约束；
- 已废弃实现直接删除，不在源码中承担历史兼容；
- 新增复杂度必须产生稳定价值；
- 保持从上到下的阅读连续性；
- 所有模块像同一个人编写。

粗糙实现和过度设计都不符合本规范。

## 模块边界

- 根项目是 Gradle 聚合工程，不承载业务源码。
- `sca4d-server` 承载主后端应用。
- `com.sca4d.*` 是 `sca4d-server` 内部包结构，不因包边界拆成 Gradle 模块。
- `sca4d-gateway` 保持可选，不进入主应用进程，不成为主应用正确性的前提。
- 新模块只有形成独立部署、独立裁切或独立技术边界时才成立。

目录移动使用 `git mv`，并在一次变更中同步完成构建、配置和文档接线。

## 命名与类型

- 类名表达业务概念或稳定职责。
- 避免泛化的 `Dto`、`VO`、`BO`、`PO`、`Data`、`Info`、`Context`、`Manager`。`Util` 和 `Helper` 只有在类型确实提供无状态通用操作或有限技术协助时才成立，不得承载业务状态机和流程编排。
- HTTP 入参使用 `Request`，接口出参使用 `View`，持久化对象使用 `Entity`。
- 查询条件使用 `Query`，操作结果使用 `Result`，状态快照使用 `Snapshot`，消息、审计和回调内容使用 `Payload`。
- 业务模型优先使用无后缀语义名。
- 不使用 Java `record`。
- 枚举在 API 和持久化边界实现项目统一的 `BaseEnum` 契约，并使用统一转换机制。
- 已有枚举转换能力时，Entity 不把枚举降级为手写字符串字段。
- Entity、领域模型、Request、Result、View、Mapper 查询结果和 Service 契约中的有限状态、类型、模式、操作、
  类别必须使用实现 `BaseEnum` 的类型安全枚举。数据库列为 varchar、JSON 展示为字符串或 SQL 使用字符串字面量，
  均不能成为 Java 字段降级为 `String` 的理由。
- `String status`、`String type`、`String mode`、`String operation` 等字段必须逐项证明是开放业务标识；无法证明
  可无限扩展时按阻塞问题改为枚举；确属开放标识时必须在字段或所属类型文档中说明扩展来源。修改必须同步 Entity、
  领域模型、Request、Result、View、Wrapper、Mapper、测试和 OpenAPI。
- 不为相同字段同时创建 Request 和 Command 两套对象。
- Command 只有在表达独立业务命令并明显超出 HTTP 请求语义时才成立。

## Service

- 通用 Java 分层、完整文件审查和私有方法规则以[Java 工程完整审查规范](/standards/stack/java-engineering-review)为准；以下条目只补充 Sca4d-J 的类型和目录要求。
- 业务 Service 使用接口和实现类分离。
- 接口定义稳定业务能力，写完整中文文档注释。
- 实现类承载流程、事务和副作用，使用中文逻辑注释完整说明实现。
- 业务层允许使用 Lombok；基础设施和公共契约按仓库既有规则保持显式代码。
- 核心 API 优先使用显式参数，只有参数组已经形成稳定概念时才包装。
- Service 对外禁止返回 `View`。
- 简单单表查询可以返回 `Entity`。
- 组合查询、权限数据、展示前置数据和统计数据返回业务模型。
- Service 不接受仅为 HTTP 绑定存在的 Request，除非该对象已经被项目明确确认为稳定查询或写入契约。
- 不增加与 Request 字段相同、只为跨层而存在的 Command。

## Controller 与 View

- Controller 统一放在 `com.sca4d.controllers`。
- 公共路由直接位于 controllers 根包。
- 产品或业务使用流程放在 `controllers.biz.<module>`，不强制 `/biz` 前缀。
- 管理流程放在 `controllers.mg.<module>`，路由使用 `/mg/...`。
- 流程视角过多时评估多应用或微服务，不无限增加 Controller 分类。
- Controller 处理请求绑定、权限声明和响应转换，不编写复杂业务流程。
- View 通过 `View.from(...)` 从 Entity 或业务模型构造。
- View 可以承担展示构造、字段脱敏、派生字段和结构裁剪。
- View 不访问数据库、不调用外部服务、不做业务决策。
- 纯字段复制优先使用项目已有 Bean 工具；只有特殊转换保留显式处理逻辑。

## Mapper 与 Repository

- 完整执行[持久化分层完整规范](/standards/stack/persistence-boundary)。
- Mapper 统一放在 `com.sca4d.mapper`，XML 放在 `src/main/resources/mapper`。
- 简单单表查询、动态筛选、排序和分页由 Repository 使用 MyBatis-Flex `QueryWrapper` 与实体 getter 类型安全构造，Mapper 继承 `BaseMapper<Entity>`，不重复声明自定义查询或 XML。
- 复杂 JOIN、聚合、原子条件写入、锁语义、JSON/数据库特有 SQL，或 Wrapper 无法清晰表达的查询，才使用自定义 Mapper 方法和 XML。
- Mapper 使用最小 SQL 形状的方法名，例如 `insert`、`selectById`、`countBy...`、`updateStatus`。
- Mapper 不按 enable、disable、approve、reject 等业务动作拆分相同 SQL。
- Repository 提供稳定持久化能力、聚合读写和结果转换，不机械复制 Service 的业务动作名称。
- Repository 方法仅绑定不同状态常量并转发同一 Mapper 时，应合并为一个类型安全的持久化方法。
- Service 负责 enable、disable、approve、reject 等业务动作和允许的状态流转。
- Repository 负责乐观锁参数、受影响行数判断、持久化结果转换和稳定存储不变量。
- Mapper 与 Repository 不以同名、同参数和同返回值一一转发。
- 一个 Mapper 方法可以支撑多个 Service 动作；一个 Repository 方法可以组合多个 Mapper 或多次 SQL 操作。
- 自定义 Mapper 方法数量大于或等于 Repository 公开持久化方法数量时，视为需要重点复核的设计异味。
- 简单查询使用 Entity；关系聚合查询可以使用 Result，并由 Repository 转换为业务模型。
- 已有全局驼峰映射和类型转换时，不手写重复 `resultMap`。
- 复杂关系读取优先使用 MyBatis XML 在可控范围内一次完成，避免在 Service 拼装大量关系查询。
- Repository 不泄漏 Wrapper、JOIN、XML 和 SQL 实现细节给 Service。

## 核心流程与私有方法

核心业务方法完整展示校验、构建、持久化、事件和通知流程。

拒绝为了减少方法长度创建以下类型的私有步骤方法：

- normalize...
- validate...
- prepare...
- build...
- execute...
- after...
- current...
- resolve...，仅做枚举或字段直接转换时
- to...，仅做完整字段复制时

私有方法必须代表能够独立命名、独立说明和可能复用的能力，并遵守完整中文文档注释规范。

纯转发重载、只改变参数形状的短包装和重复当前上下文组装通常应当删除。

## 公共实现入口

- 认证、身份、主体、权限、请求上下文和实时传输复用统一处理链，不为 HTTP、WS、SSE 分别创建平行身份模型。
- Sa-Token 负责其原生会话与权限匹配能力，项目扩展只补充真实缺失的业务语义。
- Sa-Token 已支持通配符时，不重新实现权限通配算法。
- Spring 提供类型化消息转换和监听声明时，不在监听器中手写 `ObjectMapper` 解析和容器生命周期。
- MyBatis-Flex、Jackson、枚举 Codec、Bean 工具和统一配置能够完成的工作，不建立第二套实现。
- 业务模块通过 `MessagePublisher` 发布消息，不直接注入 `RabbitTemplate`，不直接操作 Outbox 表。
- 长期数据库和业务 ID 使用 `IdGenerator`；临时 traceId、connectionId 可以使用 UUID。

## 常量与路由

常量类只管理一个清晰、稳定的语义集合。

禁止把消息类型、群聊角色、路由、事件名和配置键放入同一个 `XxxConstants`。

共享 realtime 基础设施路由由 `RealtimeRoutes` 管理。IM、通知和其他业务路由、事件名称由所属业务模块维护。

配置默认值只保留一个权威来源。Java 默认值、YAML 默认值和常量不得重复维护同一事实。

## 数据模型

- 数据库保存稳定事实，不保存可以通过可靠 SQL 条件组合得到的临时结果。
- 未读数量等派生值优先根据消息和已读游标计算，不为方便查询维护错误计数模型。
- 逻辑删除用于需要恢复、审计追溯、外部引用稳定或唯一键隔离的主数据和业务主单。
- 关系表、映射表、临时表、缓存投影、可重建明细和日志流水默认硬删除或仅追加。
- 主数据和可编辑业务主单使用 `version` 乐观锁。
- 可编辑 View 暴露 `version`，过期写入统一返回 409 和客户端刷新信号。
- 分布式锁只保护跨请求、跨节点且存在竞争副作用的关键区。
- 集群正式路径使用 Redis Bucket4j，不使用本机限流作为默认实现。

## 注释与文档

完整执行[注释与成品文档](/standards/general/comments-and-docs)。

项目补充要求：

- 接口和实现的方法签名使用 JetBrains nullability 注解；
- 接口及接口方法提供完整中文文档注释；
- 所有私有方法提供完整中文文档注释；
- 实现逻辑使用中文行内注释；
- 业务方法只阅读注释即可理解完整业务流程；
- `docs/standards/` 保存强制工程规则，不混入实现路线和讨论过程；
- 开发指南和内置功能文档提供模块用法、配置、扩展和示例；
- 根 `README.md` 只保留项目身份、运行入口和文档入口；
- 产品和技术文档不保存内部工作记录、审查草稿、设计争论、未完成指令和对文档自身的解释。

## 格式化

- Java 使用 Spotless 和仓库版本化的 Eclipse JDT 格式配置。
- YAML、JSON 的基础格式由 `.editorconfig` 维护。
- 后端脚手架不为 YAML、JSON 引入 Prettier 或 npm 依赖。
- `docker-compose.yml` 不进入 Spotless misc 格式化。
- 不根据个人猜测调整换行和 import，遵循 Spotless 输出。

## 验证

根据变更范围选择命令，至少包含：

```bash
git diff --check
./gradlew :sca4d-server:compileJava
./gradlew :sca4d-server:test
./gradlew :sca4d-server:spotlessCheck
```

影响 Gateway 时增加：

```bash
./gradlew :sca4d-gateway:test
./gradlew :sca4d-gateway:spotlessCheck
```

影响跨模块契约或构建结构时增加：

```bash
./gradlew test
./gradlew spotlessCheck
./gradlew build
```

修改 Compose 时执行：

```bash
docker compose config
```

格式化或构建失败时，先区分本次变更和仓库既有问题。不修改无关代码制造通过结果。

## 不合规情况

以下任一情况成立时，项目实现不符合本规范：

- 新增概念无法说明稳定语义；
- 同一对象存在平行类型、重复计算或多个状态来源；
- Mapper、Repository、Service、Controller 职责错位；
- Mapper 与 Repository 方法接近一一对应，没有形成抽象层级变化；
- 相同 SQL 因业务动作名称不同被拆成多个 Mapper 或 Repository 包装方法；
- 核心流程被大量步骤型私有方法拆碎；
- 手写项目或框架已有能力；
- 包、名称、类型和文档偏离仓库既有语言；
- 注释和文档不满足中文成品规范；
- 源码、配置、测试、迁移、文档或调用方未完成同步；
- 只证明能够编译，没有证明行为和架构正确。
