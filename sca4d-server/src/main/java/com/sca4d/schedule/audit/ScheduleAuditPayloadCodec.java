package com.sca4d.schedule.audit;

import com.sca4d.common.audit.AuditPayloadCodec;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** Schedule 写操作审计载荷的长期类型和版本契约。 */
@Component
public class ScheduleAuditPayloadCodec implements AuditPayloadCodec<ScheduleAuditPayload> {

    /** Schedule 写操作保存到审计 JSON 中的内容类型。 */
    public static final String PAYLOAD_TYPE = "platform.schedule";

    @Override
    public @NotNull String payloadType() {
        return PAYLOAD_TYPE;
    }

    @Override
    public @NotNull Class<ScheduleAuditPayload> payloadClass() {
        return ScheduleAuditPayload.class;
    }
}
