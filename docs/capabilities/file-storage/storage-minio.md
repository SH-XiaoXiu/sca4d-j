# Storage MinIO 模块

`modules/storage/sca4dj-storage-minio` 使用 MinIO 保存 FileStorage 临时对象和正式对象。客户端通过短期预签名 URL 直接 PUT 或 GET 。模块不保存文件业务状态，不判断访问权限，也不代理客户端内容流。

MinIO 的端点、 Bucket 和预签名 URL 要求见[文件存储实现](/capabilities/file-storage/storage-backends)。文件记录的状态变化见[文件上传、使用与清理](/capabilities/file-storage/file-lifecycle)。

## 公开 API 与状态

模块注册一个 [FileStorage](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/FileStorage.html) 实现。它使用内部端点执行 Bucket 检查、对象元数据读取、复制、读取和删除。它使用公开端点生成客户端可访问的预签名 URL 。内部与公开两个客户端共用区域和凭据。

模块没有数据库表。对象位于配置的 Bucket 中。 backend ID 、内部端点、公开端点、区域、凭据、 Bucket 和是否允许创建 Bucket，来自 `sca4dj.storage.minio`。文件记录中的 backend 、 Bucket 和对象键由 Storage File 模块保存。

## 配置

| 配置键 | 代码默认值 | 作用 |
|---|---|---|
| `sca4dj.storage.minio.enabled` | `true` | 为 `false` 时不注册 MinIO 实现 |
| `sca4dj.storage.minio.backend-id` | `minio` | 文件记录中保存的实现标识 |
| `sca4dj.storage.minio.endpoint` | `http://localhost:9000` | 应用访问 MinIO 使用的内部端点 |
| `sca4dj.storage.minio.public-endpoint` | `http://localhost:9000` | 客户端访问预签名地址使用的公开端点 |
| `sca4dj.storage.minio.region` | `us-east-1` | 内部与公开两个客户端共用的区域 |
| `sca4dj.storage.minio.access-key` | `minioadmin` | Access Key，生产环境必须覆盖 |
| `sca4dj.storage.minio.secret-key` | `minioadmin` | Secret Key，生产环境必须覆盖 |
| `sca4dj.storage.minio.bucket` | `sca4dj` | 对象所在的 Bucket |
| `sca4dj.storage.minio.create-bucket` | `true` | 启动时 Bucket 不存在是否允许创建 |

代码默认值只适用于本机开发。部署时通过受保护的配置覆盖 `access-key` 、 `secret-key`，并把 `endpoint` 、 `public-endpoint` 指向实际地址。

```yaml
sca4dj:
  storage:
    minio:
      endpoint: http://minio.internal:9000
      public-endpoint: https://files.example.internal
      region: us-east-1
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
      bucket: example-app
      create-bucket: false
```

上面的示例把 `create-bucket` 设为 `false`。此时 Bucket 必须已经存在，否则应用启动失败。

## 允许依赖与启动条件

当前直接 Gradle 依赖只有 Core API 。 MinIO Java SDK 是内部实现依赖。应用可以覆盖 `enabled` 的默认值。

应用必须保证以下条件：

- 内部端点可由应用访问，公开端点可由客户端访问；
- 两个端点指向同一 Bucket 和对象；
- Access Key 与 Secret Key 通过受保护配置提供，不能写入日志或返回给客户端；
- Bucket 已存在，或配置允许启动检查创建它；
- 同一应用中 backend ID 不与其他 FileStorage 重复，并按需选择默认 backend 。

## Bucket 或对象操作失败时

- 启动检查无法确认或创建 Bucket 时，Storage Registry 初始化失败。应用不能以不可用 backend 继续运行。
- 上传和读取预签名的最长期限受 MinIO 七天限制，即 604800 秒。超出范围的签名请求被拒绝。 File Service 通常签发更短的期限。过期的签名由 MinIO 拒绝。
- 完成上传时，先读取实际对象的媒体类型和大小。对象不存在、内容不符和 MinIO 不可用，分别由 File Service 转换为不同的稳定结果。
- promote 复制临时对象到正式键，再删除临时对象。复制成功但首次删除失败，不会撤销正式对象。清理任务会再次幂等删除临时键。
- PUT/GET URL 只访问对象字节。应用仍须在签发前完成权限检查。公开端点或反向代理都不能使整个 Bucket 变为匿名可读。
- 模块不迁移历史文件。修改默认 backend 只影响新记录，旧记录继续按创建时保存的 backend ID 访问。

## 示例：本机开发环境

下面的命令启动一个本机 MinIO 容器，并使用与配置示例一致的凭据。该命令只用于本机开发，生产环境不应使用默认凭据。

```bash
docker run -d --name example-minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  quay.io/minio/minio server /data --console-address ":9001"
```

本机开发时，`endpoint` 与 `public-endpoint` 都可以设为 `http://localhost:9000`。 Bucket 由应用在启动时创建，前提是 `create-bucket` 为 `true`。
