# 日常开发命令

## 启动本地服务

```bash
docker compose up -d
docker compose ps
./gradlew :sca4d-server:bootRun
```

Gateway 只在需要测试转发和多实例时启动：

```bash
./gradlew :sca4d-gateway:bootRun
```

## 运行后端测试

运行一个测试类：

```bash
./gradlew :sca4d-server:test \
  --tests 'com.sca4d.iam.account.DefaultAccountProvisioningServiceTest'
```

运行全部后端测试：

```bash
./gradlew test
```

## 格式化代码

```bash
./gradlew spotlessApply
./gradlew spotlessCheck spotlessMiscCheck
```

## 检查环境变量清单

```bash
./gradlew checkEnvExample
```

该任务比较 `.env.example` 与 Server、Gateway、Compose 和管理端实际读取的环境变量。

## 启动管理端

```bash
cd sca4d-admin
pnpm install
pnpm dev
```

## 检查管理端

```bash
cd sca4d-admin
pnpm api:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

后端接口发生变化时先启动后端，再运行：

```bash
pnpm api:generate
pnpm api:check
```

## 构建文档

```bash
cd docs
pnpm docs:build
```

## 修改数据库

在 `sca4d-server/src/main/resources/db/migration` 增加数据库初始化变更。已经在共享数据库执行过的数据库初始化内容不能修改。

使用新数据库启动应用，确认全部迁移可以依次执行。升级测试使用上一版本数据库启动当前代码，确认新增迁移可以执行。

## 提交前检查

```bash
git status --short
git diff --check
./gradlew check
cd sca4d-admin && pnpm typecheck && pnpm test && pnpm build
cd ../docs && pnpm docs:build
```

如果某条命令因外部服务或本机软件失败，在提交说明中记录命令、错误信息和未验证的内容。
