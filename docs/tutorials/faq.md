# 常见问题

本页汇总教程与内置功能中已经写明的常见失败、限制和排查入口，适合遇到启动、登录、权限、文件、消息、Gateway 或构建问题的开发者和运维人员。每个问题只列出现象、原因方向和需要查看的页面。

本页不新增规则。具体的状态码、配置键、失败结果和恢复方式以链接的页面为准。

## 本地启动失败

现象：

- 启动 Gateway 后，访问 `/api/**` 返回 503。

原因方向：

- Server 尚未启动时，`/api/**` 返回 503 是预期结果。
- Passkey 必须从 `SCA4DJ_FRONTEND_ORIGIN` 对应的完整地址打开页面，不能混用 `127.0.0.1` 或其他端口。
- PostgreSQL 与 TimescaleDB 的用户名和密码只在数据目录首次初始化时读取。修改环境文件不会更新已经存在的数据库角色密码。

去哪里看：

- [第一次运行](/tutorials/local-start) 的「启动 Server」「启动管理端」和「检查结果」。

## 登录失败或 Session 失效

现象：

- 提交登录或验证码后返回认证失败。
- 已登录的页面突然要求重新登录。

原因方向：

- 验证码只能使用一次。过期、错误次数达到上限、Challenge 不存在或已经消费时，验证码都不能再次使用。
- 认证方法失败也可能返回 401，但这类失败不表示登录 Session 已经失效。
- 普通 JWT 请求不在线检查注销、账号停用和权限变化。要求立即读取权威 Session 的接口需要使用不透明 Session Token。

去哪里看：

- [接入认证与 Session](/tutorials/authentication-and-session) 的「为敏感操作完成 verification」「Token 模式」。
- [HTTP 通用约定](/reference/http/common) 中 `authentication.session` 响应头的说明。
- [Sca4d Server HTTP API](/reference/http/sca4d-server) 中邮件验证码的错误码 `2401` 和 `2402`。

## 权限被拒绝（403）

现象：

- 请求返回 403。

原因方向：

- 当前 Principal、Operation 和对象权限中至少一项不满足。
- 系统管理、Platform 和 IoT 管理路由中，没有 Session 返回 401，缺少对应权限返回 403。
- 策略拒绝时，CallLifecycle 抛出 `BusinessException(CoreErrorCode.PERMISSION_DENIED)`。

去哪里看：

- [接入身份与权限](/tutorials/identity-and-permission) 的「接入一个业务入口」。
- [授权](/capabilities/identity-access/authorization) 的「固定执行规则」。
- [错误模型](/capabilities/foundation/error-model) 中 `PERMISSION` 与 403 的对应关系。
- [构建、部署与排障](/tutorials/build-deploy-troubleshoot) 的「从失败响应定位」。

## 文件上传失败

现象：

- 上传完成确认时返回无效上传或内容不匹配。
- 批量申请读取地址时返回文件不存在。

原因方向：

- 完成确认时找不到对象，返回无效上传。实际类型或大小不符，返回内容不匹配。
- 存储实现不可用时，返回存储不可用。
- 批量申请读取地址时，任一文件不存在、用途不符或无权读取，整个请求都返回文件不存在。响应不指出具体文件。
- 上传失败时，检查签发的 method、URL、headers、CORS，以及实际文件类型与大小。

去哪里看：

- [上传和引用文件](/tutorials/file-lifecycle) 的「失败时会发生什么」。
- [文件生命周期](/capabilities/file-storage/file-lifecycle) 的「状态变化」和「失败时会发生什么」。
- [构建、部署与排障](/tutorials/build-deploy-troubleshoot) 的「从失败响应定位」。

## 消息未发布

现象：

- 业务事务已经提交，但消费者没有收到消息。
- 调用发布接口后返回失败。

原因方向：

- `DispatchPolicy.immediate()` 无法路由或未确认时，调用失败。
- `DispatchPolicy.afterCommit()` 在回滚时不发送。提交后只尝试一次，失败或进程退出时消息可能丢失。
- `DispatchPolicy.outbox()` 与业务事务一起回滚。提交后，Outbox 持续重试发布。
- Outbox 发布失败后进入 `FAILED`，等待退避后重试。源码中未定义 `DEAD` 状态。
- Consumer 处理失败时，按 `RetryRule` 重试；耗尽后进入 DLQ。Messaging 不自动重放 DLQ。

去哪里看：

- [发布消息与通知](/tutorials/messaging-and-notification) 的「业务消息与可靠发布」。
- [Outbox](/capabilities/messaging-notification/outbox) 的「发布、失败与退避」。

## Gateway 回调被拒绝

现象：

- 外部回调返回 503。
- 外部回调返回 502。
- 注册控制请求返回 401。

原因方向：

- 没有租约有效、readiness 成功且支持该回调编码的实例时，Gateway 返回 503。Server 必须已经注册该回调编码。
- 已经选中实例但 TCP 连接失败时，Gateway 返回 502。
- Gateway 不自动改选其他实例重试。写请求可能已经被目标 Server 接收，调用方需要按自身协议确认结果。
- 注册控制请求的 Secret 错误或缺失时，返回 401。
- Gateway Secret 不代替回调自身的认证。EMQX 回调继续校验 `X-Iot-Emqx-Secret`。

去哪里看：

- [部署 Gateway 与接入回调](/tutorials/gateway-and-callbacks) 的「可信转发」和「失败与集群限制」。
- [Gateway 运行时](/capabilities/realtime-gateway/gateway-runtime) 的「Header 和失败结果」状态码表。

## 构建失败

现象：

- `./gradlew compile`、`./gradlew test`、`./gradlew audit` 或 `./gradlew formatCheck` 失败。

原因方向：

- 编译失败时，先运行直接受影响项目的 `compileJava`，再查看报错的类和方法。
- 格式检查失败时，`spotlessCheck` 会报告差异。`spotlessApply` 会直接写文件，运行前先确认目标文件没有无关修改。
- 审计结果为 `ERROR` 时，根任务 `audit` 失败。
- Checkstyle 或 SpotBugs 无法执行、配置文件缺失或报告无法解析时，任务失败。这类失败不等同于代码问题。
- 集成测试需要可用的 Docker。详见 [编写与运行测试](/tutorials/testing)。

去哪里看：

- [Sca4d-J 构建 Gradle 插件](/engineering/build-gradle-plugin) 的「质量级别」。
- [格式化与构建](/engineering/stack/formatting-and-build) 的「Spotless」和「Java 编译、测试与审计」。
- [构建、部署与排障](/tutorials/build-deploy-troubleshoot) 的「构建应用」。
