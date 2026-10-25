# 上传和引用文件

本页以「为工作区文档添加附件」为例，说明文件从申请上传到清理的完整流程，以及各步骤失败后的状态。适合需要在业务中接入文件上传或引用文件的开发者。

场景：工作区（Workspace）的成员（Member）为文档（Document）上传附件（Attachment）。附件可以被同一工作区的成员读取，也可以被删除。

需要解决四个问题：

- 客户端怎样把字节传到对象存储，而不经过业务服务。
- 上传完成后，业务对象怎样引用该文件。
- 删除附件时，旧文件怎样清理。
- 上传中断或事务回滚后，文件记录处于什么状态。

上传文件依次完成申请、传输字节、确认上传和绑定业务对象。只有绑定完成，文件才成为业务引用。业务表保存 `fileId`，显示时申请短期读取地址。

前置条件：已登录，能通过 Gateway 的 `/api` 入口调用接口，并已按[身份与权限](/tutorials/identity-and-permission)为文档接口配置了 Principal 选择。存储地址和 backend 选择见[运行配置](/reference/runtime/sca4d-server)。

本页的 `DocumentAttachmentController`、`DocumentAttachmentService`、`AttachmentRepository` 等类型由具体应用定义。

## 实现步骤

### 1. 申请上传

应用先检查当前 Principal 是否可以维护目标文档，并校验媒体类型和大小，再调用 `FileService.createUpload`。`FileUse` 由场景名和目标 Resource 组成，后续绑定与释放必须使用相同的 `FileUse`。

```java
@PostMapping("/workspaces/{workspaceId}/documents/{documentId}/attachments/uploads")
@RequirePermission(
        resource = "#documentId",
        resourceType = Document.RESOURCE_TYPE_VALUE,
        action = Action.UPDATE_VALUE)
public ApiResponse<FileUploadView> createUpload(
        @PathVariable long workspaceId,
        @PathVariable long documentId,
        @CurrentPrincipal WorkspaceMemberPrincipal member,
        @RequestBody @Valid CreateAttachmentUploadRequest request) {
    if (!ALLOWED_CONTENT_TYPES.contains(request.getContentType())
            || request.getSizeBytes() > MAX_SIZE_BYTES) {
        throw new BusinessException(FileErrorCode.INVALID);
    }
    FileUpload upload = files.createUpload(
            member,
            new FileUploadIntent(
                    attachmentUse(documentId),
                    request.getFilename(),
                    request.getContentType(),
                    request.getSizeBytes()));
    return ApiResponse.created(FileUploadView.from(upload)); // 由具体应用定义
}

static FileUse attachmentUse(long documentId) {
    return new FileUse(
            "DOCUMENT_ATTACHMENT",
            new Resource.Ref(Document.RESOURCE_TYPE, Long.toString(documentId)));
}
```

`ALLOWED_CONTENT_TYPES` 与 `MAX_SIZE_BYTES` 由具体应用定义。`FileUploadIntent` 要求文件名长度为 1 到 255、媒体类型符合 `type/subtype` 格式且长度不超过 128、字节数为正数，不满足时构造方法抛出 `IllegalArgumentException`。`FileUse` 的场景名长度不超过 64。

请求示例：

```http
POST /api/workspaces/101/documents/9001/attachments/uploads
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "filename": "plan.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 482113
}
```

响应为 201，数据包含 `fileId`、`method`、`url`、`headers` 和 `expiresAt`，对应 `FileUpload` 的同名访问器。

### 2. 上传字节

客户端按响应中的 `method`、`url` 和 `headers` 上传原始文件字节。发送到对象存储地址的请求不附加管理端的 `Authorization` 头，文件内容也不包装成 JSON。

```bash
curl -X PUT "<url>" \
  -H "<headers 中的每一项>" \
  --data-binary @plan.pdf
```

服务端持有内容流时，调用 `FileService.upload(uploader, intent, inputStream)`。它创建相同的 `PENDING` 记录，并在方法内完成核验。

两条路径都只写临时对象，不能覆盖正式对象。

### 3. 确认上传

由创建文件的 Principal 调用 `completeUpload`。文件服务检查临时对象的实际媒体类型和字节数，再把对象转为正式对象，并将记录改为 `READY`。

```java
@PostMapping("/workspaces/{workspaceId}/documents/{documentId}/attachments/uploads/{fileId}/completion")
@RequirePermission(
        resource = "#fileId",
        resourceType = StoredFile.RESOURCE_TYPE_VALUE,
        action = Action.WRITE_VALUE)
public ApiResponse<Void> completeUpload(
        @PathVariable long fileId,
        @CurrentPrincipal WorkspaceMemberPrincipal member) {
    files.completeUpload(fileId, member);
    return ApiResponse.noContent();
}
```

该接口声明了对 `StoredFile` 的权限，应用需要注册支持 `StoredFile.RESOURCE_TYPE` 的 `PermissionPolicy`。文件服务本身只检查上传 Principal、文件用途、状态和期限，不知道「谁能维护文档」。

```java
@Component
public final class AttachmentFilePermissionPolicy implements PermissionPolicy {

    private final FileService files;

    private final DocumentAccess access; // 由具体应用定义：判断成员是否属于文档所在工作区

    public AttachmentFilePermissionPolicy(FileService files, DocumentAccess access) {
        this.files = files;
        this.access = access;
    }

    @Override
    public boolean supports(Resource resource) {
        return StoredFile.RESOURCE_TYPE.equals(resource.type());
    }

    @Override
    public PermissionDecision decide(Call call, Permission permission) {
        StoredFile file = files.loadById(Long.parseLong(permission.resource().id()));
        long current = call.principal().orElseThrow().principalId();
        if (Action.WRITE.equals(permission.action())) {
            return file.uploaderPrincipalId() == current
                    ? PermissionDecision.allow()
                    : PermissionDecision.deny("只有上传者可以完成上传");
        }
        // 读取：文件所属文档的工作区成员可以读取
        long documentId = Long.parseLong(file.use().target().id());
        return access.canRead(current, documentId)
                ? PermissionDecision.allow()
                : PermissionDecision.deny("无权读取该文件");
    }
}
```

### 4. 绑定业务对象

业务 Service 在自己的事务中调用 `bind`，同时把文件 ID 写入业务表。文件记录变为 `IN_USE`。同一文档的并发变更用分布式锁串行化。

```java
public Attachment attach(WorkspaceMemberPrincipal member, long documentId, long fileId) {
    return distributedLock.execute(
            new LockKey(TreeCode.ofColon("WORKSPACE:DOCUMENT"), Long.toString(documentId)),
            Duration.ofSeconds(10),
            () -> transactions.execute(status -> {
                FileUse use = attachmentUse(documentId);
                StoredFile file = files.loadById(fileId);
                // 新文件必须属于目标文档，并由当前成员创建
                if (!file.use().scene().equals(use.scene())
                        || !file.use().target().type().equals(use.target().type())
                        || !file.use().target().id().equals(use.target().id())
                        || file.uploaderPrincipalId() != member.principalId()) {
                    throw new BusinessException(FileErrorCode.NOT_FOUND);
                }
                if (file.status() != FileStatus.READY) {
                    throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                }
                files.bind(fileId, use);
                return attachments.insert(documentId, fileId, member.principalId());
            }));
}
```

`distributedLock` 是 `DistributedLock`，`transactions` 是 Spring 的 `TransactionTemplate`，`attachments` 是应用自己的 Repository。

### 5. 读取附件

业务表保存 `fileId`。显示时批量申请短期读取地址，访问地址过期后重新申请。

```java
@PostMapping("/workspaces/{workspaceId}/documents/{documentId}/attachments/access-urls")
@RequirePermission(
        resource = "#request.fileIds",
        resourceType = StoredFile.RESOURCE_TYPE_VALUE,
        action = Action.READ_VALUE)
public ApiResponse<List<FileAccessUrlView>> accessUrls(
        @RequestBody @Valid CreateAccessUrlsRequest request) {
    List<Long> ids = request.getFileIds().stream().distinct().toList();
    try {
        return ApiResponse.ok(files.createAccessUrls(ids).stream()
                .map(FileAccessUrlView::from) // 由具体应用定义
                .toList());
    } catch (BusinessException failure) {
        // 不向调用方区分具体失败的文件
        if (failure.errorCode() == FileErrorCode.NOT_FOUND
                || failure.errorCode() == FileErrorCode.STATE_CONFLICT) {
            throw new BusinessException(FileErrorCode.NOT_FOUND, failure);
        }
        throw failure;
    }
}
```

`createAccessUrls` 一次接受 1 至 100 个文件 ID。本地签名路由通过 `openAccess` 验签并重新检查状态。

服务端需要解析上传内容时，调用 `FileService.openForServer(fileId)`，并在 try-with-resources 中消费返回的 `FileContent`。

```java
try (FileContent content = files.openForServer(fileId)) {
    parser.parse(content.stream(), content.contentType());
}
```

`openForServer` 不签发客户端地址，也不替代应用的权限和用途校验。只有未过期的 `READY` 和已绑定的 `IN_USE` 文件可以打开。

### 6. 删除附件并释放文件

业务引用被删除或替换时，业务 Service 调用 `release`，并与业务表更新放在同一事务。文件记录变为 `DELETE_PENDING`，业务请求不等待实际删除结束。

```java
public void remove(long documentId, long attachmentId) {
    distributedLock.execute(documentLock(documentId), Duration.ofSeconds(10), () -> {
        transactions.executeWithoutResult(status -> {
            Attachment attachment = attachments.load(documentId, attachmentId);
            attachments.delete(attachmentId);
            files.release(attachment.fileId(), attachmentUse(documentId));
        });
        return null;
    });
}
```

替换附件时，在同一事务中先 `bind` 新文件、更新业务引用，再 `release` 旧文件。任一步失败，这些数据库变化一起回滚。

应用取消尚未使用的上传时，调用 `FileService.discard(fileId, use)`。用途必须与上传申请一致。只允许 `PENDING` 或 `READY` 文件登记为 `DELETE_PENDING`。已绑定的 `IN_USE` 文件不能丢弃，应使用 `release`。`discard` 同样参与调用方事务。

### 7. 清理

清理任务按状态、重试时间和租约到期条件执行 `UPDATE ... RETURNING`，分批取得文件删除租约，领取不使用显式行锁。任务在事务外删除临时对象和正式对象。成功后记录变为 `DELETED`，失败时记录重试时间并变为 `DELETE_FAILED`。
绑定和释放随业务事务提交或回滚，实际删除只由清理任务在事务外异步执行。

调用方只登记清理，不等待实际对象删除。

## 验证

1. 申请上传，预期 201，取得 `fileId`、`method`、`url` 和 `headers`。
2. 按响应上传字节，预期对象存储返回成功。
3. 调用完成接口，预期 204，文件进入 `READY`。
4. 调用绑定接口，预期附件出现在文档的附件列表，文件进入 `IN_USE`。
5. 申请读取地址并用返回的 URL 下载，预期内容与上传一致。
6. 删除附件，预期附件从列表消失，文件进入 `DELETE_PENDING`，随后由清理任务转为 `DELETED`。

再验证两个失败场景：

- 另一名成员调用步骤 3 的完成接口，预期 404（`FileErrorCode.NOT_FOUND`），不暴露文件是否存在。
- 对已经绑定的文件重复调用 `bind`，预期返回现有结果。

对服务层补充测试：覆盖绑定成功、旧文件释放、事务回滚后 `bind` 与业务表保持一致。集成测试放在 `src/integrationTest`，需要 Docker，运行方式见[编写与运行测试](/tutorials/testing)。

## 失败结果与限制

文件错误码来自 `FileErrorCode`：

| 错误码 | 含义 | HTTP 状态 | 典型条件 |
|---|---|---|---|
| `2500` `INVALID` | 文件请求无效 | 400 | 媒体类型或大小不被允许；读取地址的数量不在 1 到 100 之间 |
| `2501` `NOT_FOUND` | 文件不存在 | 404 | 文件不存在；完成确认的 Principal 不是创建者；批量读取中任一文件不可用 |
| `2502` `UPLOAD_EXPIRED` | 上传已过期 | 409 | 完成确认或服务端上传时已超过上传期限 |
| `2503` `STATE_CONFLICT` | 文件状态冲突 | 409 | 状态不允许当前操作；`READY` 保留期已结束后绑定 |
| `2504` `CONTENT_MISMATCH` | 内容与申请不一致 | 400 | 实际媒体类型或大小与申请不符 |
| `2505` `BACKEND_UNAVAILABLE` | 存储实现不可用 | 503 | 对象存储无法读写 |
| `2506` `TRANSFER_INVALID` | 传输 token 无效 | 400 | 上传或访问 token 验证失败 |

- 客户端上传请求无法签发，或服务端上传的内容流无法写入时，已创建的 `PENDING` 记录等待到期清理。调用方不会得到存储凭据、容器或对象键。
- 完成确认找不到对象时返回无效上传。实际类型或大小不符时返回内容不匹配。存储实现不可用时返回存储不可用。
- `completeUpload` 对已经 `READY` 或 `IN_USE` 的文件复用现有结果，同一用途的 `bind` 和已经进入删除流程的 `release` 也可以安全重试。对象转正成功但数据库状态尚未更新时，再次执行完成操作可以恢复中断。
- `READY` 文件过期后，不能再绑定或生成读取地址，即使清理任务尚未删除对象。
- 只有未过期的 `READY` 和已绑定的 `IN_USE` 文件可以打开。其他状态，或 `READY` 保留期限已结束时，`openForServer` 返回文件不存在。对象打开失败时继续报告存储错误或 `IOException`，不会返回空内容。
- 文件释放后，字节可能要等清理任务重试成功才消失。业务代码必须先撤销引用和读取权限，不能等待对象删除后才提交业务事务。
- 实际删除在事务提交后异步执行，因此删除失败不会撤销已经完成的附件替换。

## 相关页面

- [文件生命周期](/capabilities/file-storage/file-lifecycle)
- [接入身份与权限](/tutorials/identity-and-permission)
- [开发管理端功能](/tutorials/admin-feature#文件传输)
- [运行配置](/reference/runtime/sca4d-server)
- [编写与运行测试](/tutorials/testing)
- [StoredFile](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/StoredFile.html)
- [FileStatus](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/FileStatus.html)
