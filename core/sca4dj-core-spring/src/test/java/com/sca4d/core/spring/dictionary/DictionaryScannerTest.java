package com.sca4d.core.spring.dictionary;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.spring.dictionary.fixture.ScannedDictionary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证实现 `Dictionary` 的 enum 可以被 classpath 扫描并进入代码字典目录。 */
class DictionaryScannerTest {

    @Test
    @DisplayName("发现实现 Dictionary 的顶层 enum 并注册其选项")
    void discoversDictionaryEnum() {
        DictionaryScanner scanner = new DictionaryScanner(getClass().getClassLoader());

        List<Class<? extends Enum<?>>> types = scanner.scan(
                List.of(
                        "com.sca4d.core.spring.dictionary.fixture"));
        DictionaryRegistry registry = new DictionaryRegistry(types);

        assertThat(types).containsExactly(ScannedDictionary.class);
        assertThat(registry.keys()).containsExactly("fixture.scanned");
        assertThat(registry.options("fixture.scanned"))
                .extracting(option -> option.value())
                .containsExactly("FIRST", "SECOND");
    }
}
