package com.sca4d.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证 BaseEnum 固定字符串值和默认大写规则。 */
class BaseEnumTest {

    @Test
    void normalizeToUppercase() {
        assertThat(BaseEnum.normalize(" active ")).isEqualTo("ACTIVE");
    }

    @Test
    void readValueIgnoringCase() {
        assertThat(BaseEnum.fromValue(TestStatus.class, "active")).isEqualTo(TestStatus.ACTIVE);
    }

    /** 使用转换 name 和中文说明验证 BaseEnum 默认写法的枚举。 */
    private enum TestStatus implements BaseEnum {
        /** 唯一可读取的测试状态。 */
        ACTIVE("启用");

        private final String value;
        private final String description;

        TestStatus(String description) {
            this.value = BaseEnum.normalize(name());
            this.description = description;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String description() {
            return description;
        }
    }
}
