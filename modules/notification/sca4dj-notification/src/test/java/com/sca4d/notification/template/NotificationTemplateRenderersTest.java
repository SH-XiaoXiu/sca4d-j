package com.sca4d.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.i18n.LocaleCatalog;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 验证通知模板默认输出、结构限制和具体应用渲染器扩展。 */
class NotificationTemplateRenderersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LocaleCatalog locales = new LocaleCatalog(
            List.of(Locale.SIMPLIFIED_CHINESE, Locale.US),
            Locale.SIMPLIFIED_CHINESE);

    @Test
    @DisplayName("默认渲染器按同语言 Locale 回退并生成纯文本和安全 HTML")
    void rendersDefaultFormatsWithLocaleFallback() throws Exception {
        NotificationTemplateRenderers renderers = new NotificationTemplateRenderers(locales, List.of());
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

        RenderedNotification rendered = renderers.renderDefaults(content, Locale.UK, Map.of("code", "<123456>"));

        assertThat(renderers.variables(content)).containsExactly("code");
        assertThat(rendered.title()).isEqualTo("Code");
        assertThat(rendered.text()).isEqualTo("Value: <123456>");
        assertThat(rendered.html()).isEqualTo("Value: <strong>&lt;123456&gt;</strong>");
    }

    @Test
    @DisplayName("具体应用渲染器可以新增编码并覆盖内置编码")
    void installsAndOverridesCustomRenderers() throws Exception {
        NotificationTemplateRenderer custom = renderer("markdown", "custom");
        NotificationTemplateRenderer override = renderer(NotificationTemplateRenderers.HTML, "override");
        NotificationTemplateRenderers renderers = new NotificationTemplateRenderers(locales, List.of(custom, override));
        JsonNode content = document();

        assertThat(renderers.codes()).containsExactlyInAnyOrder("text", "html", "markdown");
        assertThat(renderers.render("markdown", content, Locale.SIMPLIFIED_CHINESE, Map.of()).body())
                .isEqualTo("custom");
        assertThat(renderers.render("html", content, Locale.SIMPLIFIED_CHINESE, Map.of()).body())
                .isEqualTo("override");
    }

    @Test
    @DisplayName("两个具体应用渲染器不能声明相同编码")
    void rejectsDuplicateCustomRendererCodes() {
        assertThatThrownBy(
                () -> new NotificationTemplateRenderers(
                        locales,
                        List.of(renderer("markdown", "first"), renderer("markdown", "second"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复通知模板渲染器");
    }

    @Test
    @DisplayName("结构错误被拒绝但未知节点由默认渲染器降级处理")
    void validatesStructureWithoutRestrictingNodeTypes() throws Exception {
        NotificationTemplateRenderers renderers = new NotificationTemplateRenderers(locales, List.of());
        JsonNode invalid = objectMapper.readTree(
                """
                        {
                          "zh-CN": {
                            "title": {"type":"doc","content":{}},
                            "body": {"type":"doc"}
                          }
                        }
                        """);

        assertThatThrownBy(() -> renderers.validate(invalid)).isInstanceOf(RuntimeException.class);
    }

    private JsonNode document() throws Exception {
        return objectMapper.readTree(
                """
                        {
                          "zh-CN": {
                            "title": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"%s"}]}]},
                            "body": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"%s"}]}]}
                          }
                        }
                        """
                        .formatted("Title", "Body"));
    }

    private static NotificationTemplateRenderer renderer(String code, String body) {
        return new NotificationTemplateRenderer() {
            @Override
            public @NotNull String code() {
                return code;
            }

            @Override
            public @NotNull RenderedTemplate render(
                    @NotNull JsonNode title,
                    @NotNull JsonNode ignored,
                    @NotNull Map<@NotNull String, @NotNull String> variables) {
                return new RenderedTemplate("title", body);
            }
        };
    }
}
