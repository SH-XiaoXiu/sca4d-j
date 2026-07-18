# CurrentRequest

## 保存的内容

`CurrentRequest` 保存一次 HTTP 请求、STOMP 帧或 SYSTEM 任务的 traceId、客户端地址、Session、Subject、Account、Principal、权限和 AccessCriteria。`CurrentRequestHolder` 只在当前线程中返回它。

## HTTP 请求信息

`HttpRequestFacts` 从 Servlet 请求读取 traceId、URI、客户端地址和 locale。客户端地址使用 Tomcat 处理可信代理后的结果，应用代码不直接读取任意客户端传入的 `X-Forwarded-For`。

## 登录和权限信息

`AuthInterceptor` 恢复 Session、Subject 和固定 Account，`PrincipalInterceptor` 选择当前 Principal，并加载 `SessionPermission`。后续 Controller、权限检查和审计读取同一个 `CurrentRequest`。

## 数据访问范围

`AccessAspect` 调用业务模块的 Policy 生成 `AccessCriteria`。Repository 通过 `ResourceAccess` 读取 Criteria，并把租户 ID、Principal ID 或资源 ID 加入 SQL。

## HTTP 生命周期

`CurrentRequestFilter` 创建 `CurrentRequest` 和 traceId。认证拦截器恢复登录，Principal 拦截器选择当前 Principal，权限和 Access 代码添加授权信息。响应完成后 Filter 清理 Holder，防止线程复用时读取上一个请求的数据。

## STOMP 生命周期

握手阶段建立连接身份；每个 STOMP 帧由 `StompRequestInterceptor` 重新建立 CurrentRequest，并执行订阅授权、限流或消息处理。连接 Principal 只提供可验证的身份基础，不替代帧级资源访问判断。

## SYSTEM 请求

后台任务没有用户 Session。它显式创建 SYSTEM 请求与系统 Principal，调用允许 SYSTEM 的领域能力。Policy 必须明确接受或拒绝 SYSTEM，不能用 `null` 暗示“无限权限”。

## 请求结束清理

无论正常返回还是发生异常，Holder 和 MDC 都在 `finally` 中清理。异步任务不能保存 `CurrentRequest`，需要使用的 `principalId`、`traceId` 和业务 ID 要作为方法参数传入。

## Trace ID 与客户端状态

traceId 同时进入响应和日志以建立诊断链。授权或工作上下文变化时，`ClientState` 通过响应头提示客户端刷新；它是反馈协议，不改变服务器授权结果。
