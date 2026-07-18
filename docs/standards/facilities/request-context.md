# CurrentRequest 规范

## 唯一请求态

HTTP、STOMP 和 SYSTEM 入口都必须建立 `CurrentRequest`。禁止新增平行 ThreadLocal、全局 RequestContext，禁止在业务代码中直接读取 ServletRequest。

## 数据来源

traceId、locale、客户端地址由入口写入；认证结果由认证链写入；资源范围由 AccessAspect 写入。下游只能读取这些值，不得覆盖。

## 地址信任

应用只读取 Servlet 容器在可信代理配置下解析的客户端地址。禁止直接信任任意 `X-Forwarded-For` 或 `X-Real-IP`。

## SYSTEM

后台任务必须显式建立 SYSTEM CurrentRequest 和 Principal。`null` 身份禁止表示系统无限权限；每个 Policy 都要单独决定是否允许 SYSTEM。

## 生命周期

入口必须在 finally 清理 Holder 和 MDC。异步任务不得隐式继承线程本地请求信息，需要的业务字段必须作为方法参数传入。

## 一致快照

一次请求只选择一次 Principal 并使用同一授权快照。业务流程中禁止重新选择或切换工作上下文。
