package com.sca4d.audit.recording;

import com.sca4d.audit.payload.AuditPayloadData;
import com.sca4d.audit.payload.AuditPayloadJson;
import com.sca4d.audit.payload.EncodedAuditPayload;
import com.sca4d.audit.query.AuditEntry;
import com.sca4d.audit.query.AuditQuery;
import com.sca4d.audit.recording.mapper.AuditMapper;
import com.sca4d.audit.recording.mapper.EncodedAuditEntry;
import com.sca4d.core.api.audit.AuditActor;
import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.resource.ResourceReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 向 `audit_record` 追加审计行，并按查询条件读取审计行。
 */
@ApiStatus.Internal
public class AuditRepository {
    private static final int PERSISTED_TEXT_MAX_LENGTH = 128;

    private final @NotNull AuditMapper mapper;

    private final @NotNull IdGenerator ids;

    private final @NotNull AuditPayloadJson payloadJson;

    /**
     * 创建负责审计领域对象和数据库列值转换的 Repository。
     */
    public AuditRepository(
            @NotNull AuditMapper mapper,
            @NotNull IdGenerator ids,
            @NotNull AuditPayloadJson payloadJson) {
        this.mapper = Objects.requireNonNull(mapper, "审计 Mapper 不能为空");
        this.ids = Objects.requireNonNull(ids, "审计标识生成器不能为空");
        this.payloadJson = Objects.requireNonNull(payloadJson, "审计 payload 处理器不能为空");
    }

    /**
     * 追加一条后续不再更新的审计行。
     */
    @AffectedRows(target = "新增审计记录")
    public void append(@NotNull AuditRecord record) {
        mapper.insert(encode(Objects.requireNonNull(record, "审计记录不能为空")));
    }

    /**
     * 按标识读取审计行。
     */
    @NotNull
    public Optional<AuditEntry> find(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::decode);
    }

    /**
     * @return 满足查询条件的一页审计记录和完整命中数量
     */
    public @NotNull Page<AuditEntry> page(@NotNull AuditQuery query) {
        AuditQuery checked = Objects.requireNonNull(query, "审计查询不能为空");
        long total = mapper.count(checked);
        List<AuditEntry> items = total == 0
                ? List.of()
                : mapper.selectPage(checked).stream().map(this::decode).toList();
        return new Page<>(items, checked.page(), total);
    }

    /**
     * 将待记录审计事实编码成 Mapper 使用的列值。
     */
    private @NotNull EncodedAuditEntry encode(@NotNull AuditRecord record) {
        EncodedAuditPayload payload = payloadJson.encode(record.payload().orElse(null));
        Resource target = record.target().orElse(null);
        HttpCall httpCall = record.httpCall().orElse(null);
        return new EncodedAuditEntry(
                ids.nextId().value(),
                required(record.requestId(), "调用标识"),
                record.actor().subjectId(),
                record.actor().accountId().orElse(null),
                record.actor().principalId(),
                required(record.operation().id(), "审计操作"),
                target == null ? null : required(target.type().value(), "目标资源类型"),
                target == null ? null : required(target.id(), "目标资源标识"),
                record.result(),
                payload == null ? null : payload.type(),
                payload == null ? null : payload.version(),
                payload == null ? null : payload.json(),
                httpCall == null ? null : httpCall.method(),
                httpCall == null ? null : httpCall.path(),
                httpCall == null ? null : httpCall.clientIp(),
                httpCall == null ? null : httpCall.userAgent().orElse(null),
                record.occurredAt());
    }

    /**
     * 将 Mapper 返回的列值恢复成查询服务使用的审计领域对象。
     */
    private @NotNull AuditEntry decode(@NotNull EncodedAuditEntry row) {
        String targetType = row.targetType();
        String payloadType = row.payloadType();
        String httpMethod = row.httpMethod();
        Resource target = targetType == null
                ? null
                : new ResourceReference(targetType, Objects.requireNonNull(row.targetId()));
        AuditPayloadData payload = payloadType == null
                ? null
                : payloadJson.decode(
                        payloadType,
                        Objects.requireNonNull(row.payloadVersion()),
                        Objects.requireNonNull(row.payloadJson()));
        HttpCall httpCall = httpMethod == null
                ? null
                : new HttpCall(
                        httpMethod,
                        Objects.requireNonNull(row.httpPath()),
                        Objects.requireNonNull(row.clientIp()),
                        row.userAgent());
        return new AuditEntry(
                row.id(),
                row.requestId(),
                new AuditActor(row.subjectId(), row.accountId(), row.principalId()),
                new AuditOperation(row.operation()),
                target,
                row.result(),
                payload,
                httpCall,
                row.occurredAt());
    }

    /**
     * 校验数据库短文本字段的长度和空白限制。
     */
    private static @NotNull String required(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.isBlank() || checked.length() > PERSISTED_TEXT_MAX_LENGTH) {
            throw new IllegalArgumentException(name + "长度必须在 1 到 " + PERSISTED_TEXT_MAX_LENGTH + " 之间");
        }
        return checked;
    }
}
