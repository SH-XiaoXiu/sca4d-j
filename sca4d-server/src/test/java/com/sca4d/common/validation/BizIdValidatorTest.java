package com.sca4d.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Payload;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;

/** 验证业务 ID 的格式、长度和首尾空格拒绝规则。 */
class BizIdValidatorTest {

    @Test
    void codeModeShouldAcceptStableBusinessCode() {
        BizIdValidator validator = validator(BizIdMode.CODE, 1, 64, "");

        assertThat(validator.isValid("AC-001_2026", null)).isTrue();
        assertThat(validator.isValid("A", null)).isTrue();
        assertThat(validator.isValid("<script>", null)).isFalse();
        assertThat(validator.isValid(" AC-001", null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void namespacedCodeModeShouldAcceptStableNamespacedBusinessCode() {
        BizIdValidator validator = validator(BizIdMode.NAMESPACED_CODE, 1, 64, "");

        assertThat(validator.isValid("system.dictionary-status", null)).isTrue();
        assertThat(validator.isValid("System.DictionaryStatus", null)).isFalse();
        assertThat(validator.isValid("system..status", null)).isFalse();
    }

    @Test
    void numModeShouldAcceptPositiveIntegerLikeValue() {
        BizIdValidator validator = validator(BizIdMode.NUM, 1, 20, "");

        assertThat(validator.isValid(1L, null)).isTrue();
        assertThat(validator.isValid("9223372036854775807", null)).isTrue();
        assertThat(validator.isValid(0L, null)).isFalse();
        assertThat(validator.isValid("-1", null)).isFalse();
        assertThat(validator.isValid("0", null)).isFalse();
        assertThat(validator.isValid(1.0D, null)).isFalse();
        assertThat(validator.isValid(new BigDecimal("1"), null)).isFalse();
    }

    @Test
    void customModeShouldRequireExplicitPattern() {
        assertThatThrownBy(() -> validator(BizIdMode.CUSTOM, 1, 64, "")).isInstanceOf(IllegalArgumentException.class);

        BizIdValidator validator = validator(BizIdMode.CUSTOM, 2, 8, "^SN-[0-9]+$");

        assertThat(validator.isValid("SN-12", null)).isTrue();
        assertThat(validator.isValid("AC-12", null)).isFalse();
    }

    /** @return 使用指定模式、长度和正则初始化的 BizIdValidator */
    private BizIdValidator validator(BizIdMode mode, int min, int max, String regexp) {
        BizIdValidator validator = new BizIdValidator();
        validator.initialize(new BizId() {
            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public BizIdMode mode() {
                return mode;
            }

            @Override
            public int min() {
                return min;
            }

            @Override
            public int max() {
                return max;
            }

            @Override
            public String regexp() {
                return regexp;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return BizId.class;
            }
        });
        return validator;
    }
}
