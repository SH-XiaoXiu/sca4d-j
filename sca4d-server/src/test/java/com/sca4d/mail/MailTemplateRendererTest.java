package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Map;

/** 固化邮件模板变量边界、HTML escaping 和最终清洗规则。 */
class MailTemplateRendererTest {

    private final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Test
    void shouldRenderPlainTextAndSanitizedHtml() {
        RenderedMail mail = renderer.render(
                "{{productName}} 验证码",
                "你好，{{displayName}}。验证码是 {{code}} & 请勿转发。",
                "<h1>{{productName}}</h1><p>你好，{{displayName}}</p>"
                        + "<a href=\"{{actionUrl}}\">继续</a><script>alert(1)</script>",
                Map.of(
                        "productName", "Sca4d <Admin>",
                        "displayName", "Alice & Bob",
                        "code", "123456",
                        "actionUrl", "https://example.com/verify?id=1&from=mail"));

        assertThat(mail.getSubject()).isEqualTo("Sca4d <Admin> 验证码");
        assertThat(mail.getText()).contains("Alice & Bob").contains("123456 & 请勿转发");
        assertThat(mail.getHtml())
                .contains("Sca4d &lt;Admin&gt;")
                .contains("Alice &amp; Bob")
                .contains("https://example.com/verify?id&#61;1&amp;from&#61;mail")
                .doesNotContain("script", "alert(1)");
    }

    @Test
    void shouldRejectExecutableOrReflectiveMustacheFeatures() {
        assertThatThrownBy(() -> renderer.variables("{{{unescaped}}}"))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MailErrorCode.TEMPLATE_CONTENT_INVALID));
        assertThatThrownBy(() -> renderer.variables("{{#items}}{{name}}{{/items}}"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> renderer.variables("{{> shared}}"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> renderer.variables("{{class}}"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectMissingVariablesAndHeaderNewlines() {
        assertThatThrownBy(() -> renderer.render(
                "Code {{code}}", "Body", null, Map.of()))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MailErrorCode.TEMPLATE_RENDER_INVALID));
        assertThatThrownBy(() -> renderer.render(
                "Code {{suffix}}", "Body", null, Map.of("suffix", "ok\nBcc: hidden@example.com")))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MailErrorCode.TEMPLATE_RENDER_INVALID));
    }

    @Test
    void shouldRemoveUnsafeLinksAndAttributes() {
        RenderedMail mail = renderer.render(
                "Subject",
                "Body",
                "<p onclick=\"alert(1)\">Text</p>"
                        + "<a href=\"javascript:alert(2)\">bad</a>"
                        + "<img src=\"https://tracking.example/pixel\">",
                Map.of());

        assertThat(mail.getHtml())
                .contains("<p>Text</p>")
                .doesNotContain("onclick", "javascript", "img", "tracking.example");
    }
}
