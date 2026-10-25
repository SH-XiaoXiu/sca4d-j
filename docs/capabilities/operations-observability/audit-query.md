# 审计查询与关联补齐

AuditQueryService 只读查询审计事实及其资源关联。应用先完成身份、操作权限和资源范围检查，再调用查询服务。模块不提供 HTTP 路由，也不提供应用的授权策略。

## 查询方法

`loadById(id)` 接受正数标识。记录不存在时抛出 IllegalArgumentException 。

`page(query)` 按 `occurred_at DESC, id DESC` 返回分页结果。查询条件由 AuditQuery 提供，支持身份、操作、结果、主目标和半开时间区间筛选。关联资源条件通过 `EXISTS` 在分页前生效，一条记录关联多个目标时不会重复出现。 `AuditQuery.matchingResources` 同时匹配主目标和关联目标。空集合明确匹配零条记录。

`relatesTo(id, resource)` 只检查已经保存的关联，适用于应用在资源授权之后校验详情标识。它不会推断当前业务归属，也不能代替应用的授权。

`scan(afterId, throughId, size)` 按主键扫描有界区间 `(afterId, throughId]`。 `size` 的范围是 1 到 500，超出范围时抛出 IllegalArgumentException 。应用可保存最后一条标识作为下次 `afterId`，继续扫描，不依赖容易漂移的分页偏移。

## 关联补齐

AuditAssociationService 为已有记录追加有历史证据的关联。调用方负责授权、可信证据和补齐范围。服务拒绝不存在的记录，重复写入保持幂等，不改写原日志内容、时间、主体或结果。应用不得直接访问 AuditRepository 、 AuditMapper 或审计私有表来绕过这一约定。

## 示例

下面的服务先检查资源授权，再读取审计详情。审计详情的资源关联由 `relatesTo` 确认。

```java
import com.sca4d.audit.query.AuditAssociationService;
import com.sca4d.audit.query.AuditEntry;
import com.sca4d.audit.query.AuditQueryService;
import com.sca4d.core.api.resource.Resource;
import java.util.List;

public class WorkspaceAuditReader {

    private final AuditQueryService queryService;
    private final AuditAssociationService associationService;
    private final WorkspaceAccessChecker accessChecker;

    public WorkspaceAuditReader(
            AuditQueryService queryService,
            AuditAssociationService associationService,
            WorkspaceAccessChecker accessChecker) {
        this.queryService = queryService;
        this.associationService = associationService;
        this.accessChecker = accessChecker;
    }

    /** 读取审计详情。调用方必须先具备读取该 Workspace 审计的权限。 */
    public AuditEntry loadForWorkspace(long auditId, Resource workspace) {
        accessChecker.requireAuditReadable(workspace);
        if (!queryService.relatesTo(auditId, workspace)) {
            throw new IllegalArgumentException("审计记录与 Workspace 无关");
        }
        return queryService.loadById(auditId);
    }

    /** 为已有审计记录补充关联。调用方必须提供可信证据。 */
    public void associate(long auditId, List<Resource> resources) {
        associationService.associate(auditId, resources);
    }

    /** 按主键顺序读取一批审计记录，每批最多 500 条。 */
    public List<AuditEntry> scanRange(long afterId, long throughId, int batchSize) {
        return queryService.scan(afterId, throughId, batchSize);
    }
}

interface WorkspaceAccessChecker {

    /** 检查当前 Principal 是否可以读取目标 Resource 的审计记录。 */
    void requireAuditReadable(Resource target);
}
```

`relatesTo` 只返回是否存在已保存的关联。返回 `false` 时，示例抛出异常，不返回审计详情。 `scanRange` 返回一批记录，调用方保存最后一条记录的标识，作为下一次扫描的 `afterId`。