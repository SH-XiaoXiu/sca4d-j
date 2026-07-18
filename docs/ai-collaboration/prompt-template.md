# Goal 任务提示词模板

## 字段

| 字段 | 填写内容 |
| --- | --- |
| 项目背景 | 当前系统、目标用户、相关模块和已有实现 |
| 最终目标 | 用户可以观察和验收的结果 |
| 不做的内容 | 本次任务明确不修改的模块、行为和数据 |
| 已确认方案 | 数据、接口、页面、外部服务和失败处理方式 |
| 验收条件 | 测试、命令、页面操作和运行结果 |
| 运行环境 | 已启动服务、端口、数据库、外部服务和数据限制 |
| 任务相关文档 | 除项目规范外，本次必须读取的系统设计、教程、运维和参考页面 |
| 提交要求 | 是否创建提交以及提交范围 |

模板中的尖括号字段必须替换为当前任务内容。无法确认的字段写成待确认问题，不得由 Agent 自行补全。

## 模板

```text
# Goal

## 项目背景

<说明当前系统、目标用户、相关模块和已有实现。>

## 最终目标

<逐条写出用户可以观察和验收的最终结果。>

## 不做的内容

<逐条写出本次不修改的模块、行为、数据和兼容范围。>

## 已确认方案

<说明数据、接口、页面、外部服务、失败结果和兼容处理。没有确认的内容列为问题。>

## 验收条件

<列出必须通过的测试、命令、页面操作、接口响应和运行结果。>

## 运行环境

<列出已经启动的服务、实际端口、可用数据库、外部服务和不能重建的数据。先验证现有状态，不重复启动。>

## 任务相关文档

<逐个列出本次任务相关的 architecture、development、infrastructure、operations 和 reference 页面路径。>

## 提交要求

<填写“完成全部检查后创建一次提交”或“本次不创建提交”。>

# 执行要求

你是本 Goal 的受约束执行者。用户负责最终目标、业务取舍和验收；你负责读取项目、提出计划、修改文件、运行检查并报告结果。

不得根据局部要求直接修改代码。先调查当前实现和运行状态，列出冲突、缺失信息和需要用户确认的取舍，然后提供完整计划。计划必须覆盖数据、接口、页面、失败处理、测试、人类文档和提交范围。用户确认计划前不得修改文件。

用户确认后，在同一个 Goal 下持续工作，直到验收条件全部满足或出现必须由用户处理的阻塞条件。每次执行都要产生可以检查的代码、测试、文档、验证结果或阻塞证据。不要因为任务耗时、上下文较长或 Token 消耗较高而跳过读取、测试和审查。

# 必须完整读取的项目规范

以下 34 篇文件全部是强制要求。必须逐个读取完整内容，不得只读取目录、索引、摘要或搜索结果：

1. docs/standards/index.md
2. docs/standards/philosophy/acceptance-principles.md
3. docs/standards/philosophy/predictable-architecture.md
4. docs/standards/general/abstraction-and-flow.md
5. docs/standards/general/acceptance.md
6. docs/standards/general/change-scope.md
7. docs/standards/general/code-structure.md
8. docs/standards/general/comments-and-docs.md
9. docs/standards/general/core-rules.md
10. docs/standards/general/layers-and-api.md
11. docs/standards/general/naming-and-types.md
12. docs/standards/general/review-execution.md
13. docs/standards/general/testing-and-acceptance.md
14. docs/standards/stack/database.md
15. docs/standards/stack/formatting-and-build.md
16. docs/standards/stack/frontend-quality-review.md
17. docs/standards/stack/frontend.md
18. docs/standards/stack/java-and-spring.md
19. docs/standards/stack/java-engineering-review.md
20. docs/standards/stack/persistence-boundary.md
21. docs/standards/stack/persistence.md
22. docs/standards/stack/sca4d-project-conventions.md
23. docs/standards/facilities/admin-ui.md
24. docs/standards/facilities/audit.md
25. docs/standards/facilities/authentication.md
26. docs/standards/facilities/cache.md
27. docs/standards/facilities/concurrency.md
28. docs/standards/facilities/configuration.md
29. docs/standards/facilities/file.md
30. docs/standards/facilities/logging.md
31. docs/standards/facilities/messaging.md
32. docs/standards/facilities/permission.md
33. docs/standards/facilities/realtime.md
34. docs/standards/facilities/request-context.md

还必须完整读取“任务相关文档”中列出的每个文件，以及本次修改涉及的源码、配置、迁移、测试和当前未提交内容。

# 修改规则

- 严格按照用户确认的最终目标、范围和方案修改。
- 使用项目已有的业务名称、目录、接口、错误码和页面交互方式。
- 保留用户已有且与本 Goal 无关的修改，不回滚、不覆盖、不混入提交。
- 发现计划与代码冲突时停止相关修改，报告具体文件、行为差异和可选结果。
- 不新增没有业务含义的抽象、中间类型、兼容分支或旁路实现。
- 不修改已经执行的 Flyway 迁移，不手工修改生成文件。
- 任何检查失败都必须确定原因并处理，不得静默忽略。

# 人类文档

代码、接口、配置、数据库或运行方式变化时，按照 docs/documentation/change-routing.md 找到受影响页面。

修改人类文档时必须：

1. 确定目标读者和唯一文档类型：Tutorial、Concept、Reference 或 Specification。
2. 保存原文内容清单，覆盖规则、条件、例外、失败结果、示例、代码块、链接和验收要求。
3. 从当前源码、Controller、OpenAPI、Properties、YAML、迁移和构建脚本核对内容。
4. 使用项目用户和维护者的语言，不写你的思考过程、任务对话、执行记录、临时提示词或设计辩解。
5. 每段文字必须说明用户需要知道的内容、操作、结果或故障处理。
6. 示例必须包含必要上下文，不得使用“省略部分代码”“其他配置类似”“自行实现”。
7. 对照修改前清单，任何有效内容缺失都视为文档未完成。
8. 更新导航和内部链接，运行 VitePress 构建并检查桌面、窄屏、明暗主题、表格、代码块和搜索。

Agent 角色、执行规则和可复用提示词只写入 docs/ai-collaboration/。其他 docs 页面必须保持人类可读。

# 全文件审查

实现完成后列出全部新增、修改、重命名和删除文件。逐个读取每个文件的完整内容，不得只查看 diff hunk。

按照上面列出的 34 篇项目规范逐条审查。任何一个文件违反任何一条规则，都视为整个 Goal 未通过。修复全部问题后重新运行相关测试和审查，直到问题数量为零。测试通过不能替代分层、命名、类型、并发、日志、前端质量或文档规则。

至少运行：

- git diff --check
- 受影响后端或网关模块的编译、测试和 Spotless 检查
- 受影响管理端工作区的 typecheck、测试、构建和浏览器检查
- OpenAPI 契约检查（接口变化时）
- cd docs && pnpm docs:build（文档变化时）

命令必须根据实际修改范围补充，不能把上述列表当作完整替代。

# 人工接管记录

每个工作批次结束时报告：

- 当前 Goal
- 已确认决定
- 已完成内容
- 剩余工作
- 修改文件
- 已运行命令和结果
- 当前错误或阻塞条件
- 下一项可以直接执行的工作

报告必须与当前工作树一致，不保留已经完成或删除的待办。

# 提交门禁

只有最终目标全部完成、全部修改文件完成全文件审查、34 篇项目规范逐条检查无问题、相关测试和构建通过、人类文档同步且提交范围只包含本 Goal 时，才允许提交。

任意一项不符合时继续修复，不创建临时提交。如果“提交要求”允许提交，全部检查通过后创建一次提交；否则保留工作树并报告结果。
```

## 使用限制

- 模板不能替代用户确认。背景和目标不完整时，先补充信息。
- 34 篇项目规范必须保留完整列表；项目增加或删除规范文件时同步更新模板。
- 任务相关文档必须按当前 Goal 补充，不能只依赖项目规范。
- 验收命令根据修改范围填写，不能只保留模板中的最低检查项。
