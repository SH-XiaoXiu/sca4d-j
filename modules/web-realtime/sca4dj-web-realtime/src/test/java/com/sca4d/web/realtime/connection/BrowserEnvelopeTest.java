package com.sca4d.web.realtime.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证 SSE 与 STOMP 共用的浏览器信封不会把 64 位整数输出为易失真的 JSON 数字。 */
class BrowserEnvelopeTest {

    private static final long SNOWFLAKE_ID = 2117884817562849280L;

    @Test
    @DisplayName("Encodes Nested Long Values As Strings")
    void encodesNestedLongValuesAsStrings() {
        var payload = JsonNodeFactory.instance.objectNode()
                .put("updateId", SNOWFLAKE_ID)
                .put("count", 3)
                .put("enabled", true);
        payload.putArray("ids").add(SNOWFLAKE_ID);

        BrowserEnvelope envelope = new BrowserEnvelope(
                TreeCode.of("firmware.update.changed"),
                1,
                Instant.parse("2026-11-04T00:00:00Z"),
                payload);

        assertThat(envelope.getPayload().required("updateId").textValue())
                .isEqualTo("2117884817562849280");
        assertThat(envelope.getPayload().required("ids").get(0).isTextual()).isTrue();
        assertThat(envelope.getPayload().required("count").isInt()).isTrue();
        assertThat(envelope.getPayload().required("enabled").isBoolean()).isTrue();
    }
}
