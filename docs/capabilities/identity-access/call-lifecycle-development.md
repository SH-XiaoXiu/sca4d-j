# 调用状态规则

本页规定 Call 与 CallExecution 的使用约束。本页适用于编写 Controller、Service 和异步任务的开发者。读完后，开发者能确定调用信息应由哪一层读取、写入和清理。

## 唯一请求态

HTTP、STOMP 和 SYSTEM 入口都必须建立 `Call`。禁止新增平行 ThreadLocal 或全局 RequestContext，也禁止在业务代码中直接读取 ServletRequest。

## 数据来源

调用标识、开始时间和可选的 HTTP 快照由入口写入。认证结果由认证链写入。Principal 由对应入口选择。权限判定由 PermissionCallInterceptor 写入。

后续代码只能读取已经确认的值，不得覆盖或重新选择。Call 是一次传输调用中已确认事实的唯一保存位置，认证、Principal 选择和权限判定各自只写入一次；禁止另建平行 ThreadLocal，让所有声明式注解读取同一份快照。

Subject、Account 与 Principal 的来源和使用位置，遵循 [Subject、Account 与 Principal 使用规则](/capabilities/identity-access/development)。普通业务代码只读取当前 Principal。审计与认证安全代码可以读取 Subject 和 Account。

## 地址信任

应用只读取 Servlet 容器在可信代理配置下解析的客户端地址。禁止直接信任任意 `X-Forwarded-For` 或 `X-Real-IP`。

## SYSTEM

后台任务必须显式使用 `Call.system(...)` 建立 SYSTEM Call 和 Principal。`null` 身份不得表示系统无限权限。每个 Policy 都要单独决定是否允许 SYSTEM。

## 生命周期

入口必须在 finally 中清理 CallHolder 和 MDC。

每个声明式方法由 CallLifecycleAdvisor 压入自己的 CallExecution，并按相反顺序弹出。

异步任务不得隐式继承线程本地调用信息。需要的业务字段必须作为方法参数传入。

## 一致快照

一次 Call 只选择一次 Principal，并使用同一授权快照。业务流程中禁止重新选择或切换业务身份。

每个 CallExecution 只保存自己的声明、实参、要求和结果。它不把方法局部状态写回 Call。

## 示例

下面的任务在没有 HTTP 请求的线程中归档工作区的过期文档。
任务显式建立 SYSTEM Call，并通过 `CallHolder.run(call, task)` 绑定。
业务字段 `workspaceId` 作为参数传入，不从其他线程的 Holder 读取。

```java
import com.sca4d.core.api.call.Call;
import com.sca4d.core.spring.call.CallHolder;
import java.time.Instant;
import java.util.UUID;

public class DocumentArchiveJob {

    private final DocumentArchiveService archiveService;

    public DocumentArchiveJob(DocumentArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public void archiveExpired(long workspaceId) throws Exception {
        Call call = Call.system(UUID.randomUUID().toString(), Instant.now());
        CallHolder.run(call, () -> {
            archiveService.archiveExpired(workspaceId);
            return null;
        });
    }
}
```

`DocumentArchiveService` 中带 `@Audit`、`@RequirePermission` 等注解的方法，仍由 CallLifecycleAdvisor 压入自己的 CallExecution。
每个 Policy 单独决定是否允许 SYSTEM。

Service 需要异步处理时，只传入明确的值：

```java
@Audit(operation = "documents:publish", target = "#documentId", resourceType = "WORKSPACE/DOCUMENTS")
public void publish(long documentId) {
    // documentId 来自方法参数；异步工作使用参数副本，不读取 CallHolder。
    executor.execute(() -> notifier.notifyMembers(documentId));
}
```
