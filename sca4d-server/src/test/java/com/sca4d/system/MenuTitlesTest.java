package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

/** 验证菜单多语言标题的转换、回退和不可变结果。 */
class MenuTitlesTest {

    @Test
    void shouldAllowOptionalLanguageToBeAddedWithoutChangingRequiredFields() {
        MenuTitles requiredOnly = new MenuTitles(Map.of("zh-CN", "菜单"));
        MenuTitles withEnglish = new MenuTitles(Map.of("zh-CN", "菜单", "en-US", "Menus"));

        assertThat(requiredOnly.resolve(Locale.US)).isEqualTo("菜单");
        assertThat(requiredOnly.asMap()).containsExactlyEntriesOf(Map.of("zh-CN", "菜单"));
        assertThat(withEnglish.resolve(Locale.US)).isEqualTo("Menus");
    }

    @Test
    void shouldRejectMissingRequiredOrUnsupportedLanguages() {
        assertThatThrownBy(() -> new MenuTitles(Map.of("en-US", "Menus")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zh-CN");
        assertThatThrownBy(() -> new MenuTitles(Map.of("zh-CN", "菜单", "fr-FR", "Menus")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }
}
