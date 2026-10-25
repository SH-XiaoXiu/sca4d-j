# 管理端规则

具体应用的管理端通过 `common/` 接口约定接入后端，并使用 Vben 作为页面框架。本页面向开发管理端页面、Store 和 HTTP 接口约定的开发者，说明各层的职责和范围。读完后，开发者应能判断一段管理端逻辑应放在哪一层，以及权限、版本和错误应如何处理。通用 Vue、TypeScript 和交互要求见 [Vue、TypeScript 与交互](/engineering/stack/frontend)，完整页面评审见 [前端质量与交互评审](/engineering/stack/frontend-quality-review)。

## `common/` 接口约定

`frontends/<应用名>-admin/common` 保存手写 TypeScript 接口约定、HTTP Client、错误转换、ETag、业务 Controller 和运行环境 Port。该目录必须能够在没有 Vue、Pinia、Router 和 DOM 的环境中执行和测试。

后端 Request、View、状态编码、错误或路由变化时，负责该接口的 `common/` 类型与 Controller 必须同步修改。页面内不得复制一份「临时响应类型」。也不得引入 OpenAPI 生成器覆盖手写约定。

AdminApiClient 集中处理 Token、`Accept-Language`、统一响应、ETag 和 HTTP 错误。Controller 将多个请求和状态变化整理成页面可以直接使用的操作。页面不得跳过这两层，自行创建 Axios 请求。

## Vben 应用

multipart POST/PATCH 请求使用 AdminApiClient 的文件入口。浏览器 HttpPort 原样发送 FormData，不做 JSON 序列化，也不手写 `Content-Type` 或 boundary。

错误转换保留后端 `data`。页面在确认其结构后再使用该数据。完整错误响应不得写入日志。

文件签名地址按真实 Session 隔离缓存。同一微任务中的请求合并处理，每批最多一百个文件。同一文件的在途请求共用结果。

退出、失效或切换 Session 时，清理缓存和等待者。迟到的响应不得回填缓存。同一 Session 内的 Token 刷新不清理缓存。

`frontends/<应用名>-admin` 使用 Vben 的布局、路由、菜单、Store、主题和公共组件。路由页面位于 `src/pages/`。需要多个组件共同完成的流程（如认证）位于 `src/features/`。

不要把所有页面机械移动到 `feature` 中。路由文件中也不得实现页面。

页面使用 Vben 的 Page、表格、表单、弹窗和抽屉模式。具体应用的 Server 代码补充业务字段、权限和操作，不复制另一套后台框架。`examples/vue-vben-admin/` 仅供查看上游写法，演示页面、静态数据和演示菜单不得接入运行时。

## Session 与权限

认证 Store 统一负责 Token 保存、Session 恢复、刷新、注销和 Session 变化后的状态更新。页面不得各自读取浏览器存储，也不得实现登录恢复。

菜单、路由和操作按钮使用当前 Session 返回的权限编码。权限变化后，统一刷新用户、权限、菜单、动态路由和页签。不能只修改页面内的权限数组。

前端权限控制用于避免展示不可执行的操作。最终授权由后端 Controller 负责。

JWT 与在线 Session Token 的选择由统一认证代码处理。普通页面不得根据 Token 内容推导账号、Principal 或权限，也不得自行解析 JWT。

## 资源版本与错误

父级路由拒绝访问时，子级路由同样拒绝访问。有权限的页面不会因为声明无权菜单可见而被替换成 403。菜单过滤不修改原路由定义。隐藏空的纯目录时，保留其自身可访问的页面。组件图标不写入标签页 JSON，恢复时从路由定义读取。

表格查询等待本次挂载完成。并发等待都必须结束。卸载时拒绝旧等待，旧调用不能进入新挂载实例。调用方等待查询结果并处理失败，不得把失败当作完成。

可修改资源的处理流程如下：

1. 读取结果中的 URI 和强 ETag 保存为 ResourceRevision。
2. 通过 AdminApiClient 的条件 PATCH/DELETE 方法提交。
3. 业务 Controller 不直接写 `If-Match`。

列表批量读取时，使用服务端为单项返回的 `resource + etag`。

没有强 ETag、服务器返回 412，或服务器要求前置条件时，保留用户输入并重新读取资源。用户确认后再提交，不得自动覆盖。

当前状态命令不得为满足接口形式而临时读取 ETag。

AdminError 区分未认证、无权限、不存在、冲突、前置条件、服务不可用和网络失败。页面根据错误种类保留数据、关闭失效的详情、返回登录页或提供重试。不得用一个全局 toast 吞掉所有失败。

并发与后端共用版本号，`ResourceRevision` 对应后端的强 ETag 与 `@IfMatch`。

## ID、时间和秘密

- 后端 64 位 ID 统一使用 `Int64String`。不得调用 `Number(id)`，也不得按大小排序字符串形式的 ID。
- 时间通过统一入口解析和显示。页面不得分别拼接格式，也不得混用时区。
- 密码、验证码、Session Token、Client Secret 和 Passkey 响应只在当前操作中使用。它们不得写入日志、持久化 Store、路由参数或普通错误对象。
- 敏感设置只显示是否已经配置。后端不返回的原值不得用占位字符串再提交。

## 页面状态

列表和详情页面处理加载、空结果、失败、无权限、资源失效和刷新。

提交操作各自保留加载和错误状态。一个 Provider 或认证方法的请求不得禁用其他无关入口。

创建、修改、删除、启停和角色分配成功后，刷新受影响的列表、详情和共享状态。刷新失败时，页面说明当前数据可能已过期。页面不得提前显示全部同步成功。

页面文案同时维护 `zh-CN` 与 `en-US`。固定状态的标签可以使用前端已知的枚举作为回退，但不能据此允许后端未声明的新状态或选项。

## 样式与可访问性

页面使用 Vben token 和公共组件，保持管理工具需要的信息密度与层级。

桌面端和移动端保留相同的业务操作。高风险操作需明确对象和后果。图标按钮必须有可访问名称。键盘必须能够完成主要流程。

权限隐藏不替代后端授权。响应式布局不通过删除功能解决空间问题。Tooltip 不放置完成任务所必需的信息。

## 验证

- `common/` 接口约定和 Controller 变化：相关 Vitest 与类型检查；
- Store、路由和页面逻辑变化：管理端类型检查和相关测试；
- 布局、主题、响应式和交互变化：打开受影响页面检查相应尺寸与状态；
- 登录、Session 变化、权限刷新和高风险流程：根据风险运行对应 Playwright；
- 依赖、入口或生产配置变化：运行前端构建。

不要求每次页面修改都执行所有脚本。实际命令、检查范围和未执行项按 [格式化与构建](/engineering/stack/formatting-and-build) 说明。

## 示例

下例是管理端 `common/` 中一个工作区 Controller。它只依赖 AdminApiClient，不创建 Axios 请求，也不直接写 `If-Match`。`WorkspaceView` 是应用自己的类型。

```ts
import type { AdminApiClient, ResourceRevision } from './api-client';
import type { Int64String } from './types';

export class WorkspaceController {
  constructor(private readonly api: AdminApiClient) {}

  /** 读取工作区；结果同时包含资源地址与强 ETag。 */
  load(workspaceId: Int64String) {
    return this.api.get<WorkspaceView>(
      `/workspaces/${encodeURIComponent(workspaceId)}`,
    );
  }

  /** 使用读取时保存的 revision 条件提交，不自行读取 ETag。 */
  rename(revision: ResourceRevision, name: string) {
    return this.api.patchIfMatch<WorkspaceView>(revision, { name });
  }
}
```

页面按以下顺序使用它：

```ts
const loaded = await controller.load(workspaceId);
// loaded.resource 与 loaded.etag 构成 ResourceRevision，随表单状态保存。
try {
  await controller.rename(loaded, form.name);
} catch (error) {
  // 服务器返回 412 或没有强 ETag 时，保留 form.name，重新读取后由用户确认。
}
```

对应的后端方法声明 `@IfMatch`。请求缺少 `If-Match` 时返回 428；`If-Match` 格式错误或为弱 ETag 时，在业务代码执行前返回 412。版本已经过期时，由 Repository 的条件更新发现并返回 412：

```java
@PatchMapping("/{workspaceId}")
@IfMatch
@RequirePermission(operation = WorkspaceOperations.UPDATE_VALUE)
public ApiResponse<WorkspaceView> rename(
        @PathVariable long workspaceId,
        @Valid @RequestBody WorkspaceRenameRequest request) {
    return ApiResponse.ok(WorkspaceView.from(workspaces.rename(workspaceId, request.getName())));
}
```

上例中的 `IfMatch` 位于 `com.sca4d.core.spring.web.annotation`，`ApiResponse` 位于 `com.sca4d.core.spring.web`，`RequirePermission` 位于 `com.sca4d.core.api.annotation`。`AdminApiClient` 与 `ResourceRevision` 位于管理端 `common/src/api-client.ts`，不属于 Java 框架。
