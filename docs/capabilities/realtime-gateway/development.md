# 实时通信规则

## 用途

实时事件只提示状态变化，不能替代数据库查询。客户端断线或漏消息后，必须能够重新查询恢复。

## 发布接口

业务代码使用 `WebRealtimePublisher` 并指定 `WebRealtimeTarget`。业务代码禁止直接访问 SSE emitter、WebSocket session、`SimpMessagingTemplate` 或本地连接表。

## 目标授权

投递目标使用 `WebRealtimeTarget` 表示。目标类型为 Account、Subject、Principal、Resource、Topic、Session 或 Connection。STOMP 订阅和 `@MessageMapping` 必须执行认证、Access、权限和限流。客户端不能任意拼接目标路径。

## 跨节点

连接只保存在接入节点，本地注册表（registry）不得被当成全局在线状态。跨实例事件经 RabbitMQ 广播，每个节点只投递本地匹配的连接。

## 请求态

每个 STOMP 帧把本帧的 Call 绑定到当前线程（`CallHolder`），处理结束后清理。握手身份不自动授权后续所有订阅和消息。

## 断线恢复

客户端实现重连、重订阅和关键状态重新拉取。服务端不承诺无限事件回放，除非领域另有持久化事件协议。

## 示例

以下示例对应工作区文档协作应用。文档内容更新后，服务端通知同一工作区的在线成员刷新文档列表。通知只携带文档标识，成员收到后调用 HTTP 接口重新读取文档内容。

```java
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class DocumentRealtimeNotifier {

    private static final EventType<DocumentUpdated> DOCUMENT_UPDATED =
            new EventType<>("workspace.document.updated", 1, DocumentUpdated.class);

    private final WebRealtimePublisher publisher;

    public DocumentRealtimeNotifier(WebRealtimePublisher publisher) {
        this.publisher = publisher;
    }

    public void documentUpdated(long workspaceId, long documentId, Instant occurredAt) {
        Event<DocumentUpdated> event = new Event<>(
                DOCUMENT_UPDATED,
                String.valueOf(documentId),
                occurredAt,
                new DocumentUpdated(documentId));
        publisher.publish(
                WebRealtimeTarget.topic("workspace:" + workspaceId),
                event,
                DispatchPolicy.afterCommit());
    }

    public static final class DocumentUpdated {

        private final long documentId;

        public DocumentUpdated(long documentId) {
            this.documentId = documentId;
        }

        public long documentId() {
            return documentId;
        }
    }
}
```

`WebRealtimeTarget.topic(...)` 的字符串由应用定义。订阅策略必须为同一工作区返回相同的字符串，见 [WebRealtimeSubscriptionPolicy](/capabilities/realtime-gateway/subscription-policy)。需要通知单个成员时，使用 `WebRealtimeTarget.subject(memberSubjectId)`。

`DispatchPolicy.afterCommit()` 表示在事务提交后投递。文档更新事务回滚时，通知不会发出。