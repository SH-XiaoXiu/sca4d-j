package com.sca4d.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证语言标签转换和不支持语言的拒绝结果。 */
class SupportedLanguageValidatorTest {

    private final SupportedLanguageValidator validator = new SupportedLanguageValidator();

    @Test
    void shouldUseSupportedLocaleAsTheOnlyLanguageCatalog() {
        assertThat(SupportedLocale.locales()).containsExactly(SupportedLocale.ZH_CN.locale(), SupportedLocale.EN_US.locale());
        assertThat(SupportedLocale.defaultLocale()).isEqualTo(SupportedLocale.ZH_CN);
        assertThat(SupportedLocale.values()).filteredOn(SupportedLocale::isDefaultLanguage)
                .containsExactly(SupportedLocale.ZH_CN);
        assertThat(SupportedLocale.values()).filteredOn(SupportedLocale::isRequired)
                .containsExactly(SupportedLocale.ZH_CN);
        assertThat(validator.isValid("zh-CN", null)).isTrue();
        assertThat(validator.isValid("en-US", null)).isTrue();
        assertThat(validator.isValid("fr-FR", null)).isFalse();
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
