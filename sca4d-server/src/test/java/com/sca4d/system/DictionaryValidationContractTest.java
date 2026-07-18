package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.validation.ValidationPatterns;
import org.junit.jupiter.api.Test;

/** 验证字典类型和值的格式、长度和多语言标签校验。 */
class DictionaryValidationContractTest {

    @Test
    void shouldUseSharedBusinessAndNamespacedCodeRules() {
        assertThat(ValidationPatterns.isBizCode("ACTIVE_2026")).isTrue();
        assertThat(ValidationPatterns.isBizCode("system.dictionary-status")).isFalse();
        assertThat(ValidationPatterns.isNamespacedCode("system.dictionary-status")).isTrue();
        assertThat(ValidationPatterns.isNamespacedCode("System.DictionaryStatus")).isFalse();
    }
}
