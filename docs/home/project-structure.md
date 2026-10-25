# 项目结构

本页说明脚手架的目录职责和依赖方向，适用于新增代码、新建功能或调整模块划分的开发者。

新增具体业务写在 `apps/<应用名>-server`，管理端页面写在 `frontends/<应用名>-admin/apps/<应用名>-admin/src/pages/`。需要独立选择或删除的通用功能放在 `modules/`，多个模块共同依赖的稳定接口和基础类型放在 `core/`；依赖方向始终是 `apps -> modules -> core`。

```text
core/       公共接口、基础类型和 Spring 接入
modules/    可选功能的实现与数据库内容
apps/       模块选择、应用配置和具体业务
frontends/  管理端、前端接口类型和公共代码
gateway/    独立的 HTTP、SSE、WebSocket 和回调入口
tools/      Sca4d-J Gradle 插件与构建工具
docs/       使用方法、项目规范和参考
```

## Core

`core/sca4dj-core-api` 尽量不依赖 Spring，只放多个模块需要共同理解的稳定的接口约定。`core/sca4dj-core-spring` 把这些接口约定接入 Spring、HTTP、配置和通用运行环境。Core 不实现 IAM、认证或具体业务流程。

## Modules

`modules/` 中每个 Gradle 项目表示一项可独立选择的功能。模块拥有自己的实现、表结构片段、自动配置和测试。模块之间通过公开接口协作，不使用对方的 Repository、Mapper、数据库行类型或内部包。

IAM、认证引擎和认证方法分成独立项目。`sca4dj-auth` 负责认证尝试和 Session，Password、TOTP、Passkey、Email Code、SMS Code、Federation、Shared Secret、HMAC 和 X.509 在各自的项目中实现。

## Apps

`apps/<应用名>-server` 是完整示例 App。它选择需要的模块，配置数据源、缓存、消息、存储和 Gateway，并保存只属于该 App 的 Controller、Service、Repository、Mapper 和迁移。

业务项目先组合现有模块。只有一个 App 需要的规则留在 App，不为了「方便共用」移入 Core。

## Frontends

`frontends/<应用名>-admin/common` 保存手写 TypeScript 接口约定、HTTP Client、错误转换和业务 Controller，可以在没有 Vue、Router 和 DOM 的环境中测试。`frontends/<应用名>-admin/apps/<应用名>-admin` 使用 Vben 的布局、路由和组件，业务页面位于其 `src/pages/`。前端根据权限显示菜单和操作，后端在执行请求时检查权限。

## Gateway

`gateway/sca4d-gateway` 是外部 HTTP、SSE、WebSocket 和回调的统一入口，负责把请求转发到 Server 实例并做实例负载均衡。Gateway 不读取 App 数据库，也不执行业务权限和状态机。

## Tools

`tools/` 保存 Sca4d-J 的 Gradle 构建插件。

## 依赖规则

```text
apps -> modules -> core
frontends -> HTTP API
gateway -> 实例注册与转发规则
```

Core 不依赖 Module 或 App，Module 不依赖 App。不使用事件、反射、静态全局变量或工具类隐藏反向依赖。新建模块或顶层目录前，先说明现有位置为什么不适合，并确认新的模块划分。

选择或删除模块见[组装应用](/tutorials/assemble-application)，模块职责与调用关系见[功能归属与调用关系](/standards/02_architecture)。
