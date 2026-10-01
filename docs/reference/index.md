# 参考

按问题查契约：HTTP 参考查请求与响应，运行配置查环境变量和启用条件，Java API 查方法参数与失败结果。第一次接入功能先完成[教程](/tutorials/local-start)，遇到具体字段或限制再查本页对应入口。

| 要查的内容 | 入口 |
|---|---|
| JSON 响应、错误码、分页与修改冲突 | [HTTP 通用契约](/reference/http/common) |
| 登录、账号、角色、系统管理、IoT 和实时连接路由 | [Sca4d Server HTTP API](/reference/http/sca4d-server#路由总表) |
| 登录 Token、请求头与敏感操作验证 | [Token 与语言](/reference/http/sca4d-server#token-与语言)、[登录与敏感 verification](/reference/http/sca4d-server#登录与敏感-verification) |
| 基础设施、环境变量、默认值和启用条件 | [运行配置](/reference/runtime/sca4d-server) |
| Java 参数、返回值、异常和扩展接口 | 下方按功能分组的 Java API |

## Java API 参考

### 公共接口与 Spring

业务代码使用的公共类型、注解、配置和 HTTP 接入：

- [sca4dj-core-api](/javadoc/core/sca4dj-core-api/index.html)
- [sca4dj-core-spring](/javadoc/core/sca4dj-core-spring/index.html)

### 身份与认证

IAM 管理 Subject、Account、Principal 和授权关系；Auth 管理认证尝试和 Session，各认证方法独立实现凭据验证：

- [sca4dj-iam](/javadoc/modules/iam/sca4dj-iam/index.html)
- [sca4dj-auth](/javadoc/modules/auth/sca4dj-auth/index.html)
- [sca4dj-auth-password](/javadoc/modules/auth/sca4dj-auth-password/index.html)
- [sca4dj-auth-passkey](/javadoc/modules/auth/sca4dj-auth-passkey/index.html)
- [sca4dj-auth-totp](/javadoc/modules/auth/sca4dj-auth-totp/index.html)
- [sca4dj-auth-email](/javadoc/modules/auth/sca4dj-auth-email/index.html)
- [sca4dj-auth-sms](/javadoc/modules/auth/sca4dj-auth-sms/index.html)
- [sca4dj-auth-federation](/javadoc/modules/auth/sca4dj-auth-federation/index.html)
- [sca4dj-auth-shared-secret](/javadoc/modules/auth/sca4dj-auth-shared-secret/index.html)
- [sca4dj-auth-hmac](/javadoc/modules/auth/sca4dj-auth-hmac/index.html)
- [sca4dj-auth-x509](/javadoc/modules/auth/sca4dj-auth-x509/index.html)

### 审计、消息与通知

审计记录、事务消息、通知模板及各渠道发送：

- [sca4dj-audit](/javadoc/modules/audit/sca4dj-audit/index.html)
- [sca4dj-messaging](/javadoc/modules/messaging/sca4dj-messaging/index.html)
- [sca4dj-notification](/javadoc/modules/notification/sca4dj-notification/index.html)
- [sca4dj-notification-email](/javadoc/modules/notification/sca4dj-notification-email/index.html)
- [sca4dj-notification-inbox](/javadoc/modules/notification/sca4dj-notification-inbox/index.html)
- [sca4dj-notification-sms](/javadoc/modules/notification/sca4dj-notification-sms/index.html)
- [sca4dj-notification-sms-aliyun](/javadoc/modules/notification/sca4dj-notification-sms-aliyun/index.html)

### 任务、调度与实时通信

- [sca4dj-job](/javadoc/modules/job/sca4dj-job/index.html)
- [sca4dj-scheduling-quartz](/javadoc/modules/scheduling/sca4dj-scheduling-quartz/index.html)
- [sca4dj-web-realtime](/javadoc/modules/web-realtime/sca4dj-web-realtime/index.html)

### IoT

设备与物模型接口、状态和命令、EMQX 接入及历史数据：

- [sca4dj-iot-api](/javadoc/modules/iot/sca4dj-iot-api/index.html)
- [sca4dj-iot](/javadoc/modules/iot/sca4dj-iot/index.html)
- [sca4dj-iot-emqx](/javadoc/modules/iot/sca4dj-iot-emqx/index.html)
- [sca4dj-iot-timescale](/javadoc/modules/iot/sca4dj-iot-timescale/index.html)

### 文件与开放 API

- [sca4dj-storage-file](/javadoc/modules/storage/sca4dj-storage-file/index.html)
- [sca4dj-storage-local](/javadoc/modules/storage/sca4dj-storage-local/index.html)
- [sca4dj-storage-minio](/javadoc/modules/storage/sca4dj-storage-minio/index.html)
- [sca4dj-openapi](/javadoc/modules/openapi/sca4dj-openapi/index.html)

### 示例应用与构建插件

- [sca4d-iot-simulator](/javadoc/apps/sca4d-iot-simulator/index.html)
- [sca4d-server](/javadoc/apps/sca4d-server/index.html)
- [sca4d-gateway](/javadoc/gateway/sca4d-gateway/index.html)
- [sca4dj-build-gradle-plugin](/javadoc/tools/sca4dj-build-gradle-plugin/index.html)
- [sca4dj-app-gradle-plugin](/javadoc/tools/sca4dj-app-gradle-plugin/index.html)

Java API 由源码 Javadoc 生成，保存在 `docs/public/javadoc/` 并随仓库提交。本地预览和文档打包直接使用这些文件，更新方法见[Javadoc 构建](/standards/09_build#javadoc)。
