# 分层与 API

## 分层职责

- Controller：路由、参数绑定、权限注解和 View 转换；
- Service：完整业务流程、事务、校验和副作用选择；
- Repository：领域持久化语义与访问范围；
- Mapper：最小 SQL 和数据库映射；
- Entity：数据库表映射；Request：HTTP 输入；View：HTTP 输出。

禁止 Controller 编排事务，禁止 Service 返回 View，禁止 Repository 返回 HTTP 类型，禁止 Mapper 承载业务状态机。

## 依赖方向

上层依赖下层稳定契约，基础设施实现依赖公共接口。公共包不得依赖具体业务包；后端不得依赖管理端；common 前端契约不得依赖 Vue 页面。

## API 语言

Service 和公开接口使用调用方能够理解的句子命名，参数和结果完整表达预期。不把 MyBatis 配置类型、`HttpServletRequest`、通用 `Map` 或框架 Response 作为业务 API。

## 修改核心 API

核心 API 不因业务变体反复增加布尔开关、可空参数或泛化 Options。业务变体通过明确外围 API 复用稳定核心。

## 修改已有 API

API 重命名或语义改变时，所有调用、测试、配置和文档必须使用新契约，旧入口和无使用者的兼容重载不再保留。
