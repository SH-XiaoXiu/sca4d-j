package com.sca4d.schedule.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证 Schedule 审计内容的目标摘要和敏感字段过滤。 */
class ScheduleAuditPayloadTest {

    @Test
    void failureShouldKeepTypedPayloadWithoutInventingBusinessFacts() {
        ScheduleAuditPayload payload = ScheduleAuditPayload.failure();

        assertThat(payload).hasAllNullFieldsOrProperties();
        assertThat(new ScheduleAuditPayloadCodec().payloadType()).isEqualTo("platform.schedule");
        assertThat(new ScheduleAuditPayloadCodec().payloadClass()).isEqualTo(ScheduleAuditPayload.class);
    }
}
