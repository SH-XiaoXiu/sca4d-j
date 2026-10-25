# Storage File 模块

`modules/storage/sca4dj-storage-file` 管理文件记录从等待上传到删除完成的生命周期。它把文件元数据和状态保存在 PostgreSQL，并按每条记录的 backend ID 调用 FileStorage 。它不保存字节，不判断业务权限，也不定义 HTTP 路由。

上传申请、完成确认、业务绑定、释放和清理顺序见[文件上传、使用与清理](/capabilities/file-storage/file-lifecycle)。本地目录和 MinIO 怎样保存字节见[文件存储实现](/capabilities/file-storage/storage-backends)。

## 公开 API

模块实现 Core [FileService](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/FileService.html)。它提供以下能力：创建客户端签名上传、接收服务端内容流、完成确认、读取 StoredFile 、绑定、释放、丢弃未绑定文件、批量签发读取地址，以及处理本地存储签名传输。

应用通过 [FileUse](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/FileUse.html) 提交已确认的 scene 和目标 Resource 。

模块发现应用注册的全部 [FileStorage](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/FileStorage.html)，并校验 backend ID 唯一和默认 backend 存在。每条文件记录固定使用创建时的 backend 、容器和对象键。

## 模块拥有的状态

`storage_file` 保存以下内容：文件 ID 、创建文件的 Principal 、 scene 、目标 Resource 、 backend 、容器、临时与正式对象键、声明和实际内容信息、状态、上传与 READY 到期时间、删除租约、重试次数和错误摘要。

StoredFile 只返回应用授权所需的子集，不泄露对象位置或传输凭据。

模块还定义上传、读取、 READY 保留期和最大字节数设置，以及清理间隔、删除租约与重试参数。字节的保存由 FileStorage 实现负责。

## 允许依赖

当前直接 Gradle 依赖是 Core API 。 Core Spring 作为 `implementation` 依赖，提供统一 JSON 、分布式锁、设置读写、 SecretKeyring 等实现。

模块自身使用 MyBatis 和 Spring 事务，以及 Core 的 Job 和 Scheduling 接口约定。它不依赖 Local、MinIO 或具体应用。

应用可以同时注册多个 FileStorage，但业务模块不能通过另一个模块的内部 Repository 访问文件表。

## 启动条件

自动配置始终提供 FileService 、文件 Mapper 、 StorageRegistry 、传输 Token 签发器和清理 Job/计划。应用必须提供：

- 主 PostgreSQL 、事务管理器、 IdGenerator 、 DistributedLock 、 SettingReader 、 SettingWriter 、 SecretKeyring 、 AppName 和 Clock；
- 至少一个 FileStorage，且配置的默认 backend ID 能唯一匹配；
- JobExecutor 和 JobScheduler，供文件清理计划提交持久化 Job；
- 本地传输需要应用提供实际匿名 PUT/GET Controller 。模块不会自动暴露路由。

## 配置

模块的清理与重试参数位于 `sca4dj.storage` 下。以下默认值来自 `FileProperties`：

| 配置键 | 默认值 | 作用 |
|---|---:|---|
| `sca4dj.storage.backend.default` | `minio` | 新文件使用的 backend ID |
| `sca4dj.storage.transfer-base-url` | `http://localhost:8080` | 生成本地传输地址使用的基础地址 |
| `sca4dj.storage.cleanup-interval` | `PT10M` | 清理计划的运行间隔 |
| `sca4dj.storage.delete-lease` | `PT2M` | 清理任务领取记录后的删除租约时长 |
| `sca4dj.storage.delete-retry-delay` | `PT10M` | 删除失败后的固定重试间隔 |

这些值都必须为正数。文件存储实现的配置见[文件存储实现](/capabilities/file-storage/storage-backends)。

## 示例

下面的服务在读取文件内容前检查业务权限，再通过 `openForServer` 读取。内容流在 try-with-resources 中关闭。

```java
import com.sca4d.core.api.storage.FileContent;
import com.sca4d.core.api.storage.FileService;
import java.io.IOException;
import java.io.OutputStream;

public class WorkspaceAttachmentExporter {

    private final FileService fileService;
    private final WorkspaceAccessChecker accessChecker;

    public WorkspaceAttachmentExporter(
            FileService fileService,
            WorkspaceAccessChecker accessChecker) {
        this.fileService = fileService;
        this.accessChecker = accessChecker;
    }

    /**
     * 把附件内容写入 output。调用前必须已经检查当前 Principal 可以读取 Workspace。
     * 文件不存在、状态不可读或已过期时，openForServer 抛出异常，不写入任何内容。
     */
    public void export(long workspaceId, long fileId, OutputStream output) throws IOException {
        accessChecker.requireReadable(workspaceId);
        try (FileContent content = fileService.openForServer(fileId)) {
            content.stream().transferTo(output);
        }
    }
}

interface WorkspaceAccessChecker {

    /** 读取 Workspace 前检查当前 Principal，无权限时抛出应用异常。 */
    void requireReadable(long workspaceId);
}
```

`FileContent` 提供 `stream()` 、 `contentType()` 与 `sizeBytes()` 等访问方法，读取完成后必须关闭。 `openForServer` 在返回内容前检查状态，过期的 `READY` 文件和删除中的文件都会被拒绝。

## 失败时记录怎样保留

- 应用先负责权限和场景限制。模块只校验上传 Principal 、 FileUse 、状态、期限、实际媒体类型和精确字节数。
- 对象存储调用不占用长数据库事务。对象转正后，状态写入前中断，可由重复完成恢复。
- 上传请求签发失败会留下可到期清理的 `PENDING` 记录。不会返回 backend 、容器、对象键或底层异常细节。
- READY 文件到期后，立即拒绝绑定和读取，即使清理计划尚未运行。
- release 只登记异步删除。清理任务用数据库租约领取。单个对象删除失败时，写入 `DELETE_FAILED` 并等待重试，不阻止同批其他文件。
- 模块不保证业务释放后字节立即删除。应用必须先撤销业务引用和读取授权。

字节实现见 [Storage Local 模块](/capabilities/file-storage/storage-local)和 [Storage MinIO 模块](/capabilities/file-storage/storage-minio)。

## 扩展方式

应用通过以下两种方式扩展文件模块：

1. 注册 FileStorage Bean 。模块在启动时收集全部 FileStorage，并按 `backendId()` 建立索引。实现方式见[文件存储实现](/capabilities/file-storage/storage-backends)。
2. 应用通过 `PermissionPolicy` 实现文件权限判断，并按 `FileUse.scene` 区分业务场景。源码中未定义按 scene 注册场景规则的机制，权限检查由应用负责。规则写法见[文件开发规则](/capabilities/file-storage/development)。

清理计划由模块自动配置提供，应用不需要注册清理任务。
