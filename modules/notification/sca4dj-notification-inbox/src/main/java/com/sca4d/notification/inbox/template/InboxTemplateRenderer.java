package com.sca4d.notification.inbox.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.i18n.LocaleCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 校验站内信 Tiptap 模板，并生成持久化所需的纯文本和安全 HTML。 */
public class InboxTemplateRenderer {

    private static final int MAXIMUM_NODES = 10_000;

    private static final int MAXIMUM_DEPTH = 32;

    private static final int MAXIMUM_RENDERED_LENGTH = 500_000;

    private static final Pattern VARIABLE = Pattern.compile("[a-z][a-zA-Z0-9_]{0,63}");

    private static final PolicyFactory HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.STYLES);

    private final @NotNull LocaleCatalog locales;

    public InboxTemplateRenderer(@NotNull LocaleCatalog locales) {
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
    }

    /** 校验 Locale、title/body、节点结构、资源上限和变量名。 */
    @CheckReturnValue
    public @NotNull JsonNode validate(@NotNull JsonNode content) {
        JsonNode checked = object(content, "notificationTemplate");
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("站内信模板必须包含 Locale");
        }
        String defaultTag = locales.defaultLocale().toLanguageTag();
        if (!checked.has(defaultTag)) {
            throw new IllegalArgumentException("站内信模板缺少默认 Locale: " + defaultTag);
        }
        int[] nodeCount = {0};
        checked.properties().forEach(entry -> {
            String languageTag = DataOps.s(entry.getKey()).strip().req("notificationTemplate.locale").get();
            Locale locale = Locale.forLanguageTag(languageTag);
            if (locale.getLanguage().isBlank()) {
                throw new IllegalArgumentException("站内信模板 Locale 不合法: " + languageTag);
            }
            JsonNode localized = object(entry.getValue(), languageTag);
            if (localized.size() != 2 || !localized.has("title") || !localized.has("body")) {
                throw new IllegalArgumentException("每个 Locale 必须且只能包含 title 和 body");
            }
            validateNode(object(localized.get("title"), languageTag + ".title"), languageTag + ".title", 0, nodeCount);
            validateNode(object(localized.get("body"), languageTag + ".body"), languageTag + ".body", 0, nodeCount);
        });
        return checked.deepCopy();
    }

    /** @return 模板中全部 `notificationVariable` 使用的变量名 */
    @CheckReturnValue
    public @NotNull @Unmodifiable Set<@NotNull String> variables(@NotNull JsonNode content) {
        JsonNode checked = validate(content);
        Set<String> result = new LinkedHashSet<>();
        checked.forEach(localized -> {
            collectVariables(localized.get("title"), result);
            collectVariables(localized.get("body"), result);
        });
        return Set.copyOf(result);
    }

    /** 按 Locale 选择模板，并生成站内信快照使用的标题、纯文本和安全 HTML。 */
    @CheckReturnValue
    public @NotNull RenderedInboxMessage render(
            @NotNull JsonNode content,
            @NotNull Locale locale,
            @NotNull Map<@NotNull String, @NotNull String> variables) {
        JsonNode localized = localized(validate(content), locale);
        Map<String, String> checkedVariables = Map.copyOf(
                Objects.requireNonNull(variables, "站内信模板变量不能为空"));
        String title = text(localized.get("title"), checkedVariables, 0)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .strip();
        String text = text(localized.get("body"), checkedVariables, 0).strip();
        String html = HTML_POLICY.sanitize(html(localized.get("body"), checkedVariables));
        requireOutputLength(title, "站内信标题");
        requireOutputLength(text, "站内信纯文本正文");
        requireOutputLength(html, "站内信 HTML 正文");
        return new RenderedInboxMessage(title, text, html);
    }

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
        return content.get(locales.defaultLocale().toLanguageTag());
    }

    private static void validateNode(JsonNode node, String path, int depth, int[] nodeCount) {
        if (depth > MAXIMUM_DEPTH || ++nodeCount[0] > MAXIMUM_NODES) {
            throw new IllegalArgumentException("站内信模板节点过深或过多");
        }
        String type = requiredText(node, "type", path);
        JsonNode attrs = node.get("attrs");
        if (attrs != null && !attrs.isNull()) {
            object(attrs, path + ".attrs");
        }
        if ("text".equals(type)) {
            requiredText(node, "text", path);
        }
        if ("notificationVariable".equals(type)) {
            String name = requiredText(object(attrs, path + ".attrs"), "name", path + ".attrs");
            if (!VARIABLE.matcher(name).matches()) {
                throw new IllegalArgumentException("站内信模板变量名不合法: " + name);
            }
        }
        int markIndex = 0;
        for (JsonNode mark : array(node.get("marks"), path + ".marks")) {
            JsonNode checkedMark = object(mark, path + ".marks[" + markIndex + ']');
            requiredText(checkedMark, "type", path + ".marks[" + markIndex + ']');
            JsonNode markAttrs = checkedMark.get("attrs");
            if (markAttrs != null && !markAttrs.isNull()) {
                object(markAttrs, path + ".marks[" + markIndex + "].attrs");
            }
            markIndex++;
        }
        int childIndex = 0;
        for (JsonNode child : array(node.get("content"), path + ".content")) {
            validateNode(
                    object(child, path + ".content[" + childIndex + ']'),
                    path + ".content[" + childIndex + ']',
                    depth + 1,
                    nodeCount);
            childIndex++;
        }
    }

    private static void collectVariables(JsonNode node, Set<String> result) {
        if ("notificationVariable".equals(requiredText(node, "type", "notificationTemplate.node"))) {
            result.add(
                    requiredText(
                            object(node.get("attrs"), "notificationTemplate.node.attrs"),
                            "name",
                            "notificationTemplate.node.attrs"));
        }
        array(node.get("content"), "notificationTemplate.node.content")
                .forEach(child -> collectVariables(child, result));
    }

    private static @NotNull JsonNode object(JsonNode value, String path) {
        return Objects.requireNonNull(
                DataOps.v(value).ok(JsonNode::isObject, path, "JSON 值必须是对象").get());
    }

    private static @NotNull List<JsonNode> array(JsonNode value, String path) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        JsonNode checked = Objects.requireNonNull(
                DataOps.v(value).ok(JsonNode::isArray, path, "JSON 值必须是数组").get());
        List<JsonNode> result = new ArrayList<>(checked.size());
        checked.forEach(result::add);
        return List.copyOf(result);
    }

    private static @NotNull String requiredText(JsonNode node, String field, String path) {
        JsonNode value = object(node, path).get(field);
        return DataOps.v(value)
                .ok(JsonNode::isTextual, path + '.' + field, "JSON 字段必须是文本")
                .map(JsonNode::textValue)
                .str()
                .req(path + '.' + field)
                .get();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : "";
    }

    private static String variable(Map<String, String> variables, String name) {
        return DataOps.s(variables.get(name)).req("notificationVariable." + name).get();
    }

    private static void requireOutputLength(String value, String name) {
        if (value.length() > MAXIMUM_RENDERED_LENGTH) {
            throw new IllegalArgumentException(name + "不能超过 " + MAXIMUM_RENDERED_LENGTH + " 个字符");
        }
    }

    /** 把一个 Tiptap 节点和子节点转换为纯文本。 */
    private static String text(JsonNode node, Map<String, String> variables, int listDepth) {
        String type = requiredText(node, "type", "notificationTemplate.node");
        return switch (type) {
            case "text" -> requiredText(node, "text", "notificationTemplate.node");
            case "notificationVariable" -> variable(
                    variables,
                    requiredText(
                            object(node.get("attrs"), "notificationTemplate.node.attrs"),
                            "name",
                            "notificationTemplate.node.attrs"));
            case "hardBreak" -> "\n";
            case "horizontalRule" -> "\n---\n";
            case "image" -> {
                String alt = optionalText(node.get("attrs"), "alt");
                yield alt.isBlank() ? "" : '[' + alt + ']';
            }
            default -> {
                StringBuilder result = new StringBuilder();
                if ("listItem".equals(type)) {
                    result.repeat("  ", Math.max(0, listDepth)).append("- ");
                }
                int childDepth = "bulletList".equals(type) || "orderedList".equals(type)
                        ? listDepth + 1
                        : listDepth;
                for (JsonNode child : array(node.get("content"), "notificationTemplate.node.content")) {
                    result.append(text(child, variables, childDepth));
                }
                if (Set.of("paragraph", "heading", "blockquote", "codeBlock", "listItem", "tableRow")
                        .contains(type)) {
                    result.append('\n');
                } else if (Set.of("tableCell", "tableHeader").contains(type)) {
                    result.append('\t');
                }
                yield result.toString();
            }
        };
    }

    /** 把一个 Tiptap 节点和子节点转换为待清洗的 HTML。 */
    private static String html(JsonNode node, Map<String, String> variables) {
        String type = requiredText(node, "type", "notificationTemplate.node");
        return switch (type) {
            case "text" -> marked(
                    escape(requiredText(node, "text", "notificationTemplate.node")),
                    node.get("marks"));
            case "notificationVariable" -> {
                String name = requiredText(
                        object(node.get("attrs"), "notificationTemplate.node.attrs"),
                        "name",
                        "notificationTemplate.node.attrs");
                yield marked(escape(variable(variables, name)), node.get("marks"));
            }
            case "hardBreak" -> "<br>";
            case "horizontalRule" -> "<hr>";
            case "image" -> {
                JsonNode attrs = node.get("attrs");
                yield "<img src=\"" + escape(optionalText(attrs, "src")) + "\" alt=\""
                        + escape(optionalText(attrs, "alt")) + "\">";
            }
            default -> {
                String tag = switch (type) {
                    case "paragraph" -> "p";
                    case "heading" -> "h" + headingLevel(node.get("attrs"));
                    case "blockquote" -> "blockquote";
                    case "codeBlock" -> "pre";
                    case "listItem" -> "li";
                    case "bulletList" -> "ul";
                    case "orderedList" -> "ol";
                    case "table" -> "table";
                    case "tableRow" -> "tr";
                    case "tableCell" -> "td";
                    case "tableHeader" -> "th";
                    default -> null;
                };
                StringBuilder children = new StringBuilder();
                for (JsonNode child : array(node.get("content"), "notificationTemplate.node.content")) {
                    children.append(html(child, variables));
                }
                yield tag == null
                        ? children.toString()
                        : '<' + tag + style(node.get("attrs")) + '>' + children + "</" + tag + '>';
            }
        };
    }

    private static String marked(String value, JsonNode marks) {
        String result = value;
        for (JsonNode mark : array(marks, "notificationTemplate.node.marks")) {
            String type = requiredText(mark, "type", "notificationTemplate.node.mark");
            result = switch (type) {
                case "bold" -> "<strong>" + result + "</strong>";
                case "italic" -> "<em>" + result + "</em>";
                case "underline" -> "<u>" + result + "</u>";
                case "strike" -> "<s>" + result + "</s>";
                case "code" -> "<code>" + result + "</code>";
                case "link" ->
                    "<a href=\"" + escape(optionalText(mark.get("attrs"), "href")) + "\">" + result + "</a>";
                case "textStyle" -> "<span" + colorStyle(mark.get("attrs"), "color") + '>' + result + "</span>";
                case "highlight" ->
                    "<span" + colorStyle(mark.get("attrs"), "background-color") + '>' + result + "</span>";
                default -> result;
            };
        }
        return result;
    }

    private static int headingLevel(JsonNode node) {
        JsonNode value = node == null ? null : node.get("level");
        return value != null && value.isIntegralNumber() && value.canConvertToInt()
                ? value.intValue()
                : 2;
    }

    private static String colorStyle(JsonNode attrs, String property) {
        String color = optionalText(attrs, "color");
        return color.isBlank() ? "" : " style=\"" + property + ':' + escape(color) + "\"";
    }

    private static String style(JsonNode attrs) {
        String align = optionalText(attrs, "textAlign");
        return align.isBlank() ? "" : " style=\"text-align:" + escape(align) + "\"";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
