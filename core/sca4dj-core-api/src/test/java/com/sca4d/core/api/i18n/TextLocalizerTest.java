package com.sca4d.core.api.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

class TextLocalizerTest {

    private static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");

    private static final Locale EN_US = Locale.forLanguageTag("en-US");

    private final LocaleCatalog locales = new LocaleCatalog(List.of(ZH_CN, EN_US), ZH_CN);

    @Test
    @DisplayName("Resolves Exact Same Language And Default Locales")
    void resolvesExactSameLanguageAndDefaultLocales() {
        assertEquals(EN_US, locales.resolve(Locale.forLanguageTag("en-US")));
        assertEquals(EN_US, locales.resolve(Locale.forLanguageTag("en-GB")));
        assertEquals(ZH_CN, locales.resolve(Locale.FRENCH));
    }

    @Test
    @DisplayName("Static Message Falls Back To Default Then Key")
    void staticMessageFallsBackToDefaultThenKey() {
        Map<String, String> messages = Map.of("zh-CN:greeting", "你好，{0}");
        TextLocalizer localizer = new TextLocalizer(
                locales,
                (key, locale, arguments) -> Optional.ofNullable(
                        messages.get(locale.toLanguageTag() + ":" + key))
                        .map(pattern -> new MessageFormat(pattern, locale).format(arguments)));

        assertEquals("你好，Sca4d", localizer.localize(MessageText.of("greeting", "Sca4d"), EN_US));
        assertEquals("missing", localizer.localize(MessageText.of("missing"), EN_US));
    }

    @Test
    @DisplayName("Dynamic Text Falls Back To Default Then Business Text")
    void dynamicTextFallsBackToDefaultThenBusinessText() {
        TextLocalizer localizer = new TextLocalizer(locales, (key, locale, arguments) -> Optional.empty());

        assertEquals(
                "默认名称",
                localizer.localize(TranslatedText.of(Map.of(ZH_CN, "默认名称"), "业务回退"), EN_US));
        assertEquals(
                "业务回退",
                localizer.localize(TranslatedText.of(Map.of(), "业务回退"), EN_US));
    }

    @Test
    @DisplayName("Dynamic Fields Fall Back To Default And Require A Value")
    void dynamicFieldsFallBackToDefaultAndRequireAValue() {
        TextLocalizer localizer = new TextLocalizer(locales, (key, locale, arguments) -> Optional.empty());
        LocalizedFields fields = LocalizedFields.of(
                Map.of(
                        ZH_CN,
                        Map.of("name", "默认名称"),
                        Locale.FRANCE,
                        Map.of("name", "Nom")));

        assertEquals("Nom", localizer.localize(fields, "name", Locale.FRANCE));
        assertEquals("默认名称", localizer.localize(fields, "name", EN_US));
        assertThrows(IllegalArgumentException.class, () -> localizer.localize(fields, "description", EN_US));
    }
}
