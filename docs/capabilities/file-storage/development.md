# 文件开发规则

本页说明应用接入文件功能时必须遵守的规则。规则覆盖业务表保存的字段、权限策略、短期签名地址、生命周期、批量读取和存储抽象。

## 业务表保存的字段

业务表保存 `fileId` 和所属业务对象的标识。文件模块只管理文件记录、存储对象和文件状态。

禁止以下做法：

- 文件核心代码直接查询具体业务表；
- 文件核心代码硬编码某个业务类型（例如 Attachment 、 Document）的权限；
- 业务表保存短期读取地址、对象键或本地路径。

## 权限策略

应用通过 `PermissionPolicy` 判断文件权限。`FileUse.scene` 是应用定义的业务场景编码。源码中未定义按 scene 注册场景规则的机制，应用需要在自己的权限策略中按 scene 区分规则。

权限判断应核对以下内容：目标 Resource 类型、当前 Principal 、上传者，以及 `READ` 、 `WRITE` 动作。文件基础代码不依赖具体业务类型。

没有权限规则的 scene 必须拒绝访问。读取拒绝不得暴露文件是否存在。

## 短期签名地址

上传与读取使用短期签名地址。长期存储凭据不返回客户端。临时 URL 不写入业务表。签名密钥必须来自安全配置，并且满足长度要求。

## 生命周期

上传申请、上传完成、业务引用、替换和清理必须对应文件状态变化。

业务保存失败留下的文件必须可以按 TTL 清理。对象存储删除失败时，记录 `DELETE_FAILED` 并重试。

## 批量读取

业务响应只返回 `fileId`，不返回或持久化短期 URL 。

客户端的文件 Store 负责去重、合并同时发生的请求、按每批一百个文件签发、在到期前刷新，并按 Session 隔离缓存。页面和 View serializer 不逐条签名，也不逐条查询文件数据库。

## 存储抽象

业务代码不依赖 MinIO bucket 、对象键或本地路径。

业务代码通过 FileService 创建、上传、绑定和读取文件。 FileStorage 只供文件模块按记录中的 backend 执行对象操作。

更换存储实现或改用服务端上传时，业务表仍只保存 `fileId`。

## 示例

下面的服务在事务内把新附件绑定到 Workspace，并释放原附件。 `Workspace` 是应用类型，实现 `Resource`，它的 `type()` 与 `id()` 按应用的资源类型声明。

```java
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.storage.FileService;
import com.sca4d.core.api.storage.FileUse;
import com.sca4d.core.api.storage.StoredFile;
import org.springframework.transaction.annotation.Transactional;

public class WorkspaceAttachmentService {

    /** 文件 scene 名称，由 Workspace 模块拥有。 */
    private static final String SCENE = "workspace-attachment";

    private final FileService fileService;
    private final WorkspaceAttachmentRepository repository;

    public WorkspaceAttachmentService(
            FileService fileService,
            WorkspaceAttachmentRepository repository) {
        this.fileService = fileService;
        this.repository = repository;
    }

    /**
     * 在事务内绑定新附件，并释放 Workspace 原来保存的附件。
     * 调用方必须已经检查当前 Principal 对 Workspace 的维护权限。
     */
    @Transactional
    public void replaceAttachment(Workspace workspace, long newFileId) {
        StoredFile file = fileService.bind(newFileId, new FileUse(SCENE, workspace));
        Long oldFileId = repository.swapAttachment(workspace.id(), file.fileId());
        if (oldFileId != null) {
            fileService.release(oldFileId, new FileUse(SCENE, workspace));
        }
    }
}
```

同一个业务事务内完成 `bind` 、业务表更新和 `release`。事务失败时，数据库变化回滚。文件对象的删除在事务提交后由清理任务执行，业务请求不等待删除结束。
