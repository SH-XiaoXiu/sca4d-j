# 文件规则

## 业务表保存的字段

业务表保存 fileId 和所属资源；文件模块只管理文件记录、存储对象和状态。禁止文件核心查询具体业务表或硬编码头像、商品、IM 等权限。

## Policy

每个业务场景实现 `FileSignaturePolicy`，按 CurrentRequest、scene、resourceId 和 action 授权。未知场景、未知资源或 Policy 异常必须拒绝。

## Grant

上传与读取使用短期签名 Grant。长期存储凭据禁止返回客户端；临时 URL 禁止写入业务表。签名密钥必须来自安全配置且满足长度要求。

## 生命周期

上传申请、上传完成、业务引用、替换和清理必须对应文件状态变化。业务保存失败留下的文件必须可以按 TTL 清理；对象存储删除失败时记录 `DELETE_FAILED` 并重试。

## 批量读取

列表使用文件引用并批量签名，禁止逐条签名 N+1。View serializer 不得查询业务或文件数据库。

## 存储抽象

业务不得依赖 MinIO bucket/key 或本地路径。所有读写通过 `FileStorage` 与 `FileService`；更换 provider 时业务表仍只保存 fileId。
