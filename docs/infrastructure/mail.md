# 邮件发送

系统可以保存多项 SMTP 配置。每项配置包含服务器地址、端口、安全方式、可选认证信息和默认发件资料。新建配置默认为停用状态，管理员可以先测试连接，再显式启用。

## 密码存储

SMTP 密码只在创建或修改请求中接收。接口响应只返回 `passwordConfigured`，不会返回密码、key-id、nonce 或 ciphertext。

数据库分别保存 AES-256-GCM 的 key-id、12 字节 nonce 和包含 authentication tag 的密文。加密时使用 `smtp.sender.<senderId>.password` 作为 additional authenticated data，因此密文不能换到另一个 Sender 或其他敏感字段后继续解密。

密钥来自 `sca4dj.secret-keyring`。新密文使用 `active-key-id`，旧密文按自身 key-id 解密。轮换时必须先加入新密钥，再切换 active key；仍有密文引用的旧密钥不能移除。

## 发送与测试

`MailService.testConnection(senderId)` 允许测试停用配置，只建立并关闭 SMTP 连接，不发送邮件。`MailService.send(...)` 只使用启用配置，并在每次调用时读取当前数据库配置、解密密码和创建独立 JavaMailSender，不在进程内保存明文密码。

支持三种连接方式：

| 类型 | 行为 |
| --- | --- |
| `NONE` | 不启用 TLS，适合本地 Mailpit 或受控网络 |
| `STARTTLS` | 建立 SMTP 连接后要求升级到 TLS |
| `IMPLICIT_TLS` | 从连接开始启用 TLS |

本地 Compose 使用固定版本 Mailpit，SMTP 端口为 `1025`，Web UI 为 `8025`。两个端口默认只监听 `127.0.0.1`。

## 邮件模板

模板以 `family + locale` 作为稳定业务标识，主题、纯文本和可选 HTML 分别保存。模板语法只允许 `{{name}}` 变量；Section、Partial、未转义变量和反射属性名会在保存或预览时被拒绝。HTML 使用 OWASP HTML Sanitizer 清洗，预览和实际发送共用同一渲染入口。

管理端提供 Sender 与模板的桌面表格和移动记录视图。模板预览在隔离的 sandbox iframe 中展示；测试发送只读取已保存模板，并明确选择 Sender、收件地址和变量。

## 当前范围

邮件模块提供 SMTP 配置、连接测试、模板管理、草稿预览和测试发送。Email Code Challenge 与 Account 级 2FA 由认证模块和 IAM 模块处理。
