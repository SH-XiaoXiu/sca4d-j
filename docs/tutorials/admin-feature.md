# 开发管理端页面

本页以工作区 Workspace 中的文档 Document 列表与编辑为例，说明如何在 Sca4d-J 管理端新增一个页面。
读者是需要扩展管理端的前端开发者。

## 场景

后端已经为 Workspace 提供 Document 的列表和修改接口。
管理端需要一个页面：按 Workspace 查看 Document 列表，打开单个 Document 修改标题与正文。

页面开发需要回答四个问题：

- 后端 Request、View 和错误结果在前端由谁保存。
- 请求由谁发送，ETag 与错误由谁处理。
- 页面如何订阅状态，如何登记路由和权限。
- 保存失败时，输入、版本与权限如何处理。

权限与业务结果始终由后端决定。
开始前，先确认后端已经声明该页面需要的 Request、View、错误结果和权限编码。
可参考具体应用中已有的列表页面实现。

Workspace、Document、接口路径与权限编码在本页中都是由具体应用定义的示例，脚手架不提供这些类型。

## 实现步骤

### 1. 在 `common/` 定义接口约定

`frontends/<应用名>-admin/common` 保存手写 TypeScript 接口约定、HTTP Client、错误转换、ETag、业务 Controller 和运行环境 Port。
它必须能够在没有 Vue、Pinia、Router 和 DOM 的环境中执行和测试。

后端 Request、View、状态编码、错误或路由变化时，同步修改负责该接口的 `common/` 类型与 Controller。
不能在页面内复制一份「临时响应类型」，也不能引入 OpenAPI 生成器覆盖手写接口约定。

先在 `common/src/types.ts` 同类位置补充类型。
64 位 ID 使用 `Int64String`。

```ts
import type { Int64String } from '@app/<应用名>-admin-common';

// 由具体应用定义：与后端 View 字段一致。
export interface WorkspaceDocument {
  readonly id: Int64String;
  readonly workspaceId: Int64String;
  readonly title: string;
  readonly body: string;
}

// 由具体应用定义：与后端 Request 字段一致。
export interface ReplaceDocumentInput {
  readonly title: string;
  readonly body: string;
}
```

### 2. 编写业务 Controller

`AdminApiClient` 集中处理 Token、`Accept-Language`、统一响应、ETag 和 HTTP 错误。
Controller 把多个请求和状态变化整理成页面可以直接使用的操作。
页面不跳过这两层，自行创建 Axios 请求。

Controller 继承 `ObservableController`，通过 `publish` 发布不可变状态。
读取详情时保存响应中的 `etag` 与 `resource`，修改时交给 `patchIfMatch`。

```ts
import type {
  AdminApiClient,
  Int64String,
  ResourcePhase,
  ResourceRevision,
} from '@app/<应用名>-admin-common';

import {
  AdminError,
  mapNetworkError,
  ObservableController,
  resourcePhaseForError,
} from '@app/<应用名>-admin-common';

// 由具体应用定义：页面状态。
export interface DocumentsState {
  readonly phase: ResourcePhase;
  readonly error: AdminError | null;
  readonly items: readonly WorkspaceDocument[];
  readonly selected: WorkspaceDocument | null;
  readonly selectedRevision: ResourceRevision | null;
}

// 由具体应用定义。
export class DocumentsController extends ObservableController<DocumentsState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      phase: 'idle',
      error: null,
      items: Object.freeze([]),
      selected: null,
      selectedRevision: null,
    });
  }

  /** 读取一个 Workspace 的 Document 列表。 */
  async load(workspaceId: Int64String): Promise<void> {
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const result = await this.api.get<WorkspaceDocument[]>(
        `/workspaces/${encodeURIComponent(workspaceId)}/documents`,
      );
      this.publish({
        ...this.state,
        phase: result.data.length === 0 ? 'empty' : 'ready',
        items: Object.freeze([...result.data]),
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 读取单个 Document，并保存与该快照对应的 ETag。 */
  async open(workspaceId: Int64String, id: Int64String): Promise<void> {
    try {
      const result = await this.api.get<WorkspaceDocument>(
        `/workspaces/${encodeURIComponent(workspaceId)}/documents/${encodeURIComponent(id)}`,
      );
      this.publish({
        ...this.state,
        selected: result.data,
        selectedRevision: Object.freeze({
          etag: result.etag,
          resource: result.resource,
        }),
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 使用 If-Match 提交修改；成功后用响应中的新 ETag 替换旧值。 */
  async save(input: ReplaceDocumentInput): Promise<void> {
    const revision = this.state.selectedRevision;
    if (!revision) throw new Error('No document is loaded');
    try {
      const result = await this.api.patchIfMatch<WorkspaceDocument>(
        revision,
        input,
      );
      this.publish({
        ...this.state,
        selected: result.data,
        selectedRevision: Object.freeze({
          etag: result.etag,
          resource: result.resource,
        }),
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private fail(error: unknown): void {
    const value = error instanceof AdminError ? error : mapNetworkError(error);
    this.publish({
      ...this.state,
      phase: resourcePhaseForError(value),
      error: value,
    });
  }
}
```

Controller 的 `selectedRevision` 与 `selected` 一同更新，保证 ETag 对应页面正在编辑的快照。
列表中的对象不保存 ETag，编辑前必须通过 `open` 读取。

### 3. 在 Vue 页面订阅状态

页面位于 `frontends/<应用名>-admin/apps/<应用名>-admin/src/pages/`。
页面使用 `src/components/el` 的页面、Grid 与工作区入口。
独立编辑或认证流程放在对应的 `features/` 中。
通过 `useControllerState` 按需订阅相应 Controller。

```ts
// features/documents.ts，由具体应用定义。
import { useControllerState } from '#/features/controller-state';

export function useDocumentsFeature() {
  const documents = useControllerState(businessApplication.documents);
  return {
    documents,
    load: (workspaceId: Int64String) =>
      businessApplication.documents.load(workspaceId),
    open: (workspaceId: Int64String, id: Int64String) =>
      businessApplication.documents.open(workspaceId, id),
    save: (input: ReplaceDocumentInput) =>
      businessApplication.documents.save(input),
  };
}
```

`useControllerState` 返回只读响应式快照，模板自动解包。
订阅随组件作用域释放。
请求读取仍调用 Controller 的公开操作。
`businessApplication` 的装配方式参考 `features/accounts.ts`。

页面使用 `AppPage` 作为入口，`useAppVxeGrid` 提供查询表单、Grid 工具栏、密度与操作列的默认规则。

```vue
<script setup lang="ts">
import { onMounted } from 'vue';

import { AdminError } from '@app/<应用名>-admin-common';
import { message } from 'ant-design-vue';

import { AppPage, AppRowActions, useAppDrawer } from '#/components/app';
import { useDocumentsFeature } from '#/features/documents';

const props = defineProps<{ workspaceId: string }>();
const { documents, load, open, save } = useDocumentsFeature();

const [Drawer, drawerApi] = useAppDrawer({ size: 'md' });

onMounted(() => load(props.workspaceId));

async function edit(id: string) {
  await open(props.workspaceId, id);
  drawerApi.open();
}

async function submit(values: { title: string; body: string }) {
  try {
    await save(values);
    await load(props.workspaceId);
    drawerApi.close();
  } catch (error) {
    // 失败时保留抽屉与输入，由错误种类决定后续动作。
    if (error instanceof AdminError && error.kind === 'versionConflict') {
      message.warning('Document 已被修改，请重新读取后再保存');
      return;
    }
    throw error;
  }
}
</script>
```

模板部分使用 `documents.items` 渲染表格，用 `AppRowActions` 渲染每行操作，用 `Drawer` 显示编辑表单。
`useAppCreateModal` 与 `useAppDrawer` 提供标准尺寸、正文滚动和底部操作规则。

### 4. 登记路由、权限与文案

在应用路由 `src/router/routes/modules/` 中登记页面及后端声明的权限编码，同时维护 `zh-CN` 与 `en-US` 文案。

```ts
// 由具体应用定义：权限编码由后端 Operation 声明生成。
{
  name: 'WorkspaceDocuments',
  path: '/workspaces/:workspaceId/documents',
  component: () => import('#/pages/DocumentsPage.vue'),
  meta: {
    title: 'page.documents.title',
    authority: [OPERATION_CODES.documentsRead],
  },
}
```

路由的 `authority` 和页面权限判断使用后端规范编码，例如 `PLATFORM:ACCOUNTS:READ`。
权限编码保持大写和冒号分隔，不转换成另一种大小写，也不从角色名称推导。
示例应用的权限常量位于 `common/src/operation-codes.generated.ts`，页面引用 `OPERATION_CODES`。

### 5. 提交后刷新

创建、修改、删除、启停和角色分配成功后，刷新受影响的列表、详情和共享状态。
刷新失败时，说明当前数据可能过期，不提前显示已经全部同步成功。

布局、表格和表单的通用用法见 [Vben 官方文档](https://doc.vben.pro/)。
编码要求见[前端规范](/standards/07_frontend)。

## 验证

- `common/` 接口约定和 Controller 变化：运行相关 Vitest 与类型检查。
- Store、路由和页面逻辑变化：运行管理端类型检查和相关测试。
- 布局、主题、响应式和交互变化：列出受影响的页面、操作与状态，由开发者人工验收。
- 浏览器和 Playwright：仅在任务明确要求时执行指定流程。
- 依赖、入口或生产配置变化：运行前端构建。

示例命令，在 `frontends/<应用名>-admin/` 目录执行：

```bash
pnpm typecheck
pnpm test
pnpm build
```

不要求每次页面修改都执行所有脚本。
实际命令、检查范围和未执行项，按[格式化与构建](/standards/09_build)说明。

人工验收按下表列出状态：

| 操作 | 预期结果 |
| --- | --- |
| 打开页面 | 列表显示加载状态，随后显示 Document 或空结果 |
| 打开单个 Document | 编辑抽屉显示标题与正文 |
| 修改并保存 | 抽屉关闭，列表显示新标题 |
| 无 `WORKSPACE:DOCUMENTS:READ` 权限（示例编码） | 路由不可进入，菜单按配置隐藏 |

## 失败结果与限制

### 资源版本与错误

接口要求版本前置条件时，读取响应中的强 ETag，并通过 `If-Match` 提交修改。
页面使用 `common/` Controller 保存与编辑资源快照对应的 ETag。

`AdminApiClient` 的 `patchIfMatch`、`putIfMatch`、`deleteIfMatch` 要求 `ResourceRevision` 中带有强 ETag。
ETag 缺失、以 `W/` 开头或不带引号时，请求不会发出，并抛出普通 `Error`。

缺少所需 ETag、服务器返回 412，或要求前置条件时，重新读取资源并让用户确认，不自动覆盖。
后端用 `@IfMatch` 要求强 ETag，弱 ETag、通配符或非整数版本号返回 412。
其他接口按自身约定提交。

`AdminError` 的 `kind` 取值如下：

| `kind` | 来源 |
| --- | --- |
| `authentication` | 未认证 |
| `permission` | 无权限 |
| `notFound` | 不存在 |
| `conflict` | 冲突（409） |
| `versionConflict` | 版本不一致（412） |
| `precondition` | 缺少前置条件（428） |
| `validation` | 参数校验失败 |
| `unavailable` | 服务不可用（5xx） |
| `network` | 网络失败 |
| `unknown` | 其他 |

`resourcePhaseForError` 把 `permission` 映射为 `forbidden`，把 `versionConflict` 映射为 `conflict`。
页面根据错误种类保留数据、关闭失效详情、返回登录页或提供重试。
不能用一个全局 toast 吞掉所有失败。

错误映射保留状态、错误码、调用标识和后端 `data`。
页面在确认结构后使用错误数据，不把完整错误响应写入日志。

### 页面状态

列表和详情需要处理加载、空结果、失败、无权限、资源失效和刷新。
提交操作各自保留 loading 和错误状态。
一个 Provider 或认证方法的请求，不能禁用其他无关入口。

页面文案同时维护 `zh-CN` 与 `en-US`。
固定状态的标签可以使用前端已知枚举作为失败回退。
但不能据此允许后端没有声明的新状态或选项。

### ID、时间和秘密

- 后端 64 位 ID 统一使用 `Int64String`，不调用 `Number(id)`，不按大小排序字符串 ID。
- 时间通过统一入口解析和显示，不在页面分别拼接格式，也不混用时区。
- 密码、验证码、Session Token、Client Secret 和 Passkey 响应只在当前操作中使用。不写入日志、持久化 Store、路由参数或普通错误对象。
- 敏感设置只显示是否已经配置。后端不返回的原值，不能用占位字符串再提交。

### Session 与权限

认证 Store 统一负责 Token 保存、Session 恢复、刷新、注销和 Session 变化后的状态更新。
页面不各自读取浏览器存储，也不实现登录恢复。

菜单、路由和操作按钮使用当前 Session 返回的权限编码。
页面通过 `useBusinessSession`（`features/access.ts`）读取权限快照，权限判断由 Vben access store 负责。
权限变化后，统一刷新用户、权限、菜单、动态路由和页签。
不能只修改页面内的权限数组。
前端权限控制用于避免展示不可执行的操作，后端 Controller 仍负责最终授权。

前端根据后端返回的权限决定入口与操作展示。
路由元数据中的 `menuVisibleWithForbidden` 只控制无权菜单是否可见，不能把有权页面替换成 403。

配置禁止访问组件时，无权路径保留为 403 页面，默认从菜单隐藏。
父级路由被拒绝时，子级不能绕过。
隐藏子项后没有内容的纯目录不显示，自身可导航的父页面仍保留。
路由生成不会修改调用方保存的原始定义。

详情页可以在 `activePath` 中使用 `:paramName` 激活所属菜单。
参数从当前路由取得并编码。
缺少参数时使用当前路由路径。

JWT 与在线 Session Token 的选择，由统一认证代码处理。
普通页面不根据 Token 内容推导账号、Principal 或权限，也不自行解析 JWT。

个人资料中的通知语言从 `/system/locales` 读取支持选项，通知类型与渠道使用本地化名称。
必发渠道显示发送要求，可选渠道才提供开关。

业务区域由具体应用定义。
示例没有区域目录时，只读显示当前区域，不要求用户填写内部编码。
也不把业务区域当作国家或语言。

### 行操作与危险操作

`AppRowAction` 明确声明动作名称、图标、执行函数、可见性、禁用原因、loading 和确认内容。
未授权的动作隐藏，已授权但状态不允许的动作禁用。
删除等危险操作需显示具体对象和后果。
模板不能用按钮是否隐藏代替后端授权。

页面级命令位于标题区，列表创建位于工具栏，章节命令位于章节标题右侧。
同一命令只出现一次。

### HTTP 请求

`AdminApiClient` 通过 HttpPort 发送请求，并统一携带语言、认证信息及资源响应头。

普通请求体按 JSON 发送。
需要以 `file` 字段上传浏览器文件时，使用 `postMultipart` 或 `patchMultipart`。
浏览器 HttpPort 将 FormData 原样交给 Fetch，由浏览器生成 Content-Type 和 boundary。
不能对它调用 JSON 序列化。

### 文件传输

`FilesController` 申请、上传和完成头像文件，并缓存短期读取地址。
构造时显式提供当前 Session 标识。
普通 URL 缓存不能在不同 Session 之间复用。

同一微任务中到达的读取请求合并签发，同一文件的在途请求共用结果。
超过 100 个文件时分批请求。
图片加载失败时，可以要求强制换签。
对象上传原样使用后端签发的请求头，不添加管理端 Authorization。

退出、Session 失效和切换登录时，必须调用 `clear()`，清除缓存并拒绝未完成请求。
迟到的旧请求结果不会回填缓存。
同一 Session 的 access Token 刷新，不需要清理文件缓存。

### 编辑源码与认证因素

Email 模板直接保存每种 Locale 的 `subject`、`text` 和 `html` 三个源码字段。
预览请求使用选中的 `source`。

编辑和保存时，不得把原始 HTML 转成富文本再转回，也不能自动用纯文本覆盖已有 HTML。
预览由后端替换变量并清洗 HTML。
页面在无脚本权限的 iframe 内查看渲染结果。

`AuthenticationProofPanel` 复用密码、TOTP、Passkey 和验证码输入。
认证方法与 Challenge 的公开展示信息由调用方提供。
具体的登录或受保护写入由调用方执行。

输入限于当前面板。
切换认证操作、方法或离开组件后，输入内容会被清除。
发送验证码与因素提交各自显示等待状态。

## Vben 应用与 EL 组件

`apps/<应用名>-admin` 使用 Vben 的布局、路由、菜单、Store、主题和公共组件。
路由页面位于 `src/pages/`。
需要多个组件共同完成的认证等流程，位于 `src/features/`。
不要把所有页面机械移动到 feature，也不要在路由文件中实现页面。

`src/components/el` 中的 EL 组件组合 Vben 的布局、Grid、Form、Modal 和 Drawer，集中维护管理端的默认行为。
业务页面声明字段、查询和命令。
组件不推导权限、不调用另一个模块的内部状态，也不自行创建 HTTP 客户端。

`examples/vue-vben-admin/` 只供查看 Vben 写法。
不要把其中的演示页面、静态数据和演示菜单接入运行时。

| 入口 | 用途 |
| --- | --- |
| `AppPage` | 列表或连续详情，页面标题、说明与主要操作分别放在标题区 |
| `AppMasterDetail` | 需要连续选择对象的页面 |
| `AppSection`、`AppDetailCard` | 连续章节与独立摘要，普通章节不嵌套装饰卡片 |
| `AppTabs`、`AppLocaleTabs` | 同一页面的工作区与多语言字段 |
| `useAppVxeGrid` | 查询表单、Grid 工具栏、密度与操作列的默认规则 |
| `useAppCreateModal` | 创建使用居中 Modal，尺寸为 `md`、`lg`、`xl` |
| `useAppDrawer` | 已有资源使用右侧 Drawer，尺寸为 `sm`、`md`、`lg`、`xl` |
| `AppRowActions`、`AppPageActions`、`AppConfirmSwitch` | 明确动作、权限、限制原因、危险确认和提交状态 |
| `AppResourceStatus` | 展示对象状态 |
| `AppFileAvatar`、`AppResourceReference` | 文件读取与稳定资源引用 |

创建通常使用 `lg` 或 `xl`，普通编辑通常使用 `md`。
定义确实独立的工作区时，继续采用相同尺寸、正文滚动和底部操作规则。

业务流程组件按对象与用途命名，例如 `AccountCreateModal` 和 `RoleEditor`。
组件通过明确的 Props、Emits 或公开打开方法与页面协作。
不把页面的整个状态对象传入子组件。

路由页面负责筛选、页码、选中对象和工作区打开状态。
认证与权限仍来自同一 Session。

查询较复杂时，参考具体应用中已有列表页的 `useAppVxeGrid` 用法。
服务端分页结果由 Controller 读取，页面只展示不可变快照。
完整集合不能伪装成远程分页接口。

文本靠左，数字靠右，状态与操作居中。
操作列固定在右侧。
筛选控件按一列、两列和最多四列排列，超过一行时可以展开。

### 样式与可访问性

页面使用 Vben token 和公共组件，保持管理工具需要的密度与信息层级。
桌面和移动端保留相同的业务操作。
高风险操作需明确对象和后果。
图标按钮需要可访问名称。
键盘可以完成主要流程。

权限隐藏不替代后端授权。
响应式布局不以删除功能解决空间问题。
Tooltip 不提供完成任务所必需的信息。

## 相关页面

- [开发后端业务接口](/tutorials/backend-feature)
- [接入身份与权限](/tutorials/identity-and-permission)
- [编写与运行测试](/tutorials/testing)
- [前端规范](/standards/07_frontend)
- [格式化与构建](/standards/09_build)
- [HTTP 通用约定](/reference/http/common)
- [错误模型](/capabilities/foundation/error-model)
