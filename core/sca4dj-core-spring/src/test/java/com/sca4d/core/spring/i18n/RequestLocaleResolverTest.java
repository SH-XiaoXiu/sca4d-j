package com.sca4d.core.spring.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.i18n.LocaleCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Locale;

/** 验证 HTTP 请求 Locale 与静态消息支持列表保持独立。 */
class RequestLocaleResolverTest {

    private static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");

    private final RequestLocaleResolver resolver = new RequestLocaleResolver(
            new LocaleCatalog(List.of(ZH_CN, Locale.US), ZH_CN));

    @Test
    @DisplayName("Keeps Locale Outside Static Message Catalog")
    void keepsLocaleOutsideStaticMessageCatalog() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR, en-US;q=0.8");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.FRANCE);
    }

    @Test
    @DisplayName("Uses Application Default Without Accept Language")
    void usesApplicationDefaultWithoutAcceptLanguage() {
        assertThat(resolver.resolveLocale(new MockHttpServletRequest())).isEqualTo(ZH_CN);
    }
}
