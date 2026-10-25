# 基础结构

本目录说明 Sca4d-J 的仓库组成、公共 Core、通用结果类型和管理端接入规则。适用于新增代码、判断模块归属或接入公共约定的开发者。先阅读[系统组成](/capabilities/foundation/system)，判断代码应放入 `core/`、`modules/`、`apps/` 还是 `frontends/`。

## 组成与公共约定

- [系统组成](/capabilities/foundation/system)
- [核心层](/capabilities/foundation/core)
- [版本配置与接口兼容](/capabilities/foundation/versioning)
- [全局 ID](/capabilities/foundation/global-id)
- [重试规则与执行](/capabilities/foundation/retry)
- [错误码与业务异常](/capabilities/foundation/error-model)
- [名称与应用隔离](/capabilities/foundation/name-and-isolation)
- [语言环境与国际化](/capabilities/foundation/i18n)
- [时间与时钟](/capabilities/foundation/time-and-clock)
- [事务提交后回调](/capabilities/foundation/transaction-callbacks)

## 扩展与接口

- [插件机制](/capabilities/foundation/plugin)
- [OpenAPI 文档生成](/capabilities/foundation/openapi-generation)

## 管理端

- [管理端开发规则](/capabilities/foundation/admin-development)

HTTP 路由与运行配置由具体应用的接口说明和运行配置说明负责。Java 类型成员见 [Java API 参考](/reference/#java-api-参考)。
