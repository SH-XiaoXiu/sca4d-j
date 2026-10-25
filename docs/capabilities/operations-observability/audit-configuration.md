# 审计配置

源码：`modules/audit/sca4dj-audit/src/main/java/com/sca4d/audit/config/AuditProperties.java`

AuditProperties 读取 `sca4dj.audit` 下的记录方式和 payload 保存配置。 `@Audit.mode` 的方法显式值优先于类型显式值。两者都是 `DEFAULT` 时，才使用 `sca4dj.audit.mode` 配置。

## 配置项

| 配置键 | 默认值 | 含义 |
|---|---:|---|
| `sca4dj.audit.mode` | `DIRECT` | 应用默认记录方式，可选 `DIRECT` 、 `QUEUED` 、 `AFTER_COMMIT`，不能为 `DEFAULT` |
| `sca4dj.audit.max-payload-bytes` | `65536` | 单条 payload 允许保存的最大 UTF-8 字节数 |
| `sca4dj.audit.sensitive-fields` | `password, token, secret, salt, authorization` | 不区分大小写、递归匹配的敏感字段名片段 |

```yaml
sca4dj:
  audit:
    mode: DIRECT
    max-payload-bytes: 65536
    sensitive-fields:
      - password
      - token
      - secret
      - salt
      - authorization
      - api-key
```

上面的示例在默认列表后增加了 `api-key`。替换列表时，默认片段不会自动保留，需要全部写出。

## payload 大小限制

`max-payload-bytes` 必须为正数。编码并脱敏后的 JSON 超过限制时，模块记录 WARN 日志，并只保存以下固定对象，不保存原 payload 内容：

```json
{"truncated":true,"reason":"payload too large"}
```

## 敏感字段

敏感字段值在写入前替换为 `***`。匹配作用于对象的每一层，数组中的对象也会处理。应用可以替换字段片段列表，但不能依靠敏感字段片段列表保存密码、 Token 、 Authorization Header 或密钥等本来就不应进入 AuditPayload 的数据。

`sensitive-fields` 可以设为空列表，但列表项不能为 `null` 或空白文本。配置包含无效列表项时，属性绑定失败，应用不会在静默忽略脱敏规则后继续启动。

## 执行模式

| 模式 | 写入时机 | 前提 | 失败结果 |
|---|---|---|---|
| `DIRECT` | 等待数据库写入 | Audit 模块的数据库记录器 | 写入失败会让当前调用失败 |
| `QUEUED` | 等待独立事务把消息交给 Outbox | 需要启用 Messaging | 交接失败会让当前调用失败，后续消费失败按消息模块规则重试 |
| `AFTER_COMMIT` | 业务事务提交后把冻结记录交给容量为 1024 的进程内队列 | Audit 模块的提交后记录器 | 不保证进程退出后的恢复，队列拒绝或后台写库失败时记录可能丢失 |

默认方式对应的记录器未注册时，启动失败。方法指定了未注册的方式时，在执行业务方法前失败。交付差异见[审计模型](/capabilities/operations-observability/audit-model)。

选择模式时，先判断记录缺失是否可以接受。审计记录不可缺失，但不要求同步写库的操作使用 `QUEUED`。允许丢失时才使用 `AFTER_COMMIT`。需要审计与业务数据同一事务提交时，使用 `DIRECT`，并保证业务方法与审计写入位于同一个 Spring 事务内。

## 示例：方法级指定模式

下面的声明只为一个接口指定 `QUEUED`，应用默认模式仍为 `DIRECT`。

```java
import com.sca4d.core.api.annotation.Audit;
import org.springframework.stereotype.Service;

@Service
public class AttachmentDeletionService {

    @Audit(
            operation = "workspace/attachment/delete",
            target = "#attachmentId",
            resourceType = Attachment.RESOURCE_TYPE_VALUE,
            mode = Audit.Mode.QUEUED)
    public void deleteAttachment(long attachmentId) {
        // 删除附件并释放文件引用。
    }
}

final class Attachment {

    static final String RESOURCE_TYPE_VALUE = "WORKSPACE/ATTACHMENT";

    private Attachment() {
    }
}
```

如果应用没有启用 Messaging，`QUEUED` 记录器不存在，应用在执行 `deleteAttachment` 前失败。