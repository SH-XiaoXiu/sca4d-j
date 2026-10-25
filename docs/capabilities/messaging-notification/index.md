# 消息与通知

本目录说明消息与通知功能。业务消息负责可靠发布已经发生的业务事件，通知负责面向收件人的通知内容、渠道和模板。业务事务通过 Outbox 保存待发布消息，通知模块决定通知内容和渠道。适合开发业务事件、通知和邮件模板的开发者阅读。

## 消息

- [业务消息与可靠发布](/capabilities/messaging-notification/domain-events)
- [消息内容与投递结果](/capabilities/messaging-notification/message-content)
- [Outbox 派发与清理](/capabilities/messaging-notification/outbox)
- [Messaging 模块](/capabilities/messaging-notification/messaging)
- [Messaging 配置](/capabilities/messaging-notification/configuration)
- [消息开发规则](/capabilities/messaging-notification/development)

## 通知

- [Notification 模块](/capabilities/messaging-notification/notification)
- [Notification Email 模块](/capabilities/messaging-notification/notification-email)
- [通知模板](/capabilities/messaging-notification/notification-template)
- [邮件模板](/capabilities/messaging-notification/email-template)
- [站内信](/capabilities/messaging-notification/inbox)
- [短信通知渠道](/capabilities/messaging-notification/sms)
- [阿里云短信适配器](/capabilities/messaging-notification/sms-aliyun)
