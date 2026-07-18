package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.i18n.LocaleResponseInterceptor;
import com.sca4d.common.i18n.SupportedLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

/** 验证支持语言、Locale 解析和消息资源配置。 */
class I18nConfigTest {

    @AfterEach
    void clearLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void shouldResolveSupportedAcceptLanguageAndFallbackToChinese() {
        var resolver = new I18nConfig().localeResolver();
        MockHttpServletRequest englishRequest = new MockHttpServletRequest();
        englishRequest.addHeader("Accept-Language", "en-US,en;q=0.8");
        MockHttpServletRequest unsupportedRequest = new MockHttpServletRequest();
        unsupportedRequest.addHeader("Accept-Language", "fr-FR");

        assertThat(resolver.resolveLocale(englishRequest)).isEqualTo(Locale.US);
        assertThat(resolver.resolveLocale(unsupportedRequest)).isEqualTo(SupportedLocale.ZH_CN.locale());
    }

    @Test
    void shouldExposeTheResolvedLocaleAsContentLanguage() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = new LocaleResponseInterceptor()
                .preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(proceed).isTrue();
        assertThat(response.getHeader("Content-Language")).isEqualTo("en-US");
    }
}
