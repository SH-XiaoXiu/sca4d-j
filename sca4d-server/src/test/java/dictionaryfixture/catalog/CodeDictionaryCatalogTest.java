package dictionaryfixture.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.dictionary.CodeDictionaryCatalog;
import com.sca4d.common.dictionary.CodeDictionaryItem;
import com.sca4d.common.dictionary.CodeDictionaryMode;
import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.enums.BaseEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证测试字典目录可以被扫描并生成固定条目。 */
class CodeDictionaryCatalogTest {

    @Test
    void shouldBuildDeclarationOrderAndExplicitPresentationMetadata() {
        CodeDictionaryCatalog catalog = new CodeDictionaryCatalog(List.of(ValidDictionary.class));

        var definition = catalog.definitions().getFirst();
        assertThat(definition.getCode()).isEqualTo("fixture.valid");
        assertThat(definition.getMode()).isEqualTo(CodeDictionaryMode.EXTENSIBLE);
        assertThat(definition.getSource()).isEqualTo(DictionarySource.CODE);
        assertThat(definition.getOptions())
                .extracting(option -> option.getValue() + ":" + option.getLabelKey() + ":" + option.getSortOrder()
                        + ":" + option.isPublished())
                .containsExactly(
                        "FIRST:dictionary.fixture.valid.first:10:true",
                        "SECOND:fixture.valid.second:20:true",
                        "INTERNAL:null:30:false");
        assertThat(definition.getOptions().get(1).getTone()).isEqualTo(DictionaryTone.WARNING);
        assertThat(definition.getOptions().get(1).isSelectable()).isFalse();
        assertThat(definition.getOptions().get(1).getSourceRef())
                .endsWith("CodeDictionaryCatalogTest$ValidDictionary#SECOND");
    }

    @Test
    void shouldRejectDuplicateDictionaryCodes() {
        assertThatThrownBy(() -> new CodeDictionaryCatalog(List.of(ValidDictionary.class, DuplicateCodeDictionary.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复代码字典类型编码");
    }

    @Test
    void shouldRejectCaseInsensitiveDuplicateValues() {
        assertThatThrownBy(() -> new CodeDictionaryCatalog(List.of(DuplicateValueDictionary.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("代码字典枚举值重复");
    }

    /** 包含可选、不可选和未发布条目的合法可扩展字典。 */
    @CodeDictionary(code = "fixture.valid", nameKey = "fixture.valid", mode = CodeDictionaryMode.EXTENSIBLE)
    private enum ValidDictionary implements BaseEnum {
        /** 默认可选择且发布到数据库的条目。 */
        FIRST("FIRST"),
        /** 不可选择并使用警告色的已发布条目。 */
        @CodeDictionaryItem(labelKey = "fixture.valid.second", tone = DictionaryTone.WARNING, selectable = false)
        SECOND("SECOND"),
        /** 只在代码中使用、不发布到数据库的条目。 */
        @CodeDictionaryItem(published = false)
        INTERNAL("INTERNAL");

        private final String value;

        ValidDictionary(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String description() {
            return value;
        }
    }

    /** 与 ValidDictionary 使用同一字典编码的无效类型。 */
    @CodeDictionary(code = "fixture.valid", nameKey = "fixture.duplicate-code")
    private enum DuplicateCodeDictionary implements BaseEnum {
        /** 触发重复字典编码检查的唯一条目。 */
        ONLY;

        @Override
        public String value() {
            return name();
        }

        @Override
        public String description() {
            return name();
        }
    }

    /** 包含忽略大小写后重复值的无效字典。 */
    @CodeDictionary(code = "fixture.duplicate-value", nameKey = "fixture.duplicate-value")
    private enum DuplicateValueDictionary implements BaseEnum {
        /** 使用小写 same 的条目。 */
        LOWER("same"),
        /** 使用大写 SAME 并与 LOWER 冲突的条目。 */
        UPPER("SAME");

        private final String value;

        DuplicateValueDictionary(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String description() {
            return value;
        }
    }
}
