package com.sca4d.audit.recording;

import com.sca4d.audit.recording.mapper.EncodedAuditEntry;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 请求消费者写入已经编码、脱敏并冻结的审计事实，消息 ID 作为数据库主键。 */
public class AuditMessage extends Message {
    /** 审计可靠交接使用的消息编码。 */
    public static final TreeCode CODE = TreeCode.of("AUDIT/RECORD");

    private final FrozenAuditEntry frozen;

    AuditMessage(FrozenAuditEntry frozen) {
        this.frozen = Objects.requireNonNull(frozen);
    }

    List<Resource.Ref> resources() {
        return frozen.resources();
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int protocolVersion() {
        return 2;
    }

    /** 返回模块内部使用的冻结字段，不向业务调用方暴露 Mapper 数据。 */
    @NotNull
    EncodedAuditEntry entry() {
        return frozen.entry();
    }
}
