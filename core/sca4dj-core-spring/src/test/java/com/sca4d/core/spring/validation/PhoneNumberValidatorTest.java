package com.sca4d.core.spring.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.region.Continent;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.PhoneNumberCondition;
import com.sca4d.core.api.region.RegionCatalog;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

class PhoneNumberValidatorTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final Validator validator = VALIDATOR_FACTORY.getValidator();

    /** 关闭当前测试类共用的 Bean Validation 工厂。 */
    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    @DisplayName("Accepts Only Canonical Valid E164 And Keeps Blank Optional")
    void acceptsOnlyCanonicalValidE164AndKeepsBlankOptional() {
        assertThat(validator.validate(new AnyPhone("+8613800138000"))).isEmpty();
        assertThat(validator.validate(new AnyPhone("+86 138 0013 8000"))).hasSize(1);
        assertThat(validator.validate(new AnyPhone("13800138000"))).hasSize(1);
        assertThat(validator.validate(new AnyPhone("+8610000000000"))).hasSize(1);
        assertThat(validator.validate(new AnyPhone(" "))).isEmpty();
        assertThat(validator.validate(new AnyPhone(null))).isEmpty();
    }

    @Test
    @DisplayName("Combines Rule Fields With And And Rules With Or")
    void combinesRuleFieldsWithAndAndRulesWithOr() {
        assertThat(validator.validate(new RegionalPhone("+8613800138000"))).isEmpty();
        assertThat(validator.validate(new RegionalPhone("+14155552671"))).isEmpty();
        assertThat(validator.validate(new RegionalPhone("+442079460018"))).hasSize(1);
    }

    @Test
    @DisplayName("Applies Exclusions After Includes")
    void appliesExclusionsAfterIncludes() {
        assertThat(validator.validate(new NonChineseAsianPhone("+819012345678"))).isEmpty();
        assertThat(validator.validate(new NonChineseAsianPhone("+8613800138000"))).hasSize(1);
    }

    @Test
    @DisplayName("Resolves Explicit Custom Condition Bean When Constraint Initializes")
    void resolvesExplicitCustomConditionBeanWhenConstraintInitializes() throws ReflectiveOperationException {
        PhoneNumberValidator phoneValidator = new PhoneNumberValidator(
                RegionCatalog.standard(),
                new PhoneNumberConditionRegistry(List.of(new ChineseNumber())));
        phoneValidator.initialize(customPhoneAnnotation());

        assertThat(phoneValidator.isValid("+8613800138000", null)).isTrue();
        assertThat(phoneValidator.isValid("+14155552671", null)).isFalse();

        PhoneNumberValidator missingBeanValidator = new PhoneNumberValidator(
                RegionCatalog.standard(),
                new PhoneNumberConditionRegistry(List.of()));
        PhoneNumber custom = customPhoneAnnotation();
        assertThatThrownBy(() -> missingBeanValidator.initialize(custom))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("count=0");
    }

    /** 读取测试字段上的 PhoneNumber 约束。 */
    private static PhoneNumber customPhoneAnnotation() throws NoSuchFieldException {
        Field field = CustomPhone.class.getDeclaredField("value");
        return field.getAnnotation(PhoneNumber.class);
    }

    /** 保存不限制地区的电话号码。 */
    private static final class AnyPhone {

        @PhoneNumber
        private final String value;

        private AnyPhone(String value) {
            this.value = value;
        }
    }

    /** 保存中国或美国电话号码。 */
    private static final class RegionalPhone {

        @PhoneNumber(anyOf = {
                @PhoneNumber.Rule(regions = "CN", continents = Continent.ASIA),
                @PhoneNumber.Rule(regions = "US", countryCallingCodes = 1)
        })
        private final String value;

        private RegionalPhone(String value) {
            this.value = value;
        }
    }

    /** 保存亚洲地区中排除中国的电话号码。 */
    private static final class NonChineseAsianPhone {

        @PhoneNumber(anyOf = @PhoneNumber.Rule(continents = Continent.ASIA, excludeRegions = "CN"))
        private final String value;

        private NonChineseAsianPhone(String value) {
            this.value = value;
        }
    }

    /** 保存需要自定义条件判断的电话号码。 */
    private static final class CustomPhone {

        @PhoneNumber(anyOf = @PhoneNumber.Rule(conditions = ChineseNumber.class))
        private String value;
    }

    /** 只接受中国国际电话代码的测试条件。 */
    private static final class ChineseNumber implements PhoneNumberCondition {

        @Override
        public boolean test(@NotNull ParsedPhoneNumber phoneNumber) {
            return phoneNumber.countryCallingCode() == 86;
        }
    }
}
