# 配置组织与覆盖

## 配置层级

Spring Boot 按标准优先级合并 `application.yml`、Profile 文件、导入的 `.env`、环境变量和命令行参数。仓库默认导入当前目录或上级目录的可选 `.env`，生产部署不应依赖工作目录偶然存在的文件。

## 配置清单

`.env.example` 是 Server、Gateway、Compose 与管理端工具的完整环境变量清单。它不保存秘密，但必须说明默认值、范围或部署预期。

```bash
./gradlew checkEnvExample
```

该任务扫描实际使用的环境变量，并报告 `.env.example` 中缺少、重复、已经停用或没有说明的配置项。

## 命名与值格式

项目属性使用 `SCA4DJ_`；Duration 使用 ISO-8601（如 `PT5M`），DataSize 使用 Spring 格式（如 `20MB`），枚举使用代码值。Servlet context path 非空时以 `/` 开头。

## 默认值

local 默认只用于快速启动。生产必须显式设置数据库、Redis、RabbitMQ、MinIO、文件 Grant 密钥、可信代理、允许 Origin 和各实例唯一 ID。`application-prod.yml` 默认关闭 OpenAPI。

## 敏感信息

数据库/RabbitMQ/MinIO 密码、初始管理员密码、文件 Grant 密钥和 token 必须从密钥系统注入。轮换时确认旧 Session、Grant 或连接的过渡策略。

## 配置校验

类型化 Properties 在应用启动时校验格式和取值范围，BootCheck 检查外部服务是否可用。修改配置后运行 `./gradlew checkEnvExample`，再使用目标 Profile 启动应用并检查 `/actuator/health/readiness`。

全部键见[配置参考](/reference/configuration.md)。
