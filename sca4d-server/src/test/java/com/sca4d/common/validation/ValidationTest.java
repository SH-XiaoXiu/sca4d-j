package com.sca4d.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Payload;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/** 验证名称、普通文本和请求字段的 Bean Validation 规则。 */
class ValidationTest {

    @Test
    void requiredShouldRejectNullFieldsAndContainerElements() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new RequiredValue(null, List.of(1L))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("value");
        assertThat(validator.validate(new RequiredValue("value", Arrays.asList(1L, null))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("ids[1].<list element>");
        assertThat(validator.validate(new RequiredValue("value", List.of(1L))))
                .isEmpty();
    }

    @Test
    void displayNameShouldRejectControlCharactersAndLongValue() {
        DisplayNameValidator validator = new DisplayNameValidator();
        validator.initialize(newDisplayName(4));

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(" ", null)).isTrue();
        assertThat(validator.isValid("名称", null)).isTrue();
        assertThat(validator.isValid("名称\n", null)).isFalse();
        assertThat(validator.isValid("12345", null)).isFalse();
    }

    @Test
    void safeTextShouldRejectControlCharactersAndLongValue() {
        SafeTextValidator validator = new SafeTextValidator();
        validator.initialize(newSafeText(4));

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("备注", null)).isTrue();
        assertThat(validator.isValid("备注\t", null)).isFalse();
        assertThat(validator.isValid("12345", null)).isFalse();
    }

    @Test
    void phoneNumberShouldValidateFormat() {
        PhoneNumberValidator validator = new PhoneNumberValidator();

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("13800138000", null)).isTrue();
        assertThat(validator.isValid("12800138000", null)).isFalse();
    }

    @Test
    void macAddressShouldValidateColonSeparatedFormat() {
        MacAddressValidator validator = new MacAddressValidator();

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("AA:BB:CC:DD:EE:FF", null)).isTrue();
        assertThat(validator.isValid("AA-BB-CC-DD-EE-FF", null)).isFalse();
    }

    /** @return 最大长度为 max 的运行时 DisplayName 注解实例 */
    private DisplayName newDisplayName(int max) {
        return new DisplayName() {
            @Override
            public String message() {
                return "";
            }

            @Override
            public int max() {
                return max;
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
            public Class<? extends Annotation> annotationType() {
                return DisplayName.class;
            }
        };
    }

    /** @return 最大长度为 max 的运行时 SafeText 注解实例 */
    private SafeText newSafeText(int max) {
        return new SafeText() {
            @Override
            public String message() {
                return "";
            }

            @Override
            public int max() {
                return max;
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
            public Class<? extends Annotation> annotationType() {
                return SafeText.class;
            }
        };
    }

    /** 同时验证普通字段和容器元素约束的测试输入。 */
    private static final class RequiredValue {

        @Required
        private final String value;

        private final List<@Required Long> ids;

        private RequiredValue(String value, List<Long> ids) {
            this.value = value;
            this.ids = ids;
        }
    }
}
