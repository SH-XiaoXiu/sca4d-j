package com.sca4d.app.server.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/** 验证调用和执行主体使用稳定、紧凑的对象式日志格式。 */
class LogContextConverterTest {

    private final LogContextConverter converter = new LogContextConverter();

    @Test
    @DisplayName("Formats Authenticated Actor As Identity Hierarchy")
    void formatsAuthenticatedActorAsIdentityHierarchy() {
        ILoggingEvent event = event(
                Map.of(
                        "callId",
                        "call-42",
                        "subjectId",
                        "11",
                        "accountId",
                        "22",
                        "principalId",
                        "33"));

        assertThat(converter.convert(event))
                .isEqualTo("Call[call-42] Actor[Subject#11 / Account#22 / Principal#33]");
    }

    @Test
    @DisplayName("Distinguishes Anonymous Calls From System Logs")
    void distinguishesAnonymousCallsFromSystemLogs() {
        assertThat(converter.convert(event(Map.of("callId", "call-42"))))
                .isEqualTo("Call[call-42] Actor[anonymous]");
        assertThat(converter.convert(event(Map.of())))
                .isEqualTo("Call[-] Actor[system]");
    }

    private static ILoggingEvent event(Map<String, String> context) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getMDCPropertyMap()).thenReturn(context);
        return event;
    }
}
