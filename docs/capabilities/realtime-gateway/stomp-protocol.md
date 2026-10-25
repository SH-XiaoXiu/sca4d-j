# WebRealtimeProtocol

源码：`modules/web-realtime/sca4dj-web-realtime/src/main/java/com/sca4d/web/realtime/stomp/WebRealtimeProtocol.java`

WebRealtimeProtocol 集中保存浏览器、具体应用和模块必须逐字一致的 STOMP 名称。下文路径和 Header 是代码默认值，启动时可由 `sca4dj.web-realtime.protocol.*` 覆盖。模块固定只接受 STOMP `1.2`，不注册 SockJS。配置变化后，需要重启 Server，并同步更新 Gateway 路由和 STOMP 客户端。Spring 负责解析和分发 STOMP 帧，模块只扩展握手、拦截器和连接生命周期，不自行实现 STOMP 解析器。

WebRealtimeProtocol 分别声明 `/app` 应用 destination 前缀、`/user` 用户前缀，以及 `/queue`、`/topic` broker 前缀。对应配置键是 `application-destination-prefix`、`user-destination-prefix`、`queue-destination-prefix` 和 `topic-destination-prefix`。WebSocket 入口使用 `websocket-endpoint`。STOMP 标准不规定这些路径。

浏览器默认使用 `/user/queue/events` 订阅当前连接的服务端事件。服务端默认把统一的浏览器事件信封发送到内部 `/queue/events`，并以 Spring STOMP Session ID 定向到对应连接。两条目标分别由 `user-event-destination` 与 `user-event-broker-destination` 配置，未覆盖时会随 destination 前缀计算。模块不创建 `java.security.Principal`，也不把 Session 或 Core ConnectionId 伪装成 Spring 用户。

`principal-id` 是 Sca4d-J 定义的 STOMP native Header 默认名称，不是 STOMP 标准字段。部署可用 `principal-id-header` 覆盖。每个 `SEND`、`SUBSCRIBE` 必须恰好提供一个正整数值。模块使用该值，从握手 Session 中为本帧 Call 选择业务 Principal。Header 缺失、重复、格式错误、非正数，或 Principal 不属于 Session 时，当前帧在进入具体应用或登记订阅前失败。

浏览器只能向 `/app/**` 发送业务消息。具体应用使用去掉 `/app` 前缀的 `@MessageMapping` 路径接收。`SEND` 到 `/queue`、`/topic` 或 `/user` 会被模块拒绝。`CONNECT`、`DISCONNECT` 和 `UNSUBSCRIBE` 不读取 `principal-id`。

服务端需要关闭连接时，在协议消息写出后以 `POLICY_VIOLATION` 状态关闭 WebSocket。

## 示例

以下示例对应工作区文档协作应用。浏览器订阅自己的事件队列，并向 `/app/documents/comment` 发送一条评论。

订阅服务端事件，`principal-id` 指定本帧使用的业务 Principal：

```text
SUBSCRIBE
id:sub-events
destination:/user/queue/events
principal-id:42

^@
```

向应用消息处理方法发送评论。`/app` 前缀由浏览器发送，应用方法声明的路径不含 `/app`：

```text
SEND
destination:/app/documents/comment
content-type:application/json
principal-id:42

{"documentId":1001,"body":"请确认第三段的术语"}^@
```

应用侧接收的方法使用 Spring `@MessageMapping`，路径为去掉 `/app` 后的部分：

```java
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class DocumentCommentStompController {

    @MessageMapping("/documents/comment")
    public void comment(@Payload CommentRequest request) {
        // 当前 SEND 的 Call 已经绑定 principal-id 对应的业务 Principal。
        // 应用在此校验 Principal 对文档的权限，并保存评论。
    }

    public static final class CommentRequest {

        private long documentId;
        private String body;

        public CommentRequest() {
        }

        public long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(long documentId) {
            this.documentId = documentId;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
```

`^@` 表示 STOMP 帧结束的 NULL 字节。示例中的 `principal-id` 为应用为当前 Account 允许的业务 Principal 标识。
## 扩展方式

本页的协议名称固定由 `WebRealtimeProtocol` 提供，应用通过 `sca4dj.web-realtime.protocol.*` 覆盖默认值。应用侧消息处理通过 Spring `@MessageMapping` 方法声明，Spring 扫描这些方法并注册到 `/app` 前缀下。
