package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证 AuthenticationProcess 浏览器 Cookie 的安全属性和摘要读取。 */
class AuthenticationBrowserTest {

    @Test
    void shouldIssueHttpOnlySameSiteCookieAndReadSameBrowserHash() {
        Sca4djAuthenticationProperties properties = new Sca4djAuthenticationProperties();
        AuthenticationBrowser browser = new AuthenticationBrowser(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String createdHash = browser.bind(request, response);

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains(properties.getBrowserCookie() + "=")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        String token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        MockHttpServletRequest subsequent = new MockHttpServletRequest();
        subsequent.setCookies(new Cookie(properties.getBrowserCookie(), token));
        assertThat(browser.require(subsequent)).isEqualTo(createdHash);
    }
}
