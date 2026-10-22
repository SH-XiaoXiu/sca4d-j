# 开发管理端页面

新增管理端页面先在 `common/` 定义后端契约和请求，再在 Vue 页面连接表格、表单与操作状态，最后登记路由和权限。可参考 `frontends/sca4d-admin/apps/sca4d-admin/src/pages/AccountsPage.vue`；权限与业务结果始终由后端决定。

## 从接口接到页面

1. 在 `common/` 中找到同类业务 Controller，按后端 Request、View 和错误结果补充 TypeScript 类型。64 位 ID 使用 `Int64String`。
2. 在业务 Controller 中通过 `AdminApiClient` 增加请求，处理返回数据、ETag 和错误；页面只调用这些业务方法。
3. 在 `apps/sca4d-admin/src/pages/` 增加页面，使用 `src/components/sj` 的页面、Grid 与工作区入口。通过 `useSjControllerState` 按需订阅相应 Controller；独立编辑或认证流程放在对应 `features/`。
4. 在应用路由中登记页面及后端声明的权限编码，同时维护中文和英文文案。
5. 完成提交后刷新受影响的数据；检查失败时输入是否保留、版本冲突是否要求重新读取、权限变化是否更新路由。

布局、表格和表单的通用用法见 [Vben 官方文档](https://doc.vben.pro/)。以下说明 Sca4d-J 的接口和状态约定，编码要求见[前端规范](/standards/07_frontend)。

## `common/` 契约

`frontends/sca4d-admin/common` 保存手写 TypeScript 契约、HTTP Client、错误转换、ETag、业务 Controller 和运行环境 Port。它必须能够在没有 Vue、Pinia、Router 和 DOM 的环境中执行和测试。

后端 Request、View、状态编码、错误或路由变化时，同步修改负责该接口的 `common/` 类型与 Controller。不能在页面内复制一份「临时响应类型」，也不能引入 OpenAPI 生成器覆盖手写契约。

`AdminApiClient` 集中处理 Token、`Accept-Language`、统一响应、ETag 和 HTTP 错误。Controller 把多个请求和状态变化整理成页面可以直接使用的操作。页面不跳过这两层自行创建 Axios 请求。

## Vben 应用

`apps/sca4d-admin` 使用 Vben 的布局、路由、菜单、Store、主题和公共组件。路由页面位于 `src/pages/`；需要多个组件共同完成的认证等流程位于 `src/features/`。不要把所有页面机械移动到 feature，也不要在路由文件中实现页面。

Sj 组件组合 Vben 的布局、Grid、Form、Modal 和 Drawer，集中维护管理端默认行为。业务页面声明字段、查询和命令；组件不推导权限、不调用另一个模块的内部状态，也不自行创建 HTTP 客户端。`examples/vue-vben-admin/` 只供查看 Vben 写法，不把演示页面、静态数据和演示菜单接入运行时。

## Sj 页面与工作区

| 入口 | 用途 |
| --- | --- |
| `SjPage` | 列表或连续详情，页面标题、说明与主要操作分别放在标题区 |
| `SjMasterDetail` | 角色和字典等需要连续选择对象的页面；窄屏使用对象选择入口 |
| `SjSection`、`SjDetailCard` | 连续章节与独立摘要，普通章节不嵌套装饰卡片 |
| `SjTabs`、`SjLocaleTabs` | 同一页面的工作区与多语言字段，已访问内容保留状态 |
| `useSjVxeGrid` | 查询表单、Grid 工具栏、密度与操作列的默认规则 |
| `SjDataGrid` | 展示 Controller 已读取的不可变结果；分页变化通知调用方读取 |
| `useSjForm` | 普通 schema 表单的标签、控件宽度与字段间距 |
| `SjResourceEditor` | 创建使用居中 Modal，已有资源使用右侧 Drawer |
| `SjDialog` | 动作认证或小型确认，不能代替普通资源编辑 |
| `SjRowActions`、`SjPageActions`、`SjConfirmSwitch` | 明确动作、权限、限制原因、危险确认和提交状态 |
| `SjResourceState`、`SjResourceStatus` | 分别展示请求阶段与对象状态 |
| `SjFileAvatar`、`SjAvatarEditor`、`SjResourceReference` | 文件读取、头像维护与稳定资源引用 |

`SjResourceEditor` 的 `open` 由调用方通过 `v-model:open` 保存。`create` 决定创建或编辑模式，`loading` 用于初始值读取，`saving` 用于写入，`dirty` 用于未保存草稿；`submit` 请求当前流程校验和提交，成功关闭由该流程决定，`closed` 用于释放秘密、预览和已消费的链接参数。查看模式使用 `readOnly`，不提供保存入口。

工作区使用 `sm/md/lg/xl` 标准尺寸，创建常用 `lg` 或 `xl`，普通编辑常用 `md`。使用 `useSjCreateModal` 或 `useSjDrawer` 定义确实独立的工作区时，继续采用相同尺寸、正文滚动和底部操作规则。

业务流程组件按对象与用途命名，例如 `AccountCreateModal`、`RoleEditor` 和 `EmailTemplateEditor`。组件通过明确的 Props、Emits 或公开打开方法与页面协作，不把页面的整个状态对象传入子组件。

## 从 Controller 快照接到列表

```ts
const accounts = useSjControllerState(businessApplication.accounts);
const session = useSjAccess();
```

`useSjControllerState` 返回只读响应式快照，模板自动解包；订阅随组件作用域释放。请求读取仍调用 `AccountsController` 的公开操作。路由页面负责筛选、页码、选中对象和工作区打开状态，认证与权限仍来自同一 Session。

查询较复杂时参考 `AccountsPage.vue` 的 `useSjVxeGrid`。已经读取完整集合或服务端分页结果时使用 `SjDataGrid`，提供 `columns`、`items` 和稳定的 `keyField`；服务端分页提供 `pagination` 并接收 `pageChange`。完整集合不伪装成远程分页接口。

文本左对齐、数字右对齐，状态与操作居中；操作列固定右侧。筛选控件按一列、两列和最多四列排列，超过一行可以展开。页面级命令位于标题区，列表创建位于工具栏，章节命令位于章节标题右侧；同一命令只出现一次。

`SjRowAction` 明确声明动作名称、图标、执行函数、可见性、禁用原因、loading 和确认内容。未授权动作隐藏，已授权但状态不允许的动作禁用；删除等危险操作显示具体对象和后果。模板不能用按钮是否隐藏代替后端授权。

## 编辑源码与认证因素

Email 模板直接保存每种 Locale 的 `subject`、`text` 和 `html` 三个源码字段，预览请求使用选中的 `source`。编辑和保存不得把原始 HTML 转成富文本再转回，也不能自动用纯文本覆盖已有 HTML。预览由后端替换变量并清洗 HTML，页面在无脚本权限的 iframe 内查看渲染结果。

`AuthenticationProofPanel` 复用密码、TOTP、Passkey 和验证码输入。认证方法与 Challenge 的公开展示信息由调用方提供，具体登录或受保护写入由调用方执行；输入限于当前面板，切换认证操作、方法或离开组件后清除。发送验证码与因素提交各自显示等待状态。

## Session 与权限

认证 Store 统一负责 Token 保存、Session 恢复、刷新、注销和 Session 变化后的状态更新。页面不各自读取浏览器存储或实现登录恢复。

菜单、路由和操作按钮使用当前 Session 返回的权限编码。权限变化后，统一刷新用户、权限、菜单、动态路由和页签；不能只修改页面内的权限数组。前端权限控制用于避免展示不可执行操作，后端 Controller 仍负责最终授权。

路由的 `authority` 和页面权限判断使用后端规范编码，例如 `PLATFORM:ACCOUNTS:READ`。权限编码保持大写和冒号分隔，不转换成另一种大小写或从角色名称推导。

个人资料中的通知语言从 `/system/locales` 读取支持选项，通知类型与渠道使用本地化名称。必发渠道显示发送要求，可选渠道才提供开关。业务区域由具体应用定义；示例没有区域目录时只读显示当前区域，不要求用户填写内部编码，也不把业务区域当作国家或语言。

JWT 与在线 Session Token 的选择由统一认证代码处理。普通页面不根据 Token 内容推导账号、Principal 或权限，也不自行解析 JWT。

## 资源版本与错误

接口要求版本前置条件时，读取响应中的强 ETag，并通过 `If-Match` 提交修改。页面使用 `common/` Controller 保存与编辑资源快照对应的 ETag；缺少所需 ETag、服务器返回 412 或要求前置条件时，重新读取资源并让用户确认，不自动覆盖。其他接口按自身契约提交。

`AdminError` 区分未认证、无权限、不存在、冲突、前置条件、服务不可用、网络失败，以及写入已完成后的刷新失败。页面根据错误种类保留数据、关闭失效详情、返回登录页或提供重试。不能用一个全局 toast 吞掉所有失败。`useSjPresentation` 使用同一 Vben Locale 显示业务文案、浏览器时区时间和安全错误；界面不再维护第二套语言切换逻辑。

## ID、时间和秘密

- 后端 64 位 ID 统一使用 `Int64String`，不调用 `Number(id)`，不按大小排序字符串 ID。
- 时间通过统一入口解析和显示，不在页面分别拼接格式或混用时区。
- 密码、验证码、Session Token、Client Secret 和 Passkey 响应只在当前操作中使用，不写入日志、持久化 Store、路由参数或普通错误对象。
- 敏感设置只显示是否已经配置；后端不返回的原值不能用占位字符串再提交。

## 页面状态

列表和详情处理加载、空结果、失败、无权限、资源失效和刷新。提交操作各自保留 loading 和错误状态，一个 Provider 或认证方法的请求不能禁用其他无关入口。

创建、修改、删除、启停和角色分配成功后，刷新受影响的列表、详情和共享状态。刷新失败时说明当前数据可能过期，不提前显示已经全部同步成功。

页面文案同时维护 `zh-CN` 与 `en-US`。固定状态的标签可以使用前端已知枚举作为失败回退，但不能据此允许后端没有声明的新状态或选项。

## 样式与可访问性

页面使用 Vben token 和公共组件，保持管理工具需要的密度与信息层级。桌面和移动端保留相同业务操作；高风险操作明确对象和后果；图标按钮有可访问名称；键盘可以完成主要流程。

权限隐藏不替代后端授权，响应式布局不以删除功能解决空间问题，Tooltip 不提供完成任务所必需的信息。

## 验证

- `common/` 契约和 Controller 变化：相关 Vitest 与类型检查；
- Store、路由和页面逻辑变化：管理端类型检查和相关测试；
- 布局、主题、响应式和交互变化：列出受影响页面、操作与状态，由开发者人工验收；
- 浏览器和 Playwright：仅在任务明确要求时执行指定流程；
- 依赖、入口或生产配置变化：运行前端构建。

不固定要求每次页面修改都执行所有脚本。实际命令、检查范围和未执行项按[格式化与构建](/standards/09_build)说明。

## HTTP 请求

`AdminApiClient` 通过 HttpPort 发送请求，并统一携带语言、认证信息及资源响应头。错误映射保留状态、错误码、调用标识和后端 `data`；页面在确认结构后使用错误数据，不把完整错误响应写入日志。

普通请求体按 JSON 发送。需要以 `file` 字段上传浏览器文件时使用 `postMultipart` 或 `patchMultipart`；浏览器 HttpPort 将 FormData 原样交给 Fetch，由浏览器生成 Content-Type 和 boundary，不能对它调用 JSON 序列化。

## 权限

前端根据后端返回的权限决定入口与操作展示，后端继续执行最终授权检查。路由元数据中的 `menuVisibleWithForbidden` 只控制无权菜单是否可见，不能把有权页面替换成 403。

配置禁止访问组件时，无权路径保留为 403 页面，默认从菜单隐藏；父级路由被拒绝时，子级不能绕过。隐藏子项后没有内容的纯目录不显示，自身可导航的父页面仍保留。路由生成不会修改调用方保存的原始定义。

详情页可以在 `activePath` 中使用 `:paramName` 激活所属菜单，参数从当前路由取得并编码；缺少参数时使用当前路由路径。

## 文件传输

`FilesController` 申请、上传和完成头像文件，并缓存短期读取地址。构造时显式提供当前 Session 标识，普通 URL 缓存不能在不同 Session 之间复用。

同一微任务中到达的读取请求合并签发，同一文件的在途请求共用结果；超过 100 个文件时分批请求。图片加载失败时可要求强制换签。对象上传原样使用后端签发的请求头，不添加管理端 Authorization。

退出、Session 失效和切换登录时必须调用 `clear()`，清除缓存并拒绝未完成请求。迟到的旧请求结果不会回填缓存；同一 Session 的 access Token 刷新不需要清理文件缓存。
