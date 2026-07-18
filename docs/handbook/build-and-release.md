# 构建并发布项目

## 目标

生成后端 JAR、Gateway JAR、管理端静态文件和文档站点，并检查待发布产物。

## 前置条件

- 已完成[本地环境配置](./configuration)。
- 后端和管理端相关测试已经通过。
- 发布版本、目标环境和回滚版本已经确定。

## 1. 构建发布版后端

在仓库根目录运行：

```bash
./gradlew clean check :sca4d-server:bootJar :sca4d-gateway:bootJar \
  -Prelease -Prelease.version=1.0.0
```

把 `1.0.0` 替换为本次发布版本。发布版本格式为 `<release>-<commit>`；未提供 `release.version` 时使用最近的 Git Tag，没有 Tag 时使用 `untagged`。公共契约、数据库迁移或多实例行为发生变化时，应先运行对应的集成测试。不要用单个模块的编译结果代替受影响范围的测试。

## 2. 检查后端产物

主应用 JAR 位于 `sca4d-server/build/libs/`，Gateway JAR 位于 `sca4d-gateway/build/libs/`。发布前检查文件名、`Implementation-Version`、build-info 和启动 Banner 使用同一提交；不要发布 `plain` JAR。

## 3. 检查并构建管理端

```bash
cd sca4d-admin
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

静态文件由 `sca4d-admin/app` 的构建配置生成。部署时把它们发布到静态文件服务，并将 API 请求指向主应用或 Gateway。

## 4. 构建文档

```bash
cd docs
pnpm install --frozen-lockfile
pnpm docs:build
```

文档产物位于 `docs/.vitepress/dist/`。发布前检查首页、侧边栏、搜索、移动端菜单和内部链接。

## 5. 记录发布信息

发布记录至少包含：

| 项目 | 内容 |
| --- | --- |
| 版本 | Git 提交或发布标签 |
| 后端 | 主应用和 Gateway 产物名称 |
| 前端 | 静态文件版本 |
| 数据库 | 本次新增的 Flyway 版本 |
| 配置 | 新增、删除或改名的配置项 |
| 回滚 | 可恢复的上一版本和数据库限制 |

已经执行的 Flyway 迁移不得修改或回滚。涉及不可逆数据变化时，发布记录必须给出恢复步骤或明确说明无法回滚。

## 运行结果

后端、管理端和文档构建均成功，产物版本可追踪，部署所需配置和数据库变化已经记录。产物结构和部署拓扑见[构建、版本与产物](/operations/build-and-package)和[部署](/operations/deployment)。
