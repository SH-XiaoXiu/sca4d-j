package com.sca4d.notification.email;

import com.sca4d.core.api.render.Renderer;
import com.sca4d.core.api.render.TemplateInspection;
import org.jetbrains.annotations.NotNull;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 把 Email 源码模板渲染为主题、纯文本正文和安全 HTML 正文。 */
public class EmailRenderer implements Renderer<EmailTemplateSource, RenderedEmail> {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-z][a-zA-Z0-9_]{0,63})\\s*}}");

    private static final Pattern URL = Pattern.compile("(?i)^(https?://[^\\s<>]+|mailto:[^\\s<>]+|tel:[^\\s<>]+)$");

    private static final PolicyFactory HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.STYLES);

    @Override
    public @NotNull TemplateInspection inspect(@NotNull EmailTemplateSource source) {
        Set<String> variables = new LinkedHashSet<>();
        collect(source.subject(), variables);
        collect(source.text(), variables);
        collect(source.html(), variables);
        return new TemplateInspection(variables);
    }

    @Override
    public @NotNull RenderedEmail render(
            @NotNull EmailTemplateSource source,
            @NotNull Locale locale,
            @NotNull Map<String, String> variables) {
        String subject = replace(source.subject(), variables, false)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .strip();
        String text = replace(source.text(), variables, false);
        String html = HTML_POLICY.sanitize(replace(source.html(), variables, true));
        return new RenderedEmail(subject, text, html);
    }

    private static void collect(@NotNull String source, @NotNull Set<String> variables) {
        Matcher matcher = VARIABLE.matcher(source);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
    }

    private static @NotNull String replace(
            @NotNull String source,
            @NotNull Map<String, String> variables,
            boolean html) {
        Matcher matcher = VARIABLE.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            html ? htmlValue(value, !insideTagAttribute(source, matcher.start())) : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static @NotNull String htmlValue(@NotNull String value, boolean link) {
        String escaped = value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        if (!link || !URL.matcher(value).matches()) {
            return escaped;
        }
        return "<a href=\"" + escaped + "\" style=\"color:#2563eb;text-decoration:underline;\">"
                + escaped + "</a>";
    }

    private static boolean insideTagAttribute(@NotNull String source, int position) {
        return source.lastIndexOf('<', position) > source.lastIndexOf('>', position);
    }
}
