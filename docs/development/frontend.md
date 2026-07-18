# 添加管理端页面

以下步骤以系统菜单页面为例，添加 TypeScript API、路由、权限检查、页面状态和测试。

## 前置条件

- Node.js 24 或更高版本已安装。
- 已在 `sca4d-admin` 运行 `pnpm install`。
- 后端接口已经出现在 `/v3/api-docs`。
- 页面需要的权限编码已经加入后端 `PermissionCatalog`。

## 1. 更新 OpenAPI 类型

启动后端后执行：

```bash
cd sca4d-admin
SCA4D_OPENAPI_URL=http://localhost:8080/v3/api-docs pnpm api:generate
pnpm api:check
```

生成文件位于 `common/src/generated/openapi.ts`。不要手工修改该文件。`api:generate` 脚本的默认地址为 `http://localhost:8087/v3/api-docs`；主应用使用默认端口 `8080` 时必须显式设置 `SCA4D_OPENAPI_URL`。

后端 `long` ID 在 TypeScript 中使用：

```ts
export type Id = string;
```

不要调用 `Number(id)`，JavaScript number 无法安全保存所有 `long` 值。

## 2. 定义页面使用的类型

系统菜单类型位于 `common/src/types.ts`：

```ts
export interface ManagedMenuView {
  menuId: Id;
  parentId: Id | null;
  routeCode: string | null;
  pathTemplate: string | null;
  permissionCode: string | null;
  titles: Record<string, string>;
  icon: MenuIcon;
  sortOrder: number;
  visible: boolean;
  version: Id;
  children: ManagedMenuView[];
}

export interface MenuWriteInput {
  parentId?: Id;
  routeCode?: string;
  titles: Record<string, string>;
  icon: MenuIcon;
  visible: boolean;
}
```

手写类型只用于页面需要的稳定接口。OpenAPI 已生成的 Request 或 View 不要再复制一份同名类型。

## 3. 添加 API

创建 `common/src/apis/menus.ts`：

```ts
import type { ApiClient } from "../api-client";
import type {
  Id,
  ManagedMenuView,
  MenuWriteInput,
  NavigationRouteView
} from "../types";

export function createSystemMenuApi(client: ApiClient) {
  const root = "/mg/system/menus";
  return {
    menus(): Promise<ManagedMenuView[]> {
      return client.get(root);
    },
    routeOptions(): Promise<NavigationRouteView[]> {
      return client.get(`${root}/route-options`);
    },
    create(input: MenuWriteInput, idempotencyKey: string): Promise<Id> {
      return client.post(root, { ...input, idempotencyKey });
    },
    update(menuId: Id, input: MenuWriteInput, version: Id): Promise<void> {
      return client.put(`${root}/${menuId}`, { ...input, version });
    },
    delete(menuId: Id, version: Id, idempotencyKey: string): Promise<void> {
      return client.request({
        path: `${root}/${menuId}`,
        method: "DELETE",
        body: { version, idempotencyKey }
      });
    }
  };
}
```

在 `common/src/index.ts` 导出：

```ts
export * from "./apis/menus";
```

在 `app/src/api/client.ts` 使用应用共用的 `ApiClient` 创建实例：

```ts
export const systemMenuApi = createSystemMenuApi(apiClient);
```

页面不得单独创建 Axios 实例。共用 `ApiClient` 负责 token、语言、错误解析、401 和 `X-Client-State-*`。

## 4. 添加路由

在 `app/src/router/index.ts` 添加列表和详情路由：

```ts
{
  path: "system/menus",
  name: "system-menus",
  component: () => import("../pages/SystemMenuPage.vue"),
  meta: {
    routeCode: "system.menus",
    permission: "system:menus:read",
    principalType: "PLATFORM",
    workspace: true,
    cacheName: "SystemMenuPage"
  }
},
{
  path: "system/menus/:menuId",
  name: "system-menu-detail",
  component: () => import("../pages/SystemMenuPage.vue"),
  meta: {
    permission: "system:menus:read",
    principalType: "PLATFORM",
    workspace: true,
    tabMode: "full-path",
    cacheName: "SystemMenuPage"
  }
}
```

`routeCode` 必须与后端 `NavigationRouteContributor` 注册值一致。菜单保存 routeCode，不保存 Vue 组件路径。

代码路由不直接构造多语言标题 Map。后端使用 `navigation.<routeCode>` 消息键解析 `NavigationRouteView.defaultTitles`；管理员保存菜单后，数据库标题不再随默认消息变化。

## 5. 加载页面数据

`SystemMenuPage.vue` 同时加载菜单树和可选路由：

```ts
const loading = ref(false);
const menus = ref<ManagedMenuView[]>([]);
const routeOptions = ref<NavigationRouteView[]>([]);

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [menuTree, routes] = await Promise.all([
      systemMenuApi.menus(),
      systemMenuApi.routeOptions()
    ]);
    menus.value = menuTree;
    routeOptions.value = routes;
  } catch (error) {
    message.error(
      isApiError(error) ? error.message : t("menu.loadFailed")
    );
  } finally {
    loading.value = false;
  }
}

onMounted(load);
```

页面需要显示加载中、加载失败、空菜单和正常列表。请求失败时保留当前数据，用户可以重新加载。

## 6. 检查操作权限

```ts
const auth = useAuthStore();
const canManage = computed(() =>
  auth.hasPermission("system:menus:manage")
);
```

`canManage` 控制创建、编辑和删除按钮。后端仍会在每次请求检查 `system:menus:manage`。

## 7. 处理创建和更新

创建按钮打开表单时生成一次幂等 key：

```ts
const createKey = ref<string | null>(null);

function openCreate(parentId?: Id): void {
  createKey.value = createIdempotencyKey();
  form.parentId = parentId;
  editorOpen.value = true;
}
```

提交失败后保留相同 key，用户重试时不会创建第二条菜单。关闭表单并重新创建时生成新 key。

更新使用列表返回的 version：

```ts
await systemMenuApi.update(
  editing.value.menuId,
  {
    parentId: form.parentId,
    routeCode: form.routeCode,
    titles: { "zh-CN": form.zhCn, "en-US": form.enUs },
    icon: form.icon,
    visible: form.visible
  },
  editing.value.version
);
```

409 时显示错误并重新调用 `load()`。不要自动使用旧 version 再次提交。

## 8. 处理删除

删除前显示菜单标题，并说明全部下级菜单会一起删除。确认后调用：

```ts
await systemMenuApi.delete(
  menu.menuId,
  menu.version,
  createIdempotencyKey()
);
```

请求进行时禁用确认按钮。成功后关闭详情路由并重新加载菜单树。

## 9. 添加翻译

页面文字分别添加到：

```text
app/src/i18n/messages/zh-CN.ts
app/src/i18n/messages/en-US.ts
```

代码中只使用翻译 key：

```ts
message.success(t("menu.createSuccess"));
```

菜单标题由后端 `/me.navigation` 返回当前语言结果。编辑器从系统语言目录读取可维护语言，并且一次只展开一种语言；新增语言不需要修改表单布局或前端类型。

## 10. 检查窄屏和键盘操作

- 表格在窄屏可以横向滚动，操作按钮仍可点击；
- 表单标签和错误信息可见；
- 图标按钮通过 tooltip 和 `aria-label` 提供可访问名称；
- Tab 可以进入表单、确认和取消按钮；
- 颜色不是显示状态的唯一方式。

## 11. 添加测试

common API 测试检查 Method、Path 和 body：

```bash
cd sca4d-admin
pnpm --filter @sca4d/admin-common test
```

页面和 store 测试检查：

- 没有管理权限时不显示写操作；
- 加载、空列表和失败提示；
- 创建重试复用 idempotency key；
- 更新发送当前 version；
- 409 后重新加载；
- 切换 PLATFORM/TENANT 后使用新的 `/me.navigation`。

运行全部检查：

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm e2e
```

## 运行结果

菜单页面可以加载路由选项，创建请求携带幂等 key，更新和删除携带当前 version，权限不足时不显示写操作，409 后重新加载菜单树。

## 失败处理

`api:check` 失败时先确认后端正在提供当前分支的 OpenAPI。路由无法显示时核对 `routeCode`、`principalType`、permission 和 `/me.navigation`。

## 下一步

使用浏览器分别检查桌面和窄屏布局，并完成真实后端联调。
