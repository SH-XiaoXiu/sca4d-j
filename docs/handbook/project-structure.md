# 工程结构

## 聚合工程

根工程配置 Gradle 插件、依赖仓库、Java 21 toolchain、构建版本和环境变量检查。业务源码位于 `sca4d-server`，根工程不包含 Java 源码。

```text
sca4d-j/
├── sca4d-server/       主后端应用
├── sca4d-gateway/      可选网关应用
├── sca4d-admin/        管理端工作区
├── docs/               VitePress 文档
├── config/             格式化等工程配置
└── docker-compose.yml  本地依赖
```

## 主应用目录

`sca4d-server/src/main/java/com/sca4d` 中：

- `common` 保存 Web、认证、权限、并发、消息和校验接口；
- `authentication`、`iam`、`permission` 保存身份权限实现；
- `file`、`notification`、`im` 分别保存文件、通知和即时通信代码；
- `controllers` 保存 HTTP 接口；
- `entity` 和 `mapper` 保存数据库映射；
- `config` 保存显式装配和类型化配置；
- 其他业务包按领域名组织 Service、Repository、Request、View 和模型。

资源目录中的 `db/migration` 保存 Flyway 脚本，`mapper` 保存 MyBatis XML，`i18n/*.properties` 按消息类型保存国际化消息。无语言后缀文件是简体中文最终回退，`_en_US.properties` 保存英文。

## Controller 流程视角

登录、文件和系统目录等公共接口放在 `controllers`，产品端接口放在 `controllers.biz.<module>`，管理端接口放在 `controllers.mg.<module>`。产品端和管理端使用不同权限、状态修改或数据查询时，分别创建 Controller。

## 管理端目录

`sca4d-admin/common` 是框架无关的 TypeScript 契约和 API 客户端；`sca4d-admin/app` 是 Vue 3、Pinia、Vue Router、Ant Design Vue 应用。公共契约不得反向依赖具体页面。

## 网关目录

`sca4d-gateway` 是独立 Spring Boot 应用，通过 upstream 文件、健康检查和 Spring Cloud Gateway 路由请求。主应用不引入 Spring Cloud，也不依赖网关维持业务正确性。

## 配置和资源目录

根 `.env.example` 是 Server、Gateway、Compose 和管理端工具运行配置的清单；Spring YAML 是运行绑定入口。新增配置需同步两者并通过 `checkEnvExample`。

## 测试目录

后端测试位于各 Gradle 模块的 `src/test`。管理端单元测试与源码相邻或位于 `__tests__`，浏览器测试位于 `sca4d-admin/app/e2e`。
