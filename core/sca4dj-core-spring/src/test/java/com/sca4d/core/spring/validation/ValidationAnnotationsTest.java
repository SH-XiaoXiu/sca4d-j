package com.sca4d.core.spring.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class ValidationAnnotationsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Validates Common Formats And Keeps Null Optional")
    void validatesCommonFormatsAndKeepsNullOptional() {
        Input valid = new Input();
        valid.bizId = "device-001";
        valid.displayName = "设备 001";
        valid.safeText = "普通说明";
        valid.macAddress = "A0:B1:C2:D3:E4:F5";
        assertThat(validator.validate(valid)).isEmpty();

        Input invalid = new Input();
        invalid.bizId = " invalid ";
        invalid.displayName = "名称\n";
        invalid.safeText = "说明\u0000";
        invalid.macAddress = "A0-B1-C2-D3-E4-F5";
        assertThat(validator.validate(invalid)).hasSize(4);

        assertThat(validator.validate(new Input())).isEmpty();
    }

    @Test
    void validatesEachTimeRangeAndReportsTheEndProperty() {
        RangeInput input = new RangeInput();
        input.startFrom = Instant.parse("2026-10-15T00:00:00Z");
        assertThat(validator.validate(input)).isEmpty();

        input.startTo = input.startFrom.plusSeconds(1);
        assertThat(validator.validate(input)).isEmpty();

        input.startTo = input.startFrom;
        input.otherStart = input.startFrom.plusSeconds(2);
        input.otherEnd = input.startFrom;
        assertThat(validator.validate(input))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("startTo", "otherEnd");

        input.startTo = input.startFrom.minusSeconds(1);
        assertThat(validator.validate(input)).hasSize(2);
    }

    @Test
    void rejectsAnUnknownTimeRangeProperty() {
        assertThatThrownBy(() -> validator.validate(new InvalidRangeInput()))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(exception -> assertThat(exception.getCause()).hasMessageContaining("@TimeRange"));
    }

    /** 保存两组相互独立的时间范围。 */
    @TimeRange(start = "startFrom", end = "startTo")
    @TimeRange(start = "otherStart", end = "otherEnd")
    private static class RangeInput {

        private Instant startFrom;

        private Instant startTo;

        private Instant otherStart;

        private Instant otherEnd;

        public Instant getStartFrom() {
            return startFrom;
        }

        public Instant getStartTo() {
            return startTo;
        }

        public Instant getOtherStart() {
            return otherStart;
        }

        public Instant getOtherEnd() {
            return otherEnd;
        }
    }

    /** 校验错误的属性声明应明确失败。 */
    @TimeRange(start = "missing", end = "startTo")
    private static class InvalidRangeInput {

        public Instant getStartTo() {
            return null;
        }
    }

    /** 保存公共格式校验测试输入。 */
    private static class Input {

        @BizId
        private String bizId;

        @com.sca4d.core.spring.validation.DisplayName
        private String displayName;

        @SafeText
        private String safeText;

        @MacAddress
        private String macAddress;
    }
}
