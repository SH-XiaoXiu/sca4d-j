package com.sca4d.notification.template;

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

/** 验证统一通知富文本的变量、Locale 回退和安全输出。 */
class NotificationRichTextRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NotificationRichTextRenderer renderer = new NotificationRichTextRenderer(
            new LocaleCatalog(List.of(Locale.SIMPLIFIED_CHINESE, Locale.US), Locale.SIMPLIFIED_CHINESE));

    @Test
    @DisplayName("结构化变量按同语言 Locale 回退并分别生成纯文本和转义 HTML")
    void rendersStructuredVariableWithLocaleFallback() throws Exception {
        JsonNode content = objectMapper.readTree(
                """
                        {
                          "zh-CN": {
                            "title": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"验证码"}]}]},
                            "body": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"notificationVariable","attrs":{"name":"code"}}]}]}
                          },
                          "en-US": {
                            "title": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Code"}]}]},
                            "body": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Value: "},{"type":"notificationVariable","attrs":{"name":"code"},"marks":[{"type":"bold"}]}]}]}
                          }
                        }
                        """);

        RenderedNotification rendered = renderer.render(
                content,
                Locale.UK,
                Map.of("code", "<123456>"));

        assertThat(renderer.variables(content)).containsExactly("code");
        assertThat(rendered.title()).isEqualTo("Code");
        assertThat(rendered.text()).isEqualTo("Value: <123456>");
        assertThat(rendered.html()).isEqualTo("<p>Value: <strong>&lt;123456&gt;</strong></p>");
    }

    @Test
    @DisplayName("未声明节点在保存前被拒绝")
    void rejectsUnsupportedNode() throws Exception {
        JsonNode content = objectMapper.readTree("""
                {
                  "zh-CN": {
                    "title": {"type":"doc","content":[{"type":"paragraph"}]},
                    "body": {"type":"doc","content":[{"type":"video"}]}
                  }
                }
                """);

        assertThatThrownBy(() -> renderer.validate(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("节点不受支持");
    }

    @Test
    @DisplayName("迁移节点继续渲染简单变量并拒绝危险 Mustache 语法")
    void rendersAndValidatesMigratedTemplates() throws Exception {
        JsonNode content = objectMapper.readTree(
                """
                        {
                          "zh-CN": {
                            "title": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"templateText","attrs":{"source":"Code {{code}}"}}]}]},
                            "body": {"type":"doc","content":[{"type":"richHtml","attrs":{"source":"<p>Hello {{name}}</p>"}}]}
                          }
                        }
                        """);

        RenderedNotification rendered = renderer.render(
                content,
                Locale.SIMPLIFIED_CHINESE,
                Map.of("code", "123456", "name", "<b>A</b>"));

        assertThat(renderer.variables(content)).containsExactlyInAnyOrder("code", "name");
        assertThat(rendered.title()).isEqualTo("Code 123456");
        assertThat(rendered.html()).contains("Hello &lt;b&gt;A&lt;/b&gt;");

        JsonNode unsafe = objectMapper.readTree(
                """
                        {
                          "zh-CN": {
                            "title": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"templateText","attrs":{"source":"{{#items}}{{name}}{{/items}}"}}]}]},
                            "body": {"type":"doc","content":[{"type":"paragraph"}]}
                          }
                        }
                        """);
        assertThatThrownBy(() -> renderer.variables(unsafe)).isInstanceOf(IllegalArgumentException.class);
    }
}
