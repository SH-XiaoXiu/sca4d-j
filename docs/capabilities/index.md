# 内置功能

本目录按功能说明内置能力的职责、主要对象、调用顺序、配置、协议、限制和开发规则。适合开发者和技术负责人在改动某项功能前，找到对应的功能首页和长期页面。

| 功能 | 负责内容 |
|---|---|
| [基础结构](/capabilities/foundation/) | 仓库组成、Core、全局 ID、错误模型、示例应用和管理端 |
| [数据与配置](/capabilities/data-configuration/) | 数据访问、Patch、DataGraph、字典、配置、缓存、并发和敏感数据 |
| [身份与访问](/capabilities/identity-access/) | Subject、Account、Principal、Resource、IAM、权限和调用评估 |
| [认证与会话](/capabilities/authentication-session/) | 认证引擎、Session、Password、TOTP、Passkey 和 Email Code |
| [消息与通知](/capabilities/messaging-notification/) | 业务消息、可靠发布、通知和邮件模板 |
| [文件与存储](/capabilities/file-storage/) | 文件生命周期、文件服务以及 Local、MinIO 存储实现 |
| [任务与调度](/capabilities/jobs-scheduling/) | Pipeline、Job、流程状态和 Quartz 调度 |
| [实时通信与 Gateway](/capabilities/realtime-gateway/) | SSE、STOMP、订阅策略和独立 Gateway |
| [IoT](/capabilities/iot/) | 设备生命周期、运行状态、历史数据、调度、EMQX、MQTT 和 TimescaleDB |
| [运行与可观测性](/capabilities/operations-observability/) | 日志开发规则、审计和系统管理接口 |

通用 HTTP 约定和应用运行配置放在[参考](/reference/)中。Java 类型的签名、继承关系和文档注释由构建生成的标准 Javadoc 提供。
