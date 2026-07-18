# Gateway 与管理端 API

## Gateway

包：`com.sca4d.gateway.discovery`

| 类型 | 主要方法 | 失败行为 |
| --- | --- | --- |
| `GatewayUpstreamFileLoader` | `load(path, version)` | 文件缺失、重复 serviceId、非法 URI 或无实例时拒绝加载 |
| `GatewayUpstreamRegistry` | `initialize()`、`reloadIfChanged()`、`snapshot()`、`serviceIds()`、`availableInstances(...)` | 新文件加载失败时保留上一份有效快照 |
| `GatewayReactiveDiscoveryClient` | `description()`、`getInstances(...)`、`getServices()` | 只返回当前可路由实例 |
| `GatewayUpstreamHealthMonitor` | `checkHealth()` | 按 upstream 的超时和阈值更新实例状态 |
| `GatewayUpstreamHealthState` | `record(...)`、`disable(...)`、`healthy(...)`、`retain(...)` | 连续成功或失败达到阈值后切换状态；`HealthChange` 返回 `UNCHANGED`、`BECAME_HEALTHY` 或 `BECAME_UNHEALTHY` |
| `GatewayInstance` | `getId()`、`getUri()`、`isEnabled()`、`isDraining()`、`routable()` | disabled 或 draining 实例不可路由 |
| `GatewayUpstream` | serviceId、healthPath、connectTimeout、responseTimeout、阈值和实例列表 | `healthKey(...)` 生成实例健康状态键 |
| `GatewayUpstreamSnapshot` | `getVersion()`、`getUpstreams()`、`empty()` | 每次成功加载生成不可变快照 |

路由配置位于 `com.sca4d.gateway.config.GatewayRouteConfig`，配置绑定类型为 `Sca4djGatewayProperties`。

## 管理端公共包

目录：`sca4d-admin/common/src`

| 文件 | 导出类型或函数 | 行为与限制 |
| --- | --- | --- |
| `api-client.ts` | `ApiClient`、`ApiClientOptions`、`createApiClient(...)` | 处理 token、locale、查询参数、401、ClientState 和统一错误 |
| `api-error.ts` | `ApiError`、`ApiErrorKind`、`isApiError(...)` | 错误类型为 api、network、timeout 或 invalid-response |
| `client-state.ts` | `readClientState(...)`、`hasClientStateChange(...)` | 读取三个 `X-Client-State-*` Header |
| `idempotency.ts` | `createIdempotencyKey()`、`withIdempotencyKey(...)` | 浏览器支持时使用 `crypto.randomUUID()` |
| `page.ts` | `normalizePageData(...)` | 非安全整数或负数分页字段归一为 0 |
| `types.ts` | `Id`、`ApiResponse<T>`、分页、Session、Principal、权限和管理类型 | Java Long ID 使用 TypeScript `string` |
| `openapi.ts` | OpenAPI 辅助类型 | 生成内容位于 `generated/openapi.ts` |

业务 API 位于 `apis/authentication.ts`、`apis/me.ts`、`apis/management.ts`、`apis/menus.ts`、`apis/roles.ts`、`apis/dictionaries.ts`、`apis/schedules.ts` 和 `apis/system-catalog.ts`。所有 API 接受同一个 `ApiClient`，不创建独立 HTTP Client。

### 认证与当前用户 API

| 工厂 | 方法 | HTTP 请求 |
| --- | --- | --- |
| `createAuthenticationApi(client)` | `createAuthentication(request)` | `POST /authentications` |
| `createAuthenticationApi(client)` | `passwordProof(authenticationId, request)` | `POST /authentications/{authenticationId}/password-proofs` |
| `createAuthenticationApi(client)` | `createSession(authenticationId)` | `POST /sessions` |
| `createAuthenticationApi(client)` | `login(request)` | 依次创建认证、提交密码并创建 Session |
| `createAuthenticationApi(client)` | `logout()` | `DELETE /sessions/current` |
| `createMeApi(client)` | `current(query?)` | `GET /me` |

应用在 `app/src/api/client.ts` 使用共用 `ApiClient` 创建 `authenticationApi` 和 `meApi`。auth store 调用领域方法，不拼接 URL、Header 或请求体。

```ts
export const authenticationApi = createAuthenticationApi(apiClient);
export const meApi = createMeApi(apiClient);

const session = await authenticationApi.login({
  scopeType: "GLOBAL",
  identifierType: "USERNAME",
  identifier,
  password
});
const current = await meApi.current({ principalType: "PLATFORM" });
```

`MeSelectionQuery` 的字段来自管理端公共类型。Principal 选择的后端行为见[Principal 选择](/reference/api/request-auth#principal-选择)。

### 计划与系统任务 API

| 工厂 | 方法 | HTTP 请求 |
| --- | --- | --- |
| `createPlatformScheduleApi(client)` | `page(query)`、`detail(scheduleId)` | `GET /mg/platform/schedules` 及详情路由 |
| `createPlatformScheduleApi(client)` | `taskTypes()` | `GET /mg/platform/schedule-task-types` |
| `createPlatformScheduleApi(client)` | `create(input, key)` | `POST /mg/platform/schedules`，发送 `Idempotency-Key` |
| `createPlatformScheduleApi(client)` | `update(scheduleId, input, version)` | `PUT /mg/platform/schedules/{scheduleId}`，发送强 `If-Match` |
| `createPlatformScheduleApi(client)` | `delete(scheduleId, version)` | `DELETE /mg/platform/schedules/{scheduleId}`，发送强 `If-Match` |
| `createPlatformScheduleApi(client)` | `executions(...)`、`execute(...)` | 查询执行记录或发起人工执行 |
| `createSystemScheduledJobApi(client)` | `jobs()`、`detail(group, name)` | 查询系统 Job 列表或详情 |

计划 ID、版本和执行 ID 均使用 TypeScript `string`。更新或删除必须使用最近一次详情响应的 ETag 版本；人工执行重试应复用同一个幂等键。后端接口和状态见[调度 API](/reference/api/scheduler)和[HTTP 参考](/reference/http#业务调度)。

## 管理端组件与状态

目录：`sca4d-admin/app/src`

| 位置 | 类型 | 用途 |
| --- | --- | --- |
| `components/PageHeader.vue` | `PageHeader` | 页面标题、说明和操作区 |
| `components/WorkspaceTabs.vue` | `WorkspaceTabs` | 工作区页签和关闭操作 |
| `components/ResourceLink.vue` | `ResourceLink` | 根据资源引用状态显示链接或不可用文本 |
| `components/RemoteResourceSelect.vue` | `RemoteResourceSelect` | 服务端搜索、加载和失效选项显示 |
| `components/JsonPreview.vue` | `JsonPreview` | 格式化展示 JSON，不修改原值 |
| `components/QueryToolbar.vue` | `QueryToolbar` | 搜索、筛选和操作的容器查询布局 |
| `components/SortableOrderList.vue` | `SortableOrderList` | 指针、触摸和键盘排序；页面持有最终顺序 |
| `components/LocalizedTextEditor.vue` | `LocalizedTextEditor` | 根据后端语言目录一次编辑一种语言 |
| `components/IconPicker.vue` | `IconPicker` | 延迟加载、搜索并分批展示完整 Lucide 图标目录 |
| `composables/useRouteQueryState.ts` | `useRouteQueryState` | URL 已应用查询条件、页面草稿和 KeepAlive 重载 |
| `stores/auth.ts` | auth store | Session、`/me`、登录恢复和退出 |
| `stores/languages.ts` | language store | `/system/languages` 语言目录及应用语言集合 |
| `stores/workspace.ts` | workspace store | 页签、活动页、滚动位置和 KeepAlive |
| `stores/locale.ts` | locale store | `zh-CN`、`en-US` 和请求语言 |
| `stores/theme.ts` | theme store | 浅色、深色和系统主题 |

页面位于 `pages/`。当前管理页面包括平台账号、平台操作员、平台租户、租户成员、角色、租户 ACL、业务计划、系统任务、字典、系统菜单和操作日志；公共状态页包括登录、禁止访问和资源不存在。
