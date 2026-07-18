# 项目规范

以下规则适用于后端、网关、管理端、数据库、配置、脚本、测试和文档。各专题分别定义对应代码和交付物的合规条件，系统设计、开发指南、内置功能、运维和参考手册提供当前项目事实。

## 工程原则

1. [工程验收原则](./philosophy/acceptance-principles)
2. [可预测的工程结构](./philosophy/predictable-architecture)

## 通用规则

3. [工程哲学与核心编码规则](./general/core-rules)
4. [验收规则](./general/acceptance)
5. [修改范围](./general/change-scope)
6. [代码结构与分包](./general/code-structure)
7. [命名、类型与显式契约](./general/naming-and-types)
8. [抽象与处理步骤](./general/abstraction-and-flow)
9. [分层与 API](./general/layers-and-api)
10. [注释与成品文档](./general/comments-and-docs)
11. [测试、评审与验收](./general/testing-and-acceptance)
12. [代码评审覆盖规范](./general/review-execution)

## 技术栈规则

13. [Java 与 Spring](./stack/java-and-spring)
14. [Java 工程完整审查](./stack/java-engineering-review)
15. [MyBatis-Flex 与持久化](./stack/persistence)
16. [SQL 与持久化分层完整审查](./stack/persistence-boundary)
17. [PostgreSQL 与数据库](./stack/database)
18. [Vue、TypeScript 与交互](./stack/frontend)
19. [前端质量与交互设计完整审查](./stack/frontend-quality-review)
20. [Sca4d-J 项目工程约定](./stack/sca4d-project-conventions)
21. [格式化与构建](./stack/formatting-and-build)

## 内置功能规则

22. [认证](./facilities/authentication)
23. [权限](./facilities/permission)
24. [CurrentRequest](./facilities/request-context)
25. [并发](./facilities/concurrency)
26. [消息](./facilities/messaging)
27. [实时通信](./facilities/realtime)
28. [文件](./facilities/file)
29. [审计](./facilities/audit)
30. [应用日志](./facilities/logging)
31. [缓存](./facilities/cache)
32. [配置](./facilities/configuration)
33. [管理端](./facilities/admin-ui)

## 规则冲突

两篇规则对同一实现提出不同要求时，必须先形成唯一要求并同步修正文档。存在冲突的实现不符合项目规范。

冲突不能通过选择要求更低的一项或新增中间层保留两种行为来解决。

## 验收入口

[测试、评审与验收](./general/testing-and-acceptance)定义验证范围，[代码评审覆盖规范](./general/review-execution)定义修改文件的覆盖要求。

编译和测试通过不能替代分层、命名、类型、并发、日志、前端质量和文档审查。
