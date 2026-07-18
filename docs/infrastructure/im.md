# 即时通信

IM 模块提供会话、成员、文本消息、图片消息、已读和撤回，并复用 Principal、文件服务与实时通信能力。

## 会话

```text
GET  /im/conversations
POST /im/conversations
```

会话类型包括：

- `SINGLE`：两名成员的单聊。
- `GROUP`：至少两名成员的群聊。

创建请求未指定类型时，两名成员解析为单聊，更多成员解析为群聊。创建人自动加入会话；群聊创建人为 `OWNER`，其他成员为 `MEMBER`。

## 消息

```text
GET  /im/conversations/{conversationId}/messages
POST /im/conversations/{conversationId}/messages
PUT  /im/messages/{messageId}/read-status
DELETE /im/messages/{messageId}
```

`TEXT` 消息提交文本内容。`IMAGE` 消息提交 `attachmentFileId` 和文件签名阶段取得的 `attachmentUploadGrant`，发送流程会把图片文件消费到目标会话。

消息状态为 `NORMAL` 或 `REVOKED`。发送者可以撤回自己的消息，撤回后列表保留消息位置并返回撤回状态。

## 成员与访问

读取会话、发送消息、标记已读和撤回都会检查当前 Principal 是否为会话成员。成员引用包含 Principal 类型与 ID，服务端会验证 Principal 存在并可用。

图片读取由 `ImFileSignaturePolicy` 检查会话成员关系，随后返回短期文件地址。

## 实时事件

会话创建、消息发送、已读和撤回通过 `ImRealtimePublisher` 发布。客户端可以通过 WebSocket/STOMP 或 SSE 接收会话更新，事件 Payload 使用 `ImRealtimePayload`。

## 输入限制与失败结果

- 会话至少包含两名去重后的成员；单聊必须为两人，群聊至少两人。
- 当前实现只允许同 Principal 类型、同租户且状态为 ACTIVE 的成员加入同一会话。
- 会话标题最长 128 个字符，文本消息最长 4000 个字符，扩展 Payload 文本最长 4096 个字符。
- 读取、发送、已读和撤回都要求当前 Principal 是会话成员；否则返回 `NOT_CONVERSATION_MEMBER`。
- 会话或消息不存在时分别返回 `CONVERSATION_NOT_FOUND`、`MESSAGE_NOT_FOUND`。
- 只有发送者可以撤回状态为 `NORMAL` 的消息；其他情况返回 `MESSAGE_REVOKE_FORBIDDEN`。

图片消息缺少 fileId 或 uploadGrant 时返回 400。文件消费失败时消息事务回滚，不保存消息，也不发送实时事件。
