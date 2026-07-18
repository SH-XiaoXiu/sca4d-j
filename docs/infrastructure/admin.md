# 管理端

管理端位于 `sca4d-admin`，使用 pnpm workspace 管理两个包。

## common

`@sca4d/admin-common` 是框架无关的 TypeScript 包，提供：

- OpenAPI 生成类型。
- Axios API Client。
- 登录、当前用户和管理接口。
- 分页、日期时间和 API 错误处理。
- 幂等 key。
- ClientState 响应头处理。
- 支持 Token Header、事件 ID 和自动重连的 SSE Client。

OpenAPI 生成文件位于 `common/src/generated/openapi.ts`，页面通过领域 API 调用后端。

## app

`@sca4d/admin-app` 使用 Vue 3、Pinia、Vue Router、Ant Design Vue 和 Lucide 图标。正式页面包括：

- 登录与工作台。
- 账号信息。
- 平台账号。
- 平台租户。
- 平台租户成员。
- 业务计划。
- 租户成员管理。
- 系统字典。
- 系统菜单。
- 系统任务。
- 操作日志。

账号信息和顶部导航统一展示 DEFAULT Profile 头像。当前 Subject 直接点击头像选择 JPEG/PNG/WebP，不显示第二个选择按钮；圆形裁切框的位置和大小固定，通过移动、缩放或旋转图片调整取景，上传结果固定为 512×512 WebP。租户成员不复制默认头像资料。

## 工作上下文

auth Store 读取 `/me` 并保存 Subject、固定 Account、Principal、角色、权限、租户和 `navigation`。切换平台或租户工作区时重新请求对应 Principal 的 `/me`。

代码通过 `NavigationRouteContributor` 注册可以被菜单引用的路由。路由默认标题消息键固定为 `navigation.<routeCode>`，菜单编辑器选择路由时取得全部支持语言的默认标题。系统菜单保存管理员最终确认的多语言标题、路由编码、Lucide 图标名称、排序和显示状态；后续消息资源变化不会覆盖已保存标题。图标选择器打开时加载完整 Lucide 目录，支持搜索并分批渲染，网格只显示图标。菜单排序只在同一父菜单内拖拽，服务端一次接收完整顺序和版本。Router 根据 `/me.navigation` 生成可见菜单，并在进入页面前检查登录、Principal 类型和权限。

workspace Store 管理页签、活动页面、滚动位置和 KeepAlive 组件。工作区按 Subject 保存；切换 Subject 后使用各自的页签状态，同一 Subject 的不同 Account 共用工作区。启动和检查命令见[日常开发命令](/handbook/daily-development.md)。

## 请求与页面状态

`ApiClient` 统一附加 token 和语言，解析 `ApiResponse`、`Retry-After` 与 `X-Client-State-*`。401 调用 `onUnauthorized`，状态变化调用 `onClientState`；API、网络、超时和响应格式错误分别转换为 `ApiError`。SSE 在 `/me` 初始化后连接，退出或 401 时关闭。响应头、状态事件和重连信号共用一个 `/me` 刷新队列，请求期间的多个信号只追加一轮最终刷新。

列表页面必须分别处理加载、空数据、失败和正常数据。账号、操作员、租户、成员、计划、字典和操作日志页面使用 URL 保存已应用查询条件，表单输入在点击查询前只作为草稿；分页基于 URL 条件，重置清空 URL，前进、后退和 KeepAlive 重新激活都会重新加载。创建、更新和删除期间禁用重复提交；高风险操作先确认。权限只控制页面和按钮显示，后端仍检查 Principal、权限和数据范围。

应用语言列表来自 `GET /system/languages`。切换语言后重新请求 `/me`，刷新导航和服务端本地化数据，并重建 KeepAlive 页面；存在未保存标签时先确认。

文件 API 在 common 包中封装签名、DIRECT/PROXY 上传、complete 和批量 READ。文件 Store 按 scene、resourceId、fileId 缓存短期地址，并在过期前刷新；页面不持久化签名 URL。头像资料使用 DEFAULT Profile 的 `profileVersion`，收到 409 后重新加载最新默认资料。
