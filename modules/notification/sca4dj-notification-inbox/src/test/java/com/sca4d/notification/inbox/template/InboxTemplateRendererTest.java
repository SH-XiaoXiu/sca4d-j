package com.sca4d.notification.inbox.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.i18n.LocaleCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 验证站内信模板的语言回退、结构限制和安全输出。 */
class InboxTemplateRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LocaleCatalog locales = new LocaleCatalog(
            List.of(Locale.SIMPLIFIED_CHINESE, Locale.US),
            Locale.SIMPLIFIED_CHINESE);

    @Test
    @DisplayName("按同语言 Locale 回退并生成纯文本和安全 HTML")
    void rendersWithLocaleFallback() throws Exception {
        InboxTemplateRenderer renderer = new InboxTemplateRenderer(locales);
        JsonNode content = objectMapper.readTree(
                """
                        {
                          "zh-CN": {
                            "title": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"验证码"}]}]},
                            "body": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"notificationVariable","attrs":{"name":"code"}}]}]}
                          },
                          "en-US": {
                            "title": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Code"}]}]},
                            "body": {"type":"doc","content":[{"type":"customContainer","content":[{"type":"text","text":"Value: "},{"type":"notificationVariable","attrs":{"name":"code"},"marks":[{"type":"bold"}]}]}]}
                          }
                        }
                        """);

        RenderedInboxMessage rendered = renderer.render(content, Locale.UK, Map.of("code", "<123456>"));

        assertThat(renderer.variables(content)).containsExactly("code");
        assertThat(rendered.title()).isEqualTo("Code");
        assertThat(rendered.text()).isEqualTo("Value: <123456>");
        assertThat(rendered.html()).isEqualTo("Value: <strong>&lt;123456&gt;</strong>");
    }

    @Test
    @DisplayName("结构错误的模板被拒绝")
    void rejectsInvalidStructure() throws Exception {
        InboxTemplateRenderer renderer = new InboxTemplateRenderer(locales);
        JsonNode invalid = objectMapper.readTree(
                """
                        {
                          "zh-CN": {
                            "title": {"type":"doc","content":{}},
                            "body": {"type":"doc"}
                          }
                        }
                        """);

        assertThatThrownBy(() -> renderer.validate(invalid)).isInstanceOf(RuntimeException.class);
    }
}
