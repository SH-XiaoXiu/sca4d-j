package com.sca4d.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

/** 验证应用日志来源的模块、类名和第三方 logger 输出。 */
class LogSourceConverterTest {

    private final LogSourceConverter converter = new LogSourceConverter();

    @Test
    void shouldExtractDomainModuleAndClassWithoutCallerData() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn("com.sca4d.iam.service.DefaultTenantCleanupBatchService");

        assertThat(converter.convert(event))
                .isEqualTo("module=iam class=DefaultTenantCleanupBatchService");
        verify(event).getLoggerName();
        verifyNoMoreInteractions(event);
    }

    @Test
    void shouldKeepCommonCapabilityAsModule() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName())
                .thenReturn("com.sca4d.common.messaging.outbox.DefaultMessageOutboxDeliveryService");

        assertThat(converter.convert(event))
                .isEqualTo("module=common.messaging class=DefaultMessageOutboxDeliveryService");
    }

    @Test
    void shouldUseTechnicalPackageAsModuleForFrameworkBoundary() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn("com.sca4d.config.PermissionConfig");

        assertThat(converter.convert(event)).isEqualTo("module=config class=PermissionConfig");
    }

    @Test
    void shouldPreserveExternalLoggerName() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn("org.springframework.web.DispatcherServlet");

        assertThat(converter.convert(event))
                .isEqualTo("logger=org.springframework.web.DispatcherServlet");
    }

    @Test
    void shouldHandleRootProjectClassAndMissingLoggerName() {
        ILoggingEvent moduleEvent = mock(ILoggingEvent.class);
        when(moduleEvent.getLoggerName()).thenReturn("com.sca4d.Sca4dApplication");
        ILoggingEvent missingEvent = mock(ILoggingEvent.class);
        when(missingEvent.getLoggerName()).thenReturn(null);

        assertThat(converter.convert(moduleEvent))
                .isEqualTo("module=server class=Sca4dApplication");
        assertThat(converter.convert(missingEvent)).isEqualTo("logger=unknown");
    }
}
