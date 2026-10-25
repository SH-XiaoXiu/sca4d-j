# Storage Local 模块

`modules/storage/sca4dj-storage-local` 把 FileStorage 对象保存在应用主机的本地目录。它适合单机开发、测试，或明确共享同一文件系统的部署。它不保存文件业务状态，也不自动开放下载目录或 HTTP Controller 。

本地目录的传输方式、路径限制和多实例要求见[文件存储实现](/capabilities/file-storage/storage-backends)。文件记录的状态变化见[文件上传、使用与清理](/capabilities/file-storage/file-lifecycle)。

## 公开 API 与状态

模块注册一个 [FileStorage](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/FileStorage.html) 实现。上传和读取地址由 [StorageTransferSigner](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/storage/StorageTransferSigner.html) 生成，Storage File 模块提供该签发器。真正的 PUT/GET 请求仍由应用提供的匿名路由接收，并调用 FileService 。这些路由使用短期 Token 。

模块没有数据库表。它拥有配置根目录下的对象字节和伴随的媒体类型文件。 backend ID 、命名空间和根目录来自 `sca4dj.storage.local`。临时对象、正式对象和媒体类型文件使用相同的生命周期移动和删除。

## 配置

| 配置键 | 默认值 | 作用 |
|---|---|---|
| `sca4dj.storage.local.enabled` | `true` | 为 `false` 时不注册本地实现 |
| `sca4dj.storage.local.backend-id` | `local` | 文件记录中保存的实现标识 |
| `sca4dj.storage.local.container` | `files` | 新文件使用的容器名称 |
| `sca4dj.storage.local.root` | `docker-data/files` | 对象字节的根目录 |

```yaml
sca4dj:
  storage:
    local:
      enabled: true
      backend-id: local
      container: files
      root: /var/lib/example-app/files
```

## 允许依赖与启动条件

当前直接 Gradle 依赖只有 Core API 。应用可以覆盖 `enabled` 的默认值。

Storage File 模块的自动配置已经注册 StorageTransferSigner，应用不需要另行注册。应用还必须确保：

- 配置根目录可创建、可读写，且不是符号链接；
- 多实例确实看到同一目录。否则，一个实例签发的文件可能在另一个实例上不存在；
- 反向代理把隐藏传输路由交给应用，但不把根目录直接发布为静态目录；
- 传输 Token 查询参数不进入访问日志。

## 路径或磁盘操作失败时

- 对象键必须位于配置命名空间和根目录内。绝对路径、 `..` 、路径逃逸和任意已存在的符号链接都会被拒绝。
- 上传先写随机 `.part` 文件。只有实际流长度与申请值完全一致，才原子替换目标。失败时会清理临时文件。
- 上传和读取地址本身不授予永久权限。 FileService 在每次匿名路由调用时验证动作、到期时间、文件状态和内容条件。
- 本地磁盘故障、目录权限错误或跨设备无法执行原子移动时，操作失败。 File Service 将其转换为稳定的 backend 错误或删除重试。
- 模块不复制文件、不做跨节点同步，也不提供备份。需要对象存储的功能时，选择 [Storage MinIO 模块](/capabilities/file-storage/storage-minio)。

## 示例：匿名传输路由

应用提供两个匿名路由。路由只把请求转交给 FileService，不读取文件记录，也不判断业务权限。

```java
import com.sca4d.core.api.storage.FileContent;
import com.sca4d.core.api.storage.FileService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class LocalFileTransferController {

    private final FileService fileService;

    public LocalFileTransferController(FileService fileService) {
        this.fileService = fileService;
    }

    /** 接收客户端上传内容。token 由 FileService 签发并在服务端验签。 */
    @PutMapping("/files/transfers/uploads")
    public ResponseEntity<Void> receiveUpload(
            @RequestParam("token") String token,
            HttpServletRequest request) throws IOException {
        fileService.receiveUpload(
                token,
                request.getContentType(),
                request.getContentLengthLong(),
                request.getInputStream());
        return ResponseEntity.noContent().build();
    }

    /** 返回文件内容。响应结束后关闭 FileContent。 */
    @GetMapping("/files/transfers/objects")
    public ResponseEntity<StreamingResponseBody> openObject(@RequestParam("token") String token)
            throws IOException {
        FileContent content = fileService.openAccess(token);
        StreamingResponseBody body = output -> {
            try (content) {
                content.stream().transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.sizeBytes()))
                .body(body);
    }
}
```

这两个路由不要求 Session 。反向代理应该只把它们暴露给客户端，并且不记录查询参数中的 `token`。

## 扩展方式

应用通过两个方式使用本模块：

- 用 `sca4dj.storage.local.enabled` 决定是否注册本地实现；
- 注册上方示例中的匿名 Controller，把传输请求交给 FileService 。

路径校验与原子替换是本模块的固定行为，应用不通过配置关闭。
