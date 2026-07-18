package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.i18n.SupportedLocale;
import org.junit.jupiter.api.Test;

/** 验证支持语言接口返回固定语言目录。 */
class SystemLanguageControllerTest {

    @Test
    void shouldExposeTheBackendLanguageCatalogWithoutDuplicatedDefaults() {
        var response = new SystemLanguageController().languages();

        assertThat(response.getData()).hasSize(SupportedLocale.values().length);
        assertThat(response.getData())
                .extracting(item -> item.getCode() + ":" + item.getNativeName() + ":"
                        + item.getDefaultLanguage() + ":" + item.getRequired())
                .containsExactly(
                        "zh-CN:简体中文:true:true",
                        "en-US:English:false:false");
    }
}
