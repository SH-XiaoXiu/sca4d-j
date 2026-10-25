# 教程

本组教程按开发任务组织，每篇说明一项可以验收的操作。第一次接触项目的开发者从[第一次运行](/tutorials/local-start)开始，确认本机环境可用后，再按任务选择后续页面。

教程只写操作步骤、前置条件和检查结果。功能的对象、字段、配置和失败结果分别见[内置功能](/capabilities/)与[参考](/reference/)，教程通过链接引用，不重复说明。

## 第一次运行

- [第一次运行](/tutorials/local-start)：在本机按顺序启动依赖服务、Gateway、Server 和管理端，并用初始管理员登录。
- [组装应用](/tutorials/assemble-application)：以 `apps/<应用名>-server` 为起点，选择模块并配置依赖。
- [构建、部署与排障](/tutorials/build-deploy-troubleshoot)：构建、启动、发布检查、部署集中日志和失败定位。

## 业务接口与管理端页面

- [开发后端业务接口](/tutorials/backend-feature)：新增或修改业务字段时需要改动的位置，以及验证方法。
- [开发管理端页面](/tutorials/admin-feature)：在 `common/` 定义接口类型和请求，再在 Vue 页面连接表格、表单和操作状态。
- [字典与设置](/tutorials/settings-and-dictionaries)：字典与 Setting 的职责边界，以及修改设置和恢复默认值的操作。

## 身份、认证与权限

- [接入身份与权限](/tutorials/identity-and-permission)：为业务接口接入权限，并选择各层应使用的 Subject、Account 或 Principal。
- [接入认证与 Session](/tutorials/authentication-and-session)：登录、敏感操作验证和 Session 建立的流程，以及密码登录的最小调用示例。

## 消息、任务与实时事件

- [发布消息与通知](/tutorials/messaging-and-notification)：在业务模块之间发布消息，并用本地 Mailpit 发送一封通知邮件。
- [执行和调度任务](/tutorials/jobs-and-scheduling)：登记周期扫描，并提交可恢复的后台工作。
- [推送浏览器事件](/tutorials/realtime-events)：建立浏览器实时连接，业务提交后推送刷新通知。

## 文件

- [上传和引用文件](/tutorials/file-lifecycle)：以替换账号头像为例，说明文件从申请上传到清理的流程，以及各步骤失败后的状态。

## 对外接口与网关

- [部署 Gateway 与接入回调](/tutorials/gateway-and-callbacks)：Gateway 如何转发公开 API、长连接和外部回调，以及如何新增外部回调。
- [开放外部 API](/tutorials/external-api)：为第三方程序声明认证端点和开放路由，创建 Client，并使用 Bearer Token 或 HMAC 签名调用。
- [为 App 开放插件 API](/tutorials/app-plugins)：为 App 生成插件 API、编写插件，并完成配置加载、热替换和停止。

## 物联网

- [接入第一台设备](/tutorials/iot-device-access)：创建 Product、发布 ThingModel、登记并启用 Device，再让模拟器连接并上报。
- [查询设备状态与下发命令](/tutorials/iot-state-and-commands)：读取 reported 状态、设置 desired 期望值，并提交 command 执行一次性动作。

## 排障与验证

- [常见问题](/tutorials/faq)：按现象查找原因方向和对应页面。
- [编写与运行测试](/tutorials/testing)：测试分层、运行命令和集成测试的前提。
