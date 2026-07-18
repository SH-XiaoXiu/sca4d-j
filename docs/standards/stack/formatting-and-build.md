# 格式化与构建规范

## 后端格式化

Java 使用 Spotless 和仓库 `config/eclipse-java-format.xml`。Markdown、YAML 和其他文本文件使用对应的 Spotless 任务。提交前必须运行格式检查。

```bash
./gradlew spotlessApply
./gradlew spotlessCheck spotlessMiscCheck
```

## 后端验证

```bash
./gradlew compileJava
./gradlew test
./gradlew checkEnvExample
./gradlew check
```

验证范围从修改内容直接相关的测试扩展到受影响模块的完整测试。环境导致的失败必须保留失败命令和原因，不能记为测试通过。

## 管理端工具链

使用 `sca4d-admin/package.json` 声明的 Node.js 与 pnpm 版本，锁文件必须随依赖变更更新。

```bash
cd sca4d-admin
pnpm lint
pnpm typecheck
pnpm test
pnpm api:check
pnpm build
```

## VitePress

文档依赖和构建只在 `docs` 工作区管理：

```bash
cd docs
pnpm docs:build
```

生成的 cache、dist 和 node_modules 不作为文档正文修改审查依据。

## 构建产物

开发和发布 JAR 必须保留构建版本、提交、分支、作者、模式和时间元数据。发布产物必须能回到源码和同版本管理端契约。
