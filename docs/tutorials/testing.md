# 编写与运行测试

本页说明脚手架的测试分层、从仓库根目录运行单元测试和集成测试的命令、集成测试对 Docker 的要求，以及测试类的命名和位置。适合为脚手架或具体应用编写和运行测试的开发者。

读完本页，读者能选择需要的测试层次，运行对应的 Gradle 任务，并判断集成测试的前置条件是否满足。

## 测试分层

- 单元测试：验证隔离的业务规则、转换、编解码和失败结果。源码位于 `src/test/java`。
- 装配测试：验证 Bean 选择、配置绑定、代理和启动条件。脚手架使用 `ApplicationContextRunner` 的 `*AutoConfigurationTest` 属于装配测试，例如 `modules/iam/sca4dj-iam/src/test/java/com/sca4d/iam/base/user/UserAutoConfigurationTest.java`。
- 集成测试：验证 Repository、事务、消息、缓存和模块协作的实际结果。源码位于 `src/integrationTest/java`，使用 Testcontainers 启动真实依赖。

单元测试不能代替 Spring 装配、数据库约束或外部设施的真实接入。需要验证这些行为时，使用装配测试或集成测试。

编译、构建和页面检查回答的问题不同，见 [测试与验证](/standards/10_testing)。

## 运行测试

在仓库根目录运行全部已映射项目的测试：

```bash
./gradlew test
./gradlew integrationTest
```

`test` 运行单元测试和装配测试。`integrationTest` 运行集成测试。两者互不自动依赖，运行 `test` 不会执行集成测试。

只运行一个模块时，使用项目路径：

```bash
./gradlew :modules:iam:sca4dj-iam:test
./gradlew :modules:iam:sca4dj-iam:integrationTest
```

只运行一个测试类时，使用 Gradle 的 `--tests` 过滤参数：

```bash
./gradlew :modules:iam:sca4dj-iam:integrationTest --tests 'com.sca4d.iam.identity.SystemIdentityIntegrationTest'
```

根任务覆盖全部已映射项目。只修改一个模块时，优先运行该模块的任务。

## Docker 要求

集成测试使用 Testcontainers。源码中的集成测试类使用 `@Testcontainers`，并以 `@Container` 声明真实依赖，例如 `postgres:17-alpine` 的 `PostgreSQLContainer`，以及 Redis 或 MinIO 容器。

运行集成测试的机器需要可用的 Docker。源码中没有按 Docker 可用性跳过测试的条件。Docker 不可用时，容器无法启动，集成测试失败，不会显示为跳过。

单元测试和装配测试的源码不使用 Testcontainers，不需要 Docker。

## 命名与位置

- 单元测试和装配测试位于 `src/test/java`，类名以 `Test` 结尾，例如 `core/sca4dj-core-spring/src/test/java/com/sca4d/core/spring/id/SnowflakeIdGeneratorTest.java`。
- 集成测试位于 `src/integrationTest/java`，类名以 `IntegrationTest` 结尾，例如 `modules/iam/sca4dj-iam/src/integrationTest/java/com/sca4d/iam/identity/SystemIdentityIntegrationTest.java`。
- 测试辅助类不以 `Test` 结尾，例如 `modules/auth/sca4dj-auth/src/test/java/com/sca4d/auth/testing/SessionTestFixture.java`。
- 测试通过公开行为验证目标约定，检查最终数据和可观察的结果。测试方法的写法见 [代码评审](/standards/11_review)。

## 相关页面

- [测试与验证](/standards/10_testing)：说明各类检查能证明的内容。
- [格式化与构建](/standards/09_build)：说明按修改风险选择检查。
- [Sca4d-J 构建 Gradle 插件](/engineering/build-gradle-plugin)：说明 `test`、`integrationTest` 等根任务如何映射到各项目。
