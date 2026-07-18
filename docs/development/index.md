# 开发业务功能

## 开发顺序

1. 确定用户操作、输入、输出和错误情况。
2. 添加 Flyway 迁移。
3. 添加 Entity、Mapper 和 Repository。
4. 在 Service 中实现查询或写入过程。
5. 添加 Request、View 和 Controller。
6. 添加权限和数据访问检查。
7. 根据需要添加乐观锁、幂等、审计、消息或文件处理。
8. 更新 TypeScript API 和管理页面。
9. 添加测试并运行构建。

[创建一个完整业务功能](./feature-workflow.md)列出了每一步需要创建的文件和检查结果。

## 专题教程

- [编写 HTTP API](./web-api.md)
- [编写数据库查询和更新](./data-access.md)
- [添加权限和数据访问检查](./permission.md)
- [处理并发、重复请求和审计](./concurrency-and-audit.md)
- [发布消息和实时更新](./messaging-and-realtime.md)
- [接入文件上传和读取](./file-integration.md)
- [开发管理端页面](./frontend.md)

## 公告示例

[公告模块](./example/announcement/)包含平台公告和租户公告的数据库、Service、HTTP、权限、管理端和测试代码。
