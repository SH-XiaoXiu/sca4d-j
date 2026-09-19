package com.sca4d.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.i18n.LocaleCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jsoup.Jsoup;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 校验受限 Tiptap JSON，并把同一模板渲染为纯文本与安全 HTML。 */
public final class NotificationRichTextRenderer {

    private static final Set<String> BLOCKS = Set.of(
            "doc",
            "paragraph",
            "heading",
            "blockquote",
            "bulletList",
            "orderedList",
            "listItem",
            "codeBlock",
            "horizontalRule",
            "table",
            "tableRow",
            "tableCell",
            "tableHeader");

    private static final Set<String> INLINE = Set.of(
            "text",
            "hardBreak",
            "image",
            "notificationVariable",
            "templateText",
            "richHtml");

    private static final Set<String> MARKS = Set.of(
            "bold",
            "italic",
            "underline",
            "strike",
            "code",
            "link",
            "textStyle",
            "highlight");

    private static final int MAXIMUM_NODES = 10_000;

    private static final int MAXIMUM_DEPTH = 32;

    private static final PolicyFactory HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.STYLES);

    private final @NotNull LocaleCatalog locales;

    private final LegacyMustacheRenderer legacy = new LegacyMustacheRenderer();

    /** @param locales 具体应用支持与回退使用的 Locale 目录 */
    public NotificationRichTextRenderer(@NotNull LocaleCatalog locales) {
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
    }

    /** 校验完整 Locale、title/body 文档、节点、属性和变量名。 */
    @CheckReturnValue
    public @NotNull JsonNode validate(@NotNull JsonNode content) {
        JsonNode checked = Objects.requireNonNull(content, "通知模板内容不能为空");
        if (!checked.isObject() || checked.isEmpty()) {
            throw new IllegalArgumentException("通知模板必须是非空 Locale object");
        }
        String defaultTag = locales.defaultLocale().toLanguageTag();
        if (!checked.has(defaultTag)) {
            throw new IllegalArgumentException("通知模板缺少默认 Locale: " + defaultTag);
        }
        final int[] nodes = {0};
        checked.properties().forEach(entry -> {
            Locale locale = Locale.forLanguageTag(entry.getKey());
            if (locale.getLanguage().isBlank()) {
                throw new IllegalArgumentException("通知模板 Locale 不合法: " + entry.getKey());
            }
            JsonNode value = entry.getValue();
            if (!value.isObject() || !value.has("title") || !value.has("body") || value.size() != 2) {
                throw new IllegalArgumentException("每个 Locale 必须且只能包含 title 和 body");
            }
            validateNode(value.get("title"), 0, nodes);
            validateNode(value.get("body"), 0, nodes);
        });
        return checked.deepCopy();
    }

    /** @return 模板全部 Locale 使用的不可修改变量名集合 */
    @CheckReturnValue
    public @NotNull @Unmodifiable Set<@NotNull String> variables(@NotNull JsonNode content) {
        JsonNode checked = validate(content);
        Set<String> result = new LinkedHashSet<>();
        checked.forEach(locale -> {
            collectVariables(locale.get("title"), result);
            collectVariables(locale.get("body"), result);
        });
        return Set.copyOf(result);
    }

    /** 按精确 Locale、同语言和应用默认 Locale 回退并渲染。 */
    @CheckReturnValue
    public @NotNull RenderedNotification render(
            @NotNull JsonNode content,
            @NotNull Locale requestedLocale,
            @NotNull Map<@NotNull String, @NotNull String> variables) {
        JsonNode checked = validate(content);
        JsonNode localized = localized(checked, requestedLocale);
        RenderBuffer title = new RenderBuffer(variables, legacy);
        title.node(localized.get("title"), 0);
        String titleText = title.text().replace('\r', ' ').replace('\n', ' ').strip();
        RenderBuffer body = new RenderBuffer(variables, legacy);
        body.node(localized.get("body"), 0);
        return new RenderedNotification(titleText, body.text().strip(), body.html());
    }

    /** 按 LocaleCatalog 规则查找模板内容。 */
    private @NotNull JsonNode localized(@NotNull JsonNode content, @NotNull Locale requestedLocale) {
        Locale requested = Locale.forLanguageTag(
                Objects.requireNonNull(requestedLocale, "请求 Locale 不能为空").toLanguageTag());
        JsonNode exact = content.get(requested.toLanguageTag());
        if (exact != null) {
            return exact;
        }
        for (Locale locale : locales.supportedLocales()) {
            if (locale.getLanguage().equals(requested.getLanguage()) && content.has(locale.toLanguageTag())) {
                return content.get(locale.toLanguageTag());
            }
        }
        return content.required(locales.defaultLocale().toLanguageTag());
    }

    /** 递归校验 ProseMirror 节点并限制资源消耗。 */
    private static void validateNode(@NotNull JsonNode node, int depth, int[] nodes) {
        if (depth > MAXIMUM_DEPTH || ++nodes[0] > MAXIMUM_NODES || !node.isObject()) {
            throw new IllegalArgumentException("通知模板节点过深、过多或格式不正确");
        }
        String type = requiredText(node, "type");
        if (!BLOCKS.contains(type) && !INLINE.contains(type)) {
            throw new IllegalArgumentException("通知模板节点不受支持: " + type);
        }
        if ("text".equals(type)) {
            requiredText(node, "text");
        }
        if ("notificationVariable".equals(type)) {
            String name = requiredText(node.required("attrs"), "name");
            if (!name.matches("[a-z][a-zA-Z0-9_]{0,63}")) {
                throw new IllegalArgumentException("通知模板变量名不合法: " + name);
            }
        }
        if ("templateText".equals(type) || "richHtml".equals(type)) {
            String source = requiredText(node.required("attrs"), "source");
            if (source.length() > LegacyMustacheRenderer.MAXIMUM_SOURCE_LENGTH) {
                throw new IllegalArgumentException("导入通知模板原文过长");
            }
        }
        validateAttributes(type, node.path("attrs"));
        JsonNode marks = node.path("marks");
        if (!marks.isMissingNode()) {
            if (!marks.isArray()) {
                throw new IllegalArgumentException("通知模板 marks 必须是数组");
            }
            marks.forEach(mark -> {
                String markType = requiredText(mark, "type");
                if (!MARKS.contains(markType)) {
                    throw new IllegalArgumentException("通知模板 mark 不受支持: " + markType);
                }
                validateAttributes(markType, mark.path("attrs"));
            });
        }
        JsonNode children = node.path("content");
        if (!children.isMissingNode()) {
            if (!children.isArray()) {
                throw new IllegalArgumentException("通知模板 content 必须是数组");
            }
            children.forEach(child -> validateNode(child, depth + 1, nodes));
        }
    }

    /** 校验会进入 HTML 的链接、图片、颜色、对齐和表格属性。 */
    private static void validateAttributes(@NotNull String type, @NotNull JsonNode attrs) {
        if (attrs.isMissingNode() || attrs.isNull()) {
            return;
        }
        if (!attrs.isObject()) {
            throw new IllegalArgumentException("通知模板 attrs 必须是 object");
        }
        switch (type) {
            case "link" -> requireSafeUri(requiredText(attrs, "href"));
            case "image" -> {
                requireSafeUri(requiredText(attrs, "src"));
                if (attrs.path("alt").isTextual() && attrs.path("alt").textValue().length() > 512) {
                    throw new IllegalArgumentException("通知图片说明过长");
                }
            }
            case "textStyle", "highlight" -> {
                String color = attrs.path("color").asText("");
                if (!color.isEmpty() && !color.matches("#[0-9A-Fa-f]{6}")) {
                    throw new IllegalArgumentException("通知模板颜色必须使用六位十六进制值");
                }
            }
            case "paragraph", "heading" -> {
                String align = attrs.path("textAlign").asText("");
                if (!align.isEmpty() && !Set.of("left", "center", "right", "justify").contains(align)) {
                    throw new IllegalArgumentException("通知模板对齐方式不合法");
                }
            }
            default -> {
            }
        }
    }

    /** 只接受 HTTPS、mailto、tel 和 Storage 相对路径。 */
    private static void requireSafeUri(@NotNull String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (value.startsWith("/files/")
                || "https".equalsIgnoreCase(scheme)
                || "mailto".equalsIgnoreCase(scheme)
                || "tel".equalsIgnoreCase(scheme)) {
            return;
        }
        throw new IllegalArgumentException("通知模板 URL 只允许 HTTPS、mailto、tel 或 Storage 文件路径");
    }

    private void collectVariables(@NotNull JsonNode node, @NotNull Set<String> result) {
        if ("notificationVariable".equals(node.path("type").asText())) {
            result.add(node.path("attrs").path("name").asText());
        }
        if ("templateText".equals(node.path("type").asText())
                || "richHtml".equals(node.path("type").asText())) {
            result.addAll(legacy.variables(node.path("attrs").path("source").asText()));
        }
        node.path("content").forEach(child -> collectVariables(child, result));
    }

    private static @NotNull String requiredText(@NotNull JsonNode node, @NotNull String field) {
        JsonNode value = Objects.requireNonNull(node, "通知模板节点不能为空").get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("通知模板缺少文本字段: " + field);
        }
        return value.textValue();
    }

    /** 同时生成纯文本和安全 HTML 的递归渲染器。 */
    private static final class RenderBuffer {
        private final Map<String, String> variables;

        private final LegacyMustacheRenderer legacy;

        private final StringBuilder text = new StringBuilder();

        private final StringBuilder html = new StringBuilder();

        private RenderBuffer(Map<String, String> variables, LegacyMustacheRenderer legacy) {
            this.variables = Map.copyOf(Objects.requireNonNull(variables, "模板变量不能为空"));
            this.legacy = Objects.requireNonNull(legacy, "旧通知模板渲染器不能为空");
        }

        private void node(JsonNode node, int listDepth) {
            String type = node.path("type").asText();
            switch (type) {
                case "text" -> appendMarked(node.path("text").asText(), node.path("marks"));
                case "notificationVariable" -> appendMarked(
                        requiredVariable(node.path("attrs").path("name").asText()),
                        node.path("marks"));
                case "templateText" -> appendMarked(
                        legacy.renderText(node.path("attrs").path("source").asText(), variables),
                        node.path("marks"));
                case "richHtml" -> richHtml(node.path("attrs").path("source").asText());
                case "hardBreak" -> lineBreak();
                case "horizontalRule" -> {
                    text.append("\n---\n");
                    html.append("<hr>");
                }
                case "image" -> image(node.path("attrs"));
                case "paragraph", "heading", "blockquote", "codeBlock", "listItem", "tableCell", "tableHeader" ->
                    block(node, type, listDepth);
                case "bulletList", "orderedList" -> list(node, type, listDepth);
                case "table", "tableRow", "doc" -> children(node, listDepth);
                default -> throw new IllegalArgumentException("通知模板节点不受支持: " + type);
            }
        }

        private void block(JsonNode node, String type, int listDepth) {
            String tag = switch (type) {
                case "heading" -> "h" + Math.clamp(node.path("attrs").path("level").asInt(2), 1, 4);
                case "blockquote" -> "blockquote";
                case "codeBlock" -> "pre";
                case "listItem" -> "li";
                case "tableCell" -> "td";
                case "tableHeader" -> "th";
                default -> "p";
            };
            html.append('<').append(tag).append(style(node.path("attrs"))).append('>');
            if ("listItem".equals(type)) {
                text.repeat("  ", Math.max(0, listDepth)).append("- ");
            }
            children(node, listDepth);
            html.append("</").append(tag).append('>');
            if (!"tableCell".equals(type) && !"tableHeader".equals(type)) {
                text.append('\n');
            } else {
                text.append('\t');
            }
        }

        private void list(JsonNode node, String type, int listDepth) {
            String tag = "orderedList".equals(type) ? "ol" : "ul";
            html.append('<').append(tag).append('>');
            children(node, listDepth + 1);
            html.append("</").append(tag).append('>');
        }

        private void children(JsonNode node, int listDepth) {
            node.path("content").forEach(child -> node(child, listDepth));
            if ("tableRow".equals(node.path("type").asText())) {
                text.append('\n');
            }
        }

        private void image(JsonNode attrs) {
            String src = requiredText(attrs, "src");
            String alt = attrs.path("alt").asText("");
            html.append("<img src=\"").append(escape(src)).append("\" alt=\"").append(escape(alt)).append("\">");
            if (!alt.isBlank()) {
                text.append('[').append(alt).append(']');
            }
        }

        private void richHtml(String source) {
            String rendered = legacy.renderHtml(source, variables);
            String safe = HTML_POLICY.sanitize(rendered);
            html.append(safe);
            text.append(Jsoup.parseBodyFragment(safe).text());
        }

        private void appendMarked(String value, JsonNode marks) {
            String escaped = escape(value);
            StringBuilder prefix = new StringBuilder();
            StringBuilder suffix = new StringBuilder();
            for (JsonNode mark : marks) {
                String type = mark.path("type").asText();
                switch (type) {
                    case "bold" -> wrap(prefix, suffix, "strong", "");
                    case "italic" -> wrap(prefix, suffix, "em", "");
                    case "underline" -> wrap(prefix, suffix, "u", "");
                    case "strike" -> wrap(prefix, suffix, "s", "");
                    case "code" -> wrap(prefix, suffix, "code", "");
                    case "link" ->
                        wrap(prefix, suffix, "a", " href=\"" + escape(mark.path("attrs").path("href").asText()) + "\"");
                    case "textStyle" -> wrap(prefix, suffix, "span", colorStyle(mark.path("attrs"), "color"));
                    case "highlight" ->
                        wrap(prefix, suffix, "span", colorStyle(mark.path("attrs"), "background-color"));
                    default -> throw new IllegalArgumentException("通知模板 mark 不受支持: " + type);
                }
            }
            text.append(value);
            html.append(prefix).append(escaped).append(suffix);
        }

        private static void wrap(StringBuilder prefix, StringBuilder suffix, String tag, String attributes) {
            prefix.append('<').append(tag).append(attributes).append('>');
            suffix.insert(0, "</" + tag + ">");
        }

        private static String colorStyle(JsonNode attrs, String property) {
            String color = attrs.path("color").asText("");
            return color.isEmpty() ? "" : " style=\"" + property + ':' + color + "\"";
        }

        private static String style(JsonNode attrs) {
            String align = attrs.path("textAlign").asText("");
            return align.isEmpty() ? "" : " style=\"text-align:" + align + "\"";
        }

        private String requiredVariable(String name) {
            String value = variables.get(name);
            if (value == null) {
                throw new IllegalArgumentException("通知模板缺少变量: " + name);
            }
            return value;
        }

        private void lineBreak() {
            text.append('\n');
            html.append("<br>");
        }

        private String text() {
            return text.toString();
        }

        private String html() {
            return html.toString();
        }

        private static String escape(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }
}
