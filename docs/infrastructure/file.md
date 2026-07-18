# 文件服务

文件服务把数据库中的文件资源、对象存储内容、上传授权和业务访问权限组合成一个完整生命周期。业务表保存 `fileId`，客户端通过签名接口取得上传指令或短期读取地址。

DEFAULT Profile 头像是文件服务的首个完整管理端应用，场景固定为 `iam.default-profile.avatar`。它覆盖当前 Subject 的默认资料，并同时验证 LOCAL/PROXY 与 MinIO/DIRECT 两条存储链路。

## 存储方式

`FileStorage` 提供两种实现：

- `LOCAL`：保存到本地目录，适合本地开发或单节点文件存储。
- `MINIO`：保存到 MinIO，支持多实例共享对象。

上传方式：

- `DIRECT`：客户端直接向对象存储上传。
- `PROXY`：客户端把内容上传到应用的文件接口。

## 文件状态

```text
PENDING -> READY -> IN_USE
    |        |
    +------> EXPIRED

IN_USE -> DELETE_PENDING -> DELETING -> DELETED
                                  -> DELETE_FAILED
```

`PENDING` 表示等待上传，`READY` 表示上传完成但尚未绑定业务资源，`IN_USE` 表示已经被业务字段消费。

## 申请上传

业务前端调用：

```http
POST /files/signatures
```

每个签名项包含操作类型 `UPLOAD`、业务场景、业务资源 ID、文件名、Content-Type 和大小。`FileSignaturePolicy` 检查当前 Principal 是否可以向目标业务资源上传文件。

签名成功后返回 `fileId`、上传方式、地址、Header 和短期 `uploadGrant`。PROXY 上传使用：

```http
PUT /files/uploads/{fileId}/content
PUT /files/uploads/{fileId}/completion
```

DIRECT 上传完成后同样调用 complete 接口确认对象元数据。

## 绑定业务资源

业务 Service 保存文件字段时调用：

```java
fileService.consumeFile(
        fileId,
        uploadGrant,
        ImFileSignaturePolicy.MESSAGE_ATTACHMENT,
        conversationId.toString(),
        principalId);
```

文件从 `READY` 原子进入 `IN_USE`，同一个临时文件不能被不同业务字段重复消费。

默认头像替换使用 `profileVersion` 做乐观锁校验。新文件消费、`iam_default_profile.avatar_file_id` 更新以及旧文件进入 `DELETE_PENDING` 在同一业务事务中完成；并发修改只有一个可以成功。创建 Account 时头像签名没有 `resourceId`，绑定时由创建流程确定 DEFAULT Principal。

## 读取文件

读取签名项使用 `READ` 操作。Policy 验证目标业务资源与 fileId 的关系后，`FileService` 返回短期访问地址。

LOCAL 或代理读取使用：

```http
GET /files/{fileId}/content?token=<读取授权>
```

MinIO 可以返回预签名对象地址。地址有效期由 `SCA4DJ_FILE_ACCESS_TTL` 控制。

业务 View 返回 `FileSignatureRefView`，不返回永久 URL。管理端按引用批量申请短期地址并缓存到过期前 30 秒；图片加载失败时会使该引用失效并重新签名。头像 READ 策略使用一条集合查询验证每个 fileId 仍真实绑定到对应用户。

## 删除

业务解除文件引用后调用 `requestDelete(fileId)`。文件进入删除队列，对象删除成功后记录为 `DELETED`；失败记录保留重试状态。处理节点取得删除权后以 `updated_at` 记录租约起点，节点中断或终态写入失败时，其他节点在 `SCA4DJ_FILE_CLEANUP_LEASE` 到期后重新处理。对象删除保持幂等，重领任务可以安全处理已经不存在的临时对象和正式对象。过期的 PENDING 和 READY 文件也会进入清理流程。

头像清空、替换和用户删除都会请求清理旧头像。用户在创建页上传后放弃提交产生的 READY 文件由同一 TTL 清理任务回收。
