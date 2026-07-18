package com.sca4d.common.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证代码字典扫描、重复值检测和目录生成。 */
class CodeDictionaryScannerTest {

    private final CodeDictionaryScanner scanner = new CodeDictionaryScanner();

    @Test
    void shouldDiscoverExplicitProductionDictionaryEnums() {
        assertThat(scanner.scan(Set.of("com.sca4d.common.dictionary"))).contains(DictionaryTone.class);
    }

    @Test
    void shouldRejectExplicitDictionaryWithoutBaseEnumContract() {
        assertThatThrownBy(() -> scanner.scan(Set.of("dictionaryfixture.invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须实现 BaseEnum");
    }
}
