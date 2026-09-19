package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.enums.BaseEnum;
import com.sca4d.core.api.enums.EnumValueCodec;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeCatalog;
import com.sca4d.core.api.error.ErrorCodeRange;
import com.sca4d.core.api.logging.LogSourceFormatter;
import com.sca4d.core.api.runtime.Environment;
import com.sca4d.core.api.runtime.TimeFormats;
import com.sca4d.core.api.runtime.TimeUtils;
import com.sca4d.core.api.validation.Validation;
import com.sca4d.core.api.validation.ValidationResult;
import com.sca4d.core.api.validation.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

class CoreUtilitiesTest {

    @Test
    @DisplayName("Catalogs Multiple Error Code Enums And Validates Ranges")
    void catalogsMultipleErrorCodeEnumsAndValidatesRanges() {
        ErrorCodeCatalog catalog = new ErrorCodeCatalog(List.of(CoreErrorCode.class, ExampleErrorCode.class));

        assertSame(CoreErrorCode.PERMISSION_DENIED, catalog.loadByCode("1002"));
        assertSame(ExampleErrorCode.DEVICE_OFFLINE, catalog.loadByCode("2000"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ErrorCodeCatalog(List.of(CoreErrorCode.class, OverlappingErrorCode.class)));
    }

    @Test
    @DisplayName("Applies Stable Environment Time And Log Formatting Rules")
    void appliesStableEnvironmentTimeAndLogFormattingRules() {
        Instant instant = Instant.parse("2026-07-20T00:00:00Z");

        assertSame(Environment.PROD, Environment.parse("prod"));
        assertTrue(Environment.PROD.isProduction());
        assertEquals(
                "2026-07-20 08:00:00",
                TimeFormats.formatInstant(instant, ZoneId.of("Asia/Shanghai")));
        assertEquals(
                instant,
                TimeUtils.startOfDay(LocalDate.of(2026, 7, 20), ZoneOffset.UTC));
        assertEquals(
                "module=core.api class=FlowEngine",
                LogSourceFormatter.format("com.sca4d.core.api.flow.FlowEngine"));
        assertEquals(
                "module=app class=SampleApplication",
                LogSourceFormatter.format("com.example.sample", "com.example.sample.app.SampleApplication"));
    }

    @Test
    @DisplayName("Provides Deterministic Enum Codec And Validation Results")
    void providesDeterministicEnumCodecAndValidationResults() {
        assertEquals("ENABLED", EnumValueCodec.encode(State.ENABLED));
        assertSame(State.ENABLED, EnumValueCodec.decode(State.class, "enabled"));

        ValidationResult result = Validation.validate(
                "",
                List.of(
                        value -> value.isBlank()
                                ? new ValidationResult(List.of(new Violation("name", "validation.required")))
                                : ValidationResult.valid(),
                        value -> ValidationResult.valid()));
        assertFalse(result.isValid());
        assertEquals(1, result.violations().size());
    }

    private enum State implements BaseEnum {
        ENABLED;

        @Override
        public String value() {
            return name();
        }

        @Override
        public String description() {
            return "启用";
        }
    }

    @ErrorCodeRange(start = 2000, end = 2099)
    private enum ExampleErrorCode implements ErrorCode {
        DEVICE_OFFLINE;

        @Override
        public String code() {
            return "2000";
        }

        @Override
        public ErrorCategory category() {
            return ErrorCategory.CONFLICT;
        }

        @Override
        public String messageKey() {
            return "error.device.offline";
        }
    }

    @ErrorCodeRange(start = 1090, end = 1199)
    private enum OverlappingErrorCode implements ErrorCode {
        VALUE;

        @Override
        public String code() {
            return "1090";
        }

        @Override
        public ErrorCategory category() {
            return ErrorCategory.INTERNAL;
        }

        @Override
        public String messageKey() {
            return "error.overlap";
        }
    }
}
