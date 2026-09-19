package com.sca4d.core.api.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TranslatedText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

class DictionaryOptionTest {

    @Test
    @DisplayName("代码字典项使用消息键且不携带数据库翻译")
    void createsCodeOptionWithMessageKey() {
        DictionaryOption option = new DictionaryOption(
                "ACTIVE",
                "dictionary.status.active",
                null,
                null,
                true,
                true,
                10);

        assertNull(option.label());
        assertNull(option.i18n());
        assertEquals("dictionary.status.active", assertInstanceOf(MessageText.class, option.labelText()).key());
    }

    @Test
    @DisplayName("数据库字典项使用多语言 label 并保留默认回退")
    void createsDatabaseOptionWithTranslatedLabel() {
        LocalizedFields i18n = LocalizedFields.of(
                Map.of(Locale.SIMPLIFIED_CHINESE, Map.of("label", "启用")));
        DictionaryOption option = new DictionaryOption(
                "ACTIVE",
                null,
                "Active",
                i18n,
                false,
                true,
                10);

        TranslatedText label = assertInstanceOf(TranslatedText.class, option.labelText());
        assertEquals("Active", label.fallback());
        assertEquals("启用", label.translations().get(Locale.SIMPLIFIED_CHINESE));
        assertEquals(i18n, option.i18n());
    }

    @Test
    @DisplayName("数据库字典项拒绝未知多语言字段")
    void rejectsUnknownI18nField() {
        LocalizedFields i18n = LocalizedFields.of(
                Map.of(Locale.SIMPLIFIED_CHINESE, Map.of("name", "启用")));

        assertThrows(
                IllegalArgumentException.class,
                () -> new DictionaryOption(
                        "ACTIVE",
                        null,
                        "Active",
                        i18n,
                        false,
                        true,
                        10));
    }
}
