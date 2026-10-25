# 审计开发规则

本页列出应用声明和维护审计时必须遵守的规则。规则覆盖声明方式、 Actor 与 Target 、 Payload 、执行模式、失败审计和目录查询。

## 声明

需要追溯的业务动作使用 `@Audit` 和已登记的 operation 。禁止在 Service 中手工拼接日志表记录，禁止用普通应用日志替代审计。

## Actor 与 Target

Actor 来自 Call 中已经确认的 Subject 、 Account 和 Principal 。 Target 使用已登记的资源类型和 ID 。 SYSTEM 动作使用系统 Principal，匿名调用不能声明身份审计。

## Payload

复杂详情使用类型化 `AuditPayload` 和 AuditPayloadCodec 。密码、 Token 、密钥、完整请求头和不必要的个人信息禁止记录。展示失败不得破坏审计主记录。

## 执行模式

`DIRECT` 等待数据库写入，写入失败会让当前调用失败。只有审计写入仍位于业务事务内时，业务写入才一起回滚。

`QUEUED` 等待独立 Outbox 事务提交。交接失败会让当前调用失败，后续消费失败按消息模块规则重试。

`AFTER_COMMIT` 在业务事务成功提交后尽力排入进程队列。拒绝入队、进程退出或后台写库失败可能丢失记录，只保留日志。

审计记录不可缺失但不要求同步写库的操作使用 `QUEUED`。允许丢失时才使用 `AFTER_COMMIT`。

## 失败审计

每个操作必须规定是否记录失败结果。失败记录只保存已经取得且可以公开的字段，不把内部异常堆栈写入管理端可见的 Payload 。

## 目录与查询

审计 operation 由业务模块登记，并可在 Catalog 中查询。操作日志服务统一分页、解析 Payload 和资源引用。业务模块不得建立平行的审计记录表。

## 示例：类型化载荷

下面的载荷记录成员角色变化，只保存角色编码，不保存请求的完整正文。 Codec 注册为 Spring Bean 后，模块按 `payloadType()` 与 `version()` 保存和查询。

```java
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditPayloadCodec;

public final class MemberRoleChangedPayload implements AuditPayload {

    private final String fromRole;
    private final String toRole;

    public MemberRoleChangedPayload(String fromRole, String toRole) {
        this.fromRole = fromRole;
        this.toRole = toRole;
    }

    public String getFromRole() {
        return fromRole;
    }

    public String getToRole() {
        return toRole;
    }
}

class MemberRoleChangedPayloadCodec implements AuditPayloadCodec<MemberRoleChangedPayload> {

    @Override
    public String payloadType() {
        return "workspace.member.role-changed";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public Class<MemberRoleChangedPayload> payloadClass() {
        return MemberRoleChangedPayload.class;
    }
}
```

`version()` 默认返回 `1`。类型名或结构发生不兼容变化时，使用新的 `payloadType()` 或增加版本号，并保留旧 Codec 读取历史数据。没有 Codec 时，历史数据仍可以作为 Raw JSON 查询。

## 扩展方式

- **历史载荷**：实现 AuditPayloadCodec，并注册为 Spring Bean 。模块在启动时收集全部 Codec，按 `payloadType()` 与 `version()` 解析历史数据。
- **关联资源**：实现 AuditResourceCollector，并注册为 Spring Bean 。收集器在调用结果确定后运行，返回的 Resource 与目标一起保存为关联。
- **目标或载荷的运行时解析**：在 `@Audit` 中声明 `targetResolver` 或 `payloadFactory`。解析器只从 AuditInvocation 读取运行时输入。