# 实时通信规则

## 用途

实时事件只提示状态变化，不能替代数据库查询。客户端断线或漏消息后必须能重新查询恢复。

## 发布接口

业务使用 `RealtimePublisher` 并指定 target，禁止直接访问 SSE emitter、WebSocket session、SimpMessagingTemplate 或本地连接表。

## 目标授权

投递目标使用 Subject、Principal、Tenant 或 Connection。STOMP 订阅和 `@MessageMapping` 必须执行认证、Access、权限和限流；客户端不能任意拼接目标路径。

## 跨节点

连接只保存在接入节点，本地 registry 不得被当成全局在线状态。跨实例事件经 RabbitMQ 广播，每个节点只投递本地匹配连接。

## 请求态

每个 STOMP 帧建立并清理 CurrentRequest。握手身份不自动授权后续所有订阅和消息。

## 断线恢复

客户端实现重连、重订阅和关键状态重新拉取。服务端不承诺无限事件回放，除非领域另有持久化事件协议。
