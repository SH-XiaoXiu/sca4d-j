# 构建、版本与产物

## Gradle 聚合构建

根工程统一 Spring Boot、依赖管理、Spotless、Java 21 toolchain 和构建元数据。`sca4d-server` 与 `sca4d-gateway` 分别产生可执行 JAR。

## 开发构建

```bash
./gradlew clean test
./gradlew :sca4d-server:bootJar :sca4d-gateway:bootJar
```

开发版本为 `<commit>-<yyyyMMdd-HHmm>`。

## 发布构建

```bash
./gradlew clean check :sca4d-server:bootJar :sca4d-gateway:bootJar \
  -Prelease -Prelease.version=1.0.0
```

发布版本为 `<release>-<commit>`；未传版本时读取最近标签。JAR 名、Manifest、build-info 和 Banner 保存相同提交证据。

## 管理端构建

```bash
cd sca4d-admin
pnpm install --frozen-lockfile
pnpm api:check
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

## 文档构建

```bash
cd docs
pnpm install --frozen-lockfile
pnpm docs:build
```

## 产物验收

核对 JAR 文件名与 Manifest、后端/网关能以目标配置启动、管理端对应同一 OpenAPI、数据库从上一版本可迁移、静态产物不含密钥、文档构建和链接通过。

