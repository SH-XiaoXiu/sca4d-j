package com.sca4d.core.spring.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    /** 保存公共格式校验测试输入。 */
    private static final class Input {

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
