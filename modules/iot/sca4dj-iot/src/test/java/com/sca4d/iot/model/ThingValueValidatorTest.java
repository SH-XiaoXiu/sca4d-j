package com.sca4d.iot.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.iot.api.model.ThingValueType;
import org.junit.jupiter.api.Test;

/** 验证物模型值校验器执行字符串长度和完整匹配约束。 */
class ThingValueValidatorTest {

    private final ThingValueValidator validator = new ThingValueValidator();

    @Test
    void validatesStringPattern() {
        ThingValueType netId = ThingValueType.stringType()
                .maximumLength(4)
                .pattern("[0-9a-f]{4}")
                .build();

        assertThat(validator.validate(netId, "12af")).isEqualTo("12af");
        assertThatThrownBy(() -> validator.validate(netId, "12AF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pattern");
        assertThatThrownBy(() -> validator.validate(netId, "12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumLength");
    }
}
