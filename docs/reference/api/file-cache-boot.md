# 文件、缓存与启动检查

## 文件服务

| 包 | 类型 | 主要方法 | 默认实现与失败行为 |
| --- | --- | --- | --- |
| `com.sca4d.file` | `FileService` | `createUpload(...)`、`writeUpload(...)`、`completeUpload(...)`、`requireReadyFile(...)`、`consumeFile(...)`、`createAccessUrls(...)`、`requestDelete(...)`、`openContent(...)`、`cleanup()` | `DefaultFileService`；所有者、状态、授权或存储对象不符时抛出文件业务错误 |
| `com.sca4d.file.signature` | `FileSignatureService` | `sign(items, currentRequest)` | `DefaultFileSignatureService`；任一项失败时整批不签发 |
| `com.sca4d.file.signature` | `FileSignaturePolicy` | `scenes()`、`authorize(...)` | 业务模块实现；同一 Policy 每批调用一次 |
| `com.sca4d.file.signature` | `FileSignatureRegistry` | 按 scene 查找 Policy | 启动时拒绝重复 scene |
| `com.sca4d.file.storage` | `FileStorage` | `provider()`、`bucket()`、`prepareUpload(...)`、`writeTemporary(...)`、`stat(...)`、`promote(...)`、`createAccessUrl(...)`、`open(...)`、`delete(...)` | `LocalFileStorage` 或 `MinioFileStorage` |
| `com.sca4d.file.storage` | `FileStorageProvider` | `LOCAL`、`MINIO` | 由配置选择 |

`FileServiceUnavailable` 在文件功能未启用时替代 `FileService`。调用文件方法会失败，不会返回伪造地址。

## 文件模型与授权

| 包 | 类型 | 内容或主要方法 |
| --- | --- | --- |
| `com.sca4d.file.storage` | `FileContent` | 文件名、contentType、sizeBytes 和需要关闭的输入流 |
| `com.sca4d.file.storage` | `FileStorageMetadata` | 存储对象的实际大小和 contentType |
| `com.sca4d.file` | `FileAccessUrl` | fileId、短期访问地址和 expiresAt |
| `com.sca4d.file.signature` | `FileSignatureItem` | operation、scene、ref、fileId、resourceId；scene 必须为至少三级点分编码 |
| `com.sca4d.file` | `FileResource` | 文件资源、对象信息、所有者和状态 |
| `com.sca4d.file.storage` | `FileUploadInstruction` | 上传方式、URL、Header 和过期时间 |
| `com.sca4d.file.signature` | `FileGrantSigner` | `issueUpload(...)`、`requireUpload(...)`、`issueRead(...)`、`requireRead(...)` |
| `com.sca4d.file.signature` | `FileUploadGrantClaims` | fileId、scene、resourceId、principalId 和 expiresAt |
| `com.sca4d.file.signature` | `FileReadGrantClaims` | fileId 和 expiresAt |

`FileGrantSigner` 启动时拒绝短于 32 字节的密钥。授权格式、签名、文件 ID、业务场景、Principal 或过期时间不符时返回 `FILE_UPLOAD_GRANT_INVALID`。

## 文件持久化与清理

| 包 | 类型 | 主要方法或结果 |
| --- | --- | --- |
| `com.sca4d.file.repository` | `FileRepository` | 创建上传记录、批量读取、完成上传、修改状态、过期上传和领取清理记录 |
| `com.sca4d.file` | `FileCleanupJob` | Quartz Job；调用 `FileService.cleanup()` |

清理批次必须大于 0。单个对象删除失败时保留可重试状态，不把记录标记为已删除。

## 缓存

| 包 | 类型或 Bean | 默认值与限制 |
| --- | --- | --- |
| `com.sca4d.config` | `CacheConfig.CACHE_MANAGER` | 默认 Spring Cache Manager；Redis 可用时指向 `redisCacheManager` |
| `com.sca4d.config` | `CacheConfig.REDIS_CACHE_MANAGER` | JSON value、String key，不缓存 null |
| `com.sca4d.config` | `CacheConfig.LOCAL_CACHE_MANAGER` | Caffeine 本地缓存，不在实例间共享 |
| `com.sca4d.config.properties` | `Sca4djCacheProperties` | keyPrefix=`sca4dj`，Redis TTL=30 分钟，本地 TTL=5 分钟，本地最大 1000 项 |

## 启动检查

包：`com.sca4d.utils.boot`

| 类型 | 主要方法或字段 | 失败行为 |
| --- | --- | --- |
| `BootCheck` | `name()`、`policy()`、`check(environment)` | 每个启用检查在独立虚拟线程中执行 |
| `BootCheckPolicy` | enabled、criticality、timeout | 生产类环境中的 REQUIRED 失败会阻止启动 |
| `BootCheckResult` | `available(...)`、`unavailable(...)` | 保存可用状态和诊断消息 |
| `BootUtils` | `getEnv()`、`isProductionLike()`、`healthResults()`、`latestResults()` | 超时、中断和异常转换为 unavailable；未初始化调用 `getEnv()` 抛出异常 |
| `BootCheckHealthIndicator` | Actuator HealthIndicator | 使用最新检查结果输出健康状态 |

MinIO 检查实现为 `com.sca4d.file.storage.MinioBootCheck`。`BootEnvironment` 的生产类环境包括 staging 和 production。
