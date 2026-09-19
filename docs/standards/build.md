# 格式化与构建规范

格式化、编译、测试、审计和装配用于发现不同问题。修改完成后根据受影响内容选择对应检查，不把所有命令组成每次都运行的固定套餐。

## 以项目现有入口为准

Java 项目通过 Sca4d-J 构建插件把自己的编译、测试、集成测试、格式和审计任务登记到统一 Gradle 阶段。根任务可以覆盖整个仓库，但局部开发优先运行直接受影响的项目任务。

常见选择如下：

- Java 类型、签名或逻辑变化：`./gradlew :<project>:compileJava`，再运行相关测试；
- 某个项目的 Java、SQL、XML 或 Gradle 脚本格式变化：`./gradlew :<project>:spotlessCheck`；
- Spring 装配或配置变化：运行该项目的最小上下文或集成测试；
- 跨多个项目的公开契约变化：增加直接消费项目的编译和测试；
- 发布或确实影响全仓库的变化：使用根 `compile`、`test`、`integrationTest`、`audit` 或 `build`。

根任务的准确阶段和质量级别见 [Sca4d-J 构建 Gradle 插件](/standards/gradle-plugins)。普通局部修改不以根 `build` 作为默认入口。

## Spotless

Spotless 的配置是被覆盖文件的格式来源。Java 使用构建插件中的 Eclipse formatter；各 Java 项目同时处理自己的 SQL、XML 和 `build.gradle.kts`。根项目只处理根构建脚本及 `build.gradle.kts` 中明确列出的 Markdown 和配置文件。

运行 `spotlessApply` 前先检查目标文件中是否有不属于当前任务的修改。格式化会直接写文件；无法隔离时只运行 `spotlessCheck`，并手工修正本次范围内的文件。

格式检查通过只说明文件符合自动格式规则，不说明命名、分层、SQL、文档内容和运行行为正确。

## Java 编译、测试与审计

编译用于检查类型、签名、生成代码和依赖。单元测试用于检查隔离行为，集成测试用于检查 Spring、数据库、Redis、消息或其他真实接入。Checkstyle、SpotBugs 和 IDEA 检查用于发现编译器不报告的问题。

优先运行能够直接覆盖改动风险的最小任务。测试失败后先修复并重跑失败项；只有修复影响其他项目时才扩大范围。环境缺失、依赖服务未启动或工具不可用时，记录实际失败和未覆盖内容，不能写成代码已经通过。

静态检查报告中的问题按文件和规则处理。不使用批量 `@SuppressWarnings`、`noinspection` 或排除配置隐藏本次引入的问题。原有且与任务无关的问题需要说明，但不顺手扩大修改范围。

## 管理端工具链

前端在 `frontends/sca4d-admin` 工作区使用根 `package.json` 声明的 Node.js 和 pnpm 版本。依赖变化同步更新 `package.json` 与 `pnpm-lock.yaml`，不手工编辑 `node_modules` 或生成产物。

当前脚本包括：

| 脚本 | 检查内容 |
|---|---|
| `pnpm format:check` | 前端工作区格式 |
| `pnpm lint` | TypeScript、Vue、样式和工作区规则 |
| `pnpm typecheck` | `common/` 与管理端应用类型 |
| `pnpm test` | `common/` 和管理端 Vitest |
| `pnpm build` | 管理端生产构建 |
| `pnpm e2e` | Playwright 用户流程 |

只运行与改动相关的脚本。接口契约是 `common/` 中的手写 TypeScript，不存在 `api:check` 或 `api:generate` 脚本。

## 文档站

普通 Markdown 正文、注释和拼写修改不运行 Java、前端或产品测试。修改 VitePress 配置、导航、内部链接、Markdown 组件或文档生成输入时，才在 `docs/` 工作区运行 `pnpm docs:build`。

`docs/public/javadoc/` 保存随仓库提交的 Java API。运行 `pnpm docs:dev` 时可直接访问 `/javadoc/` 下的页面，`pnpm docs:build` 会把这些静态文件一同打包，无需启动 Java 构建。

`docs/.vitepress/dist`、缓存和 `node_modules` 不纳入版本管理。页面构建通过只证明 VitePress 能生成站点，不证明设计、规格和代码事实正确。

## 生成文件与构建产物

生成结果从生成来源重新产生，不手工修补生成文件。Flyway V1 是 `initFlyway` 生成后由 App 接管的例外：一旦进入 App 迁移历史，它就是 App 源文件，后续通过新迁移继续向前。

发布产物需要包含构建配置已经声明的版本和源码信息，使产物能够定位到对应提交。构建目录、报告、JAR、前端 `dist` 和本地测试结果不进入普通源码提交，除非发布流程明确要求保存某项产物。

## 交付说明

结束时写明实际运行的命令和结果，并说明每项检查针对的风险。没有运行全量检查时，说明为什么局部检查已经覆盖本次修改；没有运行某项必要检查时，明确剩余风险。不同证据分开报告，不能用编译通过代替测试、用单元测试代替 Spring 装配、用构建通过代替浏览器交互。

## 格式化与构建

### Spotless 范围

根 Spotless 使用明确的正向目标：

| 内容 | 路径 | 当前处理 |
|---|---|---|
| 根 Markdown | 根目录 `*.md`、`docs/*.md` | 删除行尾空白、保证结尾换行 |
| 分类文档 | `docs/home/`、`docs/tutorials/`、`docs/standards/`、`docs/reference/`、`docs/requirements/`、`docs/ai-collaboration/` 下的 Markdown | 删除行尾空白、保证结尾换行 |
| GitHub Markdown | `.github/**/*.md` | 删除行尾空白、保证结尾换行 |

当前根构建没有为 Markdown 配置 Flexmark。Spotless 不会自动对齐表格、重排段落或验证链接；这些内容需要人工检查和站点构建。

只修改文档时优先运行：

```bash
./gradlew :spotlessCheck
```

运行 `spotlessApply` 前确认目标范围内没有任务发起人的原有脏文件。无法隔离时只运行检查并手工修正本次文件。

### VitePress

修改导航、内部链接、Markdown 组件、VitePress 配置或文档生成输入时运行：

```bash
./gradlew buildVitePress
```

Gradle 任务先运行 `collectJavadoc` 更新 Java API，再在 `docs/` 中执行 `pnpm run docs:build`，输出位于 `docs/.vitepress/dist/`。只修改 Markdown 时可以直接在 `docs/` 执行 `pnpm run docs:build`，使用仓库已有的 Java API。VitePress 不检查 `/javadoc/` 地址，构建后需要对照实际文件检查这些链接。

### Javadoc

修改 Java 文档注释、类型签名或 Javadoc 聚合逻辑时运行：

```bash
./gradlew javadoc
./gradlew collectJavadoc
```

`javadoc` 通过构建映射逐级运行已配置 Java 项目，并包含两个 Gradle Included Build。`collectJavadoc` 会执行这些任务，再按 Gradle 项目路径同步到 `docs/public/javadoc/`，同时移除已经删除的类型页面。

公开类型、签名和文档注释变化时，将重新生成的 `docs/public/javadoc/` 与源码一起提交。该目录只保存生成结果，修改内容应从 Java 源码开始。Javadoc 生成关闭时间戳，避免每次构建产生日期差异。

### 完整站点

导航同时引用 Markdown 和 Javadoc，或者需要交付可部署站点时运行：

```bash
./gradlew documentationSite
```

任务按 Javadoc 生成、同步、VitePress 打包的顺序执行，再把完整产物复制到 `build/docs/site/`。`docs/public/javadoc/`、`docs/.vitepress/dist/javadoc/` 和 `build/docs/site/javadoc/` 都包含主构建 Java 项目及两个 Gradle Included Build 的 Java API。
