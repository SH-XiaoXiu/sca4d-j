# 管理端规则

## 契约层

`sca4d-admin/common` 只保存框架无关类型、ApiClient 和 API；不得依赖 Vue、Pinia、Router 或具体页面。后端 Request 和 View 类型从 OpenAPI 生成。

## 应用层

`app` 统一使用 AdminLayout、Router、session/authentication/workspace/locale/theme store 和公共组件。领域页面位于 `src/features/<domain>`，只负责数据加载与流程编排；独立表单、对话框和业务视图由领域组件承载。页面不得各自实现登录恢复、错误解析或工作上下文。

跨领域组件按 actions、avatar、data-display、data-entry、navigation 和 page 分类。根路由只组合按访问范围拆分的路由定义、守卫和工作区协调。根语言资源只组合公共与领域消息，不集中承载全部页面文案。

## 状态刷新

所有 API 经统一客户端读取 `X-Client-State-*`。需要刷新时重新加载 `/me`、权限路由和相关业务数据；禁止只修改本地权限数组。

后端使用 `ClientStateTarget` 写入刷新目标。固定目标由所属业务模块的 `XxxClientStateTargets` 声明，资源级目标使用资源类型和主键构造；禁止在 Service、Repository 或 Controller 中直接拼接 Header 字符串。

## ID 与时间

后端 Long ID 统一为 TypeScript string；禁止 `Number(id)`。时间使用统一工具和明确时区，不在页面散落解析格式。

## 设计与交互

页面必须使用统一 token 和公共组件，覆盖完整状态、风险确认、窄屏和无障碍。权限隐藏不替代后端授权。

## 验证

接口变化执行 api:check/generate；页面变化执行 lint、typecheck、test、build 和风险匹配的 E2E/浏览器验收。
