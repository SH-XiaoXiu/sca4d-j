package com.sca4d.app.server.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证应用日志来源只使用 logger 名，不读取调用栈。 */
class LogSourceConverterTest {

    private final LogSourceConverter converter = new LogSourceConverter();

    @Test
    @DisplayName("Formats Project Logger Without Caller Data")
    void formatsProjectLoggerWithoutCallerData() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn("com.sca4d.iam.service.PlatformIdentityService");

        assertThat(converter.convert(event)).isEqualTo("module=iam class=PlatformIdentityService");
        verify(event).getLoggerName();
        verifyNoMoreInteractions(event);
    }

    @Test
    @DisplayName("Preserves Third Party Logger Name")
    void preservesThirdPartyLoggerName() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn("org.springframework.web.DispatcherServlet");

        assertThat(converter.convert(event)).isEqualTo("logger=org.springframework.web.DispatcherServlet");
    }
}
